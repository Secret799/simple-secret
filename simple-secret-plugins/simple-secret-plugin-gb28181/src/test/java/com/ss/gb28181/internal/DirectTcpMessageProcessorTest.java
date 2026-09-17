package com.ss.gb28181.internal;

import gov.nist.javax.sip.SipStackImpl;
import gov.nist.javax.sip.message.SIPMessage;
import gov.nist.javax.sip.parser.PipelinedMsgParser;
import gov.nist.javax.sip.parser.SIPMessageListener;
import gov.nist.javax.sip.stack.TCPMessageChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class DirectTcpMessageProcessorTest {
    @Test
    void outboundConnectionsAreReusedAndShareTheConnectionLimit() throws Exception {
        SipStackImpl stack = stack();
        int port;
        try (ServerSocket reserve = new ServerSocket(0)) { port = reserve.getLocalPort(); }
        var processor = new DirectTcpMessageProcessor(InetAddress.getLoopbackAddress(), stack, port);
        try (ServerSocket peer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            processor.start();
            var channel = processor.createMessageChannel(InetAddress.getLoopbackAddress(), peer.getLocalPort());
            try (Socket accepted = peer.accept()) {
                accepted.setSoTimeout(2000);
                ((TCPMessageChannel) channel).sendMessage(new byte[]{1, 2, 3}, InetAddress.getLoopbackAddress(), peer.getLocalPort(), false);
                assertArrayEquals(new byte[]{1, 2, 3}, accepted.getInputStream().readNBytes(3));
                assertSame(channel, processor.createMessageChannel(InetAddress.getLoopbackAddress(), peer.getLocalPort()));
                assertThrows(IOException.class, () -> processor.createMessageChannel(InetAddress.getLoopbackAddress(), port));
                processor.stop();
                assertEquals(-1, accepted.getInputStream().read());
            }
            assertThrows(IOException.class, () -> processor.createMessageChannel(InetAddress.getLoopbackAddress(), peer.getLocalPort()));
        } finally { processor.stop(); stack.stop(); }
        try (ServerSocket rebound = new ServerSocket(port)) { assertTrue(rebound.isBound()); }
    }

    @Test
    void peerThatDoesNotReadCannotBlockWritesIndefinitely() throws Exception {
        SipStackImpl stack = stack();
        int port;
        try (ServerSocket reserve = new ServerSocket(0)) { port = reserve.getLocalPort(); }
        var processor = new DirectTcpMessageProcessor(InetAddress.getLoopbackAddress(), stack, port, 100);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket peer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            peer.setReceiveBufferSize(1024);
            peer.setSoTimeout(2000);
            processor.start();
            var channel = (TCPMessageChannel) processor.createMessageChannel(InetAddress.getLoopbackAddress(), peer.getLocalPort());
            try (Socket accepted = peer.accept()) {
                accepted.setReceiveBufferSize(1024);
                Future<?> write = executor.submit(() -> {
                    assertThrows(IOException.class, () -> channel.sendMessage(new byte[8 * 1024 * 1024],
                            InetAddress.getLoopbackAddress(), peer.getLocalPort(), false));
                });
                write.get(2, TimeUnit.SECONDS);
                processor.stop();
            }
        } finally { processor.stop(); executor.shutdownNow(); stack.stop(); }
    }

    @Test
    void eofReleasesInboundConnectionCapacity() throws Exception {
        SipStackImpl stack = stack();
        int port;
        try (ServerSocket reserve = new ServerSocket(0)) { port = reserve.getLocalPort(); }
        var processor = new DirectTcpMessageProcessor(InetAddress.getLoopbackAddress(), stack, port);
        try {
            processor.start();
            try (Socket first = new Socket(InetAddress.getLoopbackAddress(), port);
                 Socket extra = new Socket(InetAddress.getLoopbackAddress(), port)) {
                extra.setSoTimeout(2000);
                assertEquals(-1, extra.getInputStream().read(), "excess inbound connection must be closed");
            }
            try (ServerSocket peer = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (true) {
                    try {
                        processor.createMessageChannel(InetAddress.getLoopbackAddress(), peer.getLocalPort());
                        break;
                    } catch (IOException e) {
                        if (System.nanoTime() >= deadline) throw e;
                        Thread.sleep(10);
                    }
                }
                try (Socket accepted = peer.accept()) {
                    accepted.setSoTimeout(2000);
                    processor.stop();
                    assertEquals(-1, accepted.getInputStream().read());
                }
            }
        } finally { processor.stop(); stack.stop(); }
    }

    @Test
    void blockedMessageConsumerDoesNotContinueReadingSocket() throws Exception {
        SipStackImpl stack = stack();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger bytesRead = new AtomicInteger();
        byte[] messages = request(0).repeat(500).getBytes(StandardCharsets.US_ASCII);
        InputStream source = new ByteArrayInputStream(messages) {
            @Override public synchronized int read(byte[] target, int offset, int length) {
                int count = super.read(target, offset, length);
                if (count > 0) bytesRead.addAndGet(count);
                return count;
            }
        };
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (var pipeline = new DirectTcpMessageProcessor.DirectPipeline(source, 1000, stack.getTimer())) {
            PipelinedMsgParser parser = new PipelinedMsgParser(stack, listener(() -> {
                entered.countDown();
                assertTrue(release.await(2, TimeUnit.SECONDS));
            }), pipeline, 4096);
            Future<?> parsing = executor.submit(parser);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(4096, bytesRead.get(), "only the fixed input buffer may be prefetched");
            release.countDown();
            parsing.get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown(); executor.shutdownNow(); stack.stop();
        }
    }

    @Test
    void hugeContentLengthIsRejectedBeforeBodyAllocation() throws Exception {
        SipStackImpl stack = stack();
        try (var pipeline = new DirectTcpMessageProcessor.DirectPipeline(
                new ByteArrayInputStream(request(Integer.MAX_VALUE).getBytes(StandardCharsets.US_ASCII)),
                1000, stack.getTimer())) {
            PipelinedMsgParser parser = new PipelinedMsgParser(stack, listener(() -> fail("oversized body dispatched")), pipeline, 4096);
            RuntimeException failure = assertThrows(RuntimeException.class, parser::run);
            assertTrue(failure.getMessage().contains("Max content size Exceeded"));
        } finally { stack.stop(); }
    }

    @Test
    void closingPipelineUnblocksSocketRead() throws Exception {
        SipStackImpl stack = stack();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch reading = new CountDownLatch(1);
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
             Socket accepted = listener.accept()) {
            InputStream source = new FilterInputStream(accepted.getInputStream()) {
                @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                    reading.countDown();
                    return super.read(bytes, offset, length);
                }
            };
            try (var pipeline = new DirectTcpMessageProcessor.DirectPipeline(source, 1000, stack.getTimer())) {
                Future<Integer> result = executor.submit((Callable<Integer>) pipeline::read);
                assertTrue(reading.await(2, TimeUnit.SECONDS));
                pipeline.close();
                ExecutionException failure = assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
                assertInstanceOf(IOException.class, failure.getCause());
            }
        } finally { executor.shutdownNow(); stack.stop(); }
    }

    private static SipStackImpl stack() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("javax.sip.STACK_NAME", "direct-tcp-test-" + UUID.randomUUID());
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "0");
        properties.setProperty("gov.nist.javax.sip.TCP_POST_PARSING_THREAD_POOL_SIZE", "0");
        properties.setProperty("gov.nist.javax.sip.MAX_CONNECTIONS", "1");
        return new SipStackImpl(properties);
    }

    private static SIPMessageListener listener(CheckedAction action) {
        return new SIPMessageListener() {
            @Override public void processMessage(SIPMessage message) throws Exception { action.run(); }
            @Override public void handleException(ParseException exception, SIPMessage message, Class header,
                                                   String headerText, String messageText) throws ParseException { throw exception; }
            @Override public void sendSingleCLRF() { }
        };
    }

    private static String request(int length) {
        return "MESSAGE sip:34020000002000000001@3402000000 SIP/2.0\r\n"
                + "Via: SIP/2.0/TCP 127.0.0.1:5060;branch=z9hG4bKtest\r\n"
                + "From: <sip:34020000001320000001@3402000000>;tag=test\r\n"
                + "To: <sip:34020000002000000001@3402000000>\r\n"
                + "Call-ID: test\r\nCSeq: 1 MESSAGE\r\nMax-Forwards: 70\r\nContent-Length: " + length + "\r\n\r\n";
    }

    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
}
