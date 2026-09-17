package com.ss.gb28181.internal;

import gov.nist.core.HostPort;
import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.parser.Pipeline;
import gov.nist.javax.sip.parser.PipelinedMsgParser;
import gov.nist.javax.sip.stack.MessageChannel;
import gov.nist.javax.sip.stack.SIPTransactionStack;
import gov.nist.javax.sip.stack.TCPMessageChannel;
import gov.nist.javax.sip.stack.TCPMessageProcessor;
import gov.nist.javax.sip.stack.timers.SipTimer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Keeps TCP backpressure at the socket instead of RI's unbounded reader-to-parser queue. */
public final class DirectTcpMessageProcessor extends TCPMessageProcessor {
    private static final System.Logger LOG = System.getLogger(DirectTcpMessageProcessor.class.getName());
    private static final int CONNECT_TIMEOUT_MILLIS = 3_000;
    private final int maxConnections;
    private final int readTimeout;
    private final int writeTimeoutMillis;
    private final ScheduledThreadPoolExecutor writeTimeouts;
    private final Set<DirectChannel> connections = new HashSet<>();

    public DirectTcpMessageProcessor(InetAddress address, SIPTransactionStack stack, int port) {
        this(address, stack, port, 10_000);
    }

    DirectTcpMessageProcessor(InetAddress address, SIPTransactionStack stack, int port, int writeTimeoutMillis) {
        super(address, stack, port);
        var properties = ((SipStackImpl) stack).getConfigurationProperties();
        maxConnections = Integer.parseInt(properties.getProperty("gov.nist.javax.sip.MAX_CONNECTIONS", "1000"));
        readTimeout = Integer.parseInt(properties.getProperty("gov.nist.javax.sip.READ_TIMEOUT", "10000"));
        if (maxConnections <= 0) throw new IllegalArgumentException("TCP connection capacity must be positive");
        if (writeTimeoutMillis <= 0) throw new IllegalArgumentException("TCP write timeout must be positive");
        this.writeTimeoutMillis = writeTimeoutMillis;
        writeTimeouts = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "simple-secret-gb-tcp-write-timeouts-" + port);
            thread.setDaemon(true);
            return thread;
        });
        writeTimeouts.setRemoveOnCancelPolicy(true);
    }

    @Override public void run() {
        while (true) {
            Socket accepted;
            try { accepted = sock.accept(); }
            catch (IOException e) { return; } // Closing the listener releases accept().
            try {
                synchronized (this) {
                    requireCapacity();
                    attach(accepted);
                }
            } catch (IOException | RuntimeException e) {
                try { accepted.close(); } catch (IOException ignored) { }
            }
        }
    }

    @Override public synchronized MessageChannel createMessageChannel(HostPort destination) throws IOException {
        return createMessageChannel(destination.getInetAddress(), destination.getPort());
    }

    @Override public synchronized MessageChannel createMessageChannel(InetAddress address, int port) throws IOException {
        String key = MessageChannel.getKey(address, port, "TCP");
        if (messageChannels.get(key) instanceof DirectChannel cached && !cached.closed.get()) return cached;
        requireCapacity();
        Socket socket = new Socket();
        try {
            socket.bind(new InetSocketAddress(getIpAddress(), 0));
            socket.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT_MILLIS);
            return attach(socket);
        } catch (IOException | RuntimeException e) {
            try { socket.close(); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }

    private void requireCapacity() throws IOException {
        if (!isRunning) throw new IOException("TCP listener is stopped");
        if (connections.size() >= maxConnections) throw new IOException("TCP connection capacity exceeded");
    }

    /** Called with the processor lock; register ownership before the reader thread can finish. */
    private DirectChannel attach(Socket socket) throws IOException {
        DirectChannel channel = new DirectChannel(socket, this);
        connections.add(channel);
        messageChannels.put(channel.getKey(), channel);
        incomingMessageChannels.put(channel.getKey(), channel);
        nConnections = connections.size();
        useCount = nConnections;
        try { channel.startReader(); }
        catch (RuntimeException e) { channel.close(); throw e; }
        return channel;
    }

    private synchronized void released(DirectChannel channel) {
        connections.remove(channel);
        messageChannels.remove(channel.getKey(), channel);
        incomingMessageChannels.remove(channel.getKey(), channel);
        nConnections = connections.size();
        useCount = nConnections;
    }

    @Override public void stop() {
        List<DirectChannel> closing;
        synchronized (this) {
            isRunning = false;
            if (sock != null) try { sock.close(); } catch (IOException ignored) { }
            closing = List.copyOf(connections);
        }
        // Closing raw sockets first releases blocked writes without acquiring the channel monitor.
        for (DirectChannel channel : closing) channel.closeSocket();
        try { for (DirectChannel channel : closing) channel.close(); }
        finally { writeTimeouts.shutdownNow(); }
    }

    private static final class DirectChannel extends TCPMessageChannel {
        private final DirectTcpMessageProcessor owner;
        private final Socket socket;
        private final AtomicBoolean closed = new AtomicBoolean();

        DirectChannel(Socket socket, DirectTcpMessageProcessor owner) throws IOException {
            super(owner.getSIPStack());
            this.owner = owner;
            this.socket = socket;
            mySock = socket;
            myClientInputStream = socket.getInputStream();
            myClientOutputStream = socket.getOutputStream();
            peerAddress = socket.getInetAddress();
            peerPort = socket.getPort();
            peerProtocol = "TCP";
            myAddress = owner.getIpAddress().getHostAddress();
            myPort = owner.getPort();
            key = MessageChannel.getKey(peerAddress, peerPort, "TCP");
            messageProcessor = owner;
            // This processor owns the cache and writes to its socket directly. Do not let
            // RI cache the same channel a second time (which closes its previous cache entry).
            isCached = true;
        }

        void startReader() {
            mythread = new Thread(this, "simple-secret-gb-tcp-" + peerPort);
            mythread.setDaemon(true);
            mythread.start();
        }

        @Override public void run() {
            try (DirectPipeline input = new DirectPipeline(myClientInputStream, owner.readTimeout, sipStack.getTimer())) {
                myParser = new PipelinedMsgParser(sipStack, this, input, sipStack.getMaxMessageSize());
                if (!closed.get()) myParser.run();
            } catch (IOException | RuntimeException e) {
                if (!closed.get()) LOG.log(System.Logger.Level.DEBUG, "GB TCP connection ended: {0}", e.getClass().getSimpleName());
            } finally { close(); }
        }

        @Override protected synchronized void sendMessage(byte[] message, boolean retry) throws IOException {
            if (closed.get()) throw new IOException("TCP connection is closed");
            ScheduledFuture<?> timeout;
            try { timeout = owner.writeTimeouts.schedule(this::closeSocket, owner.writeTimeoutMillis, TimeUnit.MILLISECONDS); }
            catch (RejectedExecutionException e) { throw new IOException("TCP listener is stopped", e); }
            try { myClientOutputStream.write(message); myClientOutputStream.flush(); }
            catch (IOException e) { close(); throw e; }
            finally { timeout.cancel(false); }
        }

        @Override public void sendMessage(byte[] message, InetAddress address, int port, boolean retry) throws IOException {
            if (!peerAddress.equals(address) || peerPort != port)
                throw new IOException("TCP message destination differs from its connection");
            sendMessage(message, retry);
        }

        @Override public void close(boolean removeSocket, boolean stopKeepAliveTask) {
            if (!closed.compareAndSet(false, true)) return;
            try { super.close(false, stopKeepAliveTask); }
            finally { owner.released(this); }
        }

        void closeSocket() {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    /** RI still controls framing and starvation timers, but every read consumes the socket directly. */
    static final class DirectPipeline extends Pipeline {
        private final BufferedInputStream input;

        DirectPipeline(InputStream input, int readTimeout, SipTimer timer) {
            this(new BufferedInputStream(input, 4096), readTimeout, timer);
        }

        private DirectPipeline(BufferedInputStream input, int readTimeout, SipTimer timer) {
            super(input, readTimeout, timer);
            this.input = input;
        }

        @Override public int read() throws IOException { return input.read(); }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException { return input.read(bytes, offset, length); }
        @Override public void close() throws IOException { stopTimer(); super.close(); }
    }
}
