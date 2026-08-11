package com.ss.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Objects;

/**
 * UDP 组播监听器。
 *
 * @author JunPzx
 * @since 1.1.0
 */
public class UdpMulticastListener extends Thread {

    private static final System.Logger LOG = System.getLogger(UdpMulticastListener.class.getName());
    private static final int RECEIVE_TIMEOUT_MILLIS = 250;

    private final String groupIp;
    private final int port;
    private final String localIp;
    private final UdpMessageHandler messageHandler;
    private final InetAddress groupAddress;
    private final NetworkInterface networkInterface;
    private volatile MulticastSocket socket;
    private volatile boolean running;
    private volatile boolean stopRequested;
    private Runnable terminationCallback = () -> { };
    private int maxMessageLength = 1024;

    /**
     * 创建组播监听器。
     *
     * @param groupIp 组播数值 IP
     * @param port 监听端口
     * @param localIp 本地网卡数值 IP
     * @param messageHandler 消息处理器
     */
    public UdpMulticastListener(String groupIp, int port, String localIp,
                                UdpMessageHandler messageHandler) {
        this.port = UdpAddressValidator.requirePort(port);
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler");
        this.groupAddress = UdpAddressValidator.requireMulticastAddress(groupIp);
        InetAddress localAddress = UdpAddressValidator.requireLocalInterfaceAddress(localIp);
        UdpAddressValidator.requireSameAddressFamily(groupAddress, localAddress);
        this.networkInterface = UdpAddressValidator.requireNetworkInterface(localAddress);
        this.groupIp = groupAddress.getHostAddress();
        this.localIp = localAddress.getHostAddress();
        setName("udp-multicast-" + this.localIp + "-" + this.groupIp + "-" + port);
        setDaemon(true);
    }

    @Override
    public void run() {
        SocketAddress groupSocketAddress = new InetSocketAddress(groupAddress, port);
        try {
            MulticastSocket currentSocket = new MulticastSocket(port);
            socket = currentSocket;
            currentSocket.joinGroup(groupSocketAddress, networkInterface);
            currentSocket.setSoTimeout(RECEIVE_TIMEOUT_MILLIS);
            running = !stopRequested;
            LOG.log(System.Logger.Level.INFO, "UDP multicast listener started: {0}:{1}:{2}",
                    localIp, groupIp, port);
            while (running && !stopRequested) {
                receive(currentSocket);
            }
        } catch (SocketException exception) {
            if (!stopRequested) {
                reportError(exception);
            }
        } catch (Exception exception) {
            if (!stopRequested) {
                reportError(exception);
            }
        } finally {
            running = false;
            closeSocket();
            notifyTermination();
        }
    }

    private void receive(MulticastSocket currentSocket) throws IOException {
        DatagramPacket packet = new DatagramPacket(
                new byte[maxMessageLength], maxMessageLength);
        try {
            currentSocket.receive(packet);
            try {
                messageHandler.handle(packet);
            } catch (Exception exception) {
                reportError(exception);
            }
        } catch (SocketTimeoutException ignored) {
            // 仅用于周期性检查 stopRequested，不把空闲误判为断线。
        }
    }

    /** 停止监听并释放 socket。 */
    public void stopListener() {
        stopRequested = true;
        running = false;
        MulticastSocket currentSocket = socket;
        if (currentSocket == null || currentSocket.isClosed()) {
            return;
        }
        try {
            currentSocket.leaveGroup(new InetSocketAddress(groupAddress, port), networkInterface);
        } catch (IOException exception) {
            reportError(exception);
        } finally {
            currentSocket.close();
        }
    }

    private void closeSocket() {
        MulticastSocket currentSocket = socket;
        if (currentSocket != null && !currentSocket.isClosed()) {
            currentSocket.close();
        }
    }

    private void reportError(Exception exception) {
        LOG.log(System.Logger.Level.ERROR, "UDP multicast listener error", exception);
        try {
            messageHandler.onError(exception);
        } catch (RuntimeException callbackException) {
            LOG.log(System.Logger.Level.ERROR, "UDP multicast error callback failed", callbackException);
        }
    }

    void setTerminationCallback(Runnable terminationCallback) {
        if (getState() != State.NEW) {
            throw new IllegalStateException("termination callback must be configured before listener start");
        }
        this.terminationCallback = Objects.requireNonNull(terminationCallback, "terminationCallback");
    }

    private void notifyTermination() {
        try {
            terminationCallback.run();
        } catch (RuntimeException exception) {
            LOG.log(System.Logger.Level.ERROR, "UDP multicast termination callback failed", exception);
        }
    }

    /** @return 组播 IP 与端口组成的兼容 key */
    public String getKey() {
        return groupIp + ":" + port;
    }

    public String getGroupIp() {
        return groupIp;
    }

    public int getPort() {
        return port;
    }

    public String getLocalIp() {
        return localIp;
    }

    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * 设置最大消息长度，只能在线程启动前调用。
     *
     * @param maxMessageLength 最大 UDP payload 长度
     */
    public void setMaxMessageLength(Integer maxMessageLength) {
        if (getState() != State.NEW) {
            throw new IllegalStateException("maxMessageLength must be configured before listener start");
        }
        this.maxMessageLength = UdpAddressValidator.requirePayloadLength(maxMessageLength);
    }
}
