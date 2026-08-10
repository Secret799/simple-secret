package com.ss.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Objects;

/**
 * UDP 单播监听器。
 *
 * @author JunPzx
 * @since 1.1.0
 */
public class UdpUnicastListener extends Thread {

    private static final System.Logger LOG = System.getLogger(UdpUnicastListener.class.getName());
    private static final int RECEIVE_TIMEOUT_MILLIS = 250;

    private final String bindIp;
    private final int port;
    private final InetAddress bindAddress;
    private final UdpMessageHandler messageHandler;
    private volatile DatagramSocket socket;
    private volatile boolean running;
    private volatile boolean stopRequested;
    private int maxMessageLength = 1500;

    /**
     * 创建单播监听器。
     *
     * @param bindIp 绑定的数值 IP，可使用 0.0.0.0 或 ::
     * @param port 监听端口
     * @param messageHandler 消息处理器
     */
    public UdpUnicastListener(String bindIp, int port, UdpMessageHandler messageHandler) {
        this.port = UdpAddressValidator.requirePort(port);
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler");
        this.bindAddress = UdpAddressValidator.requireBindAddress(bindIp);
        this.bindIp = bindAddress.getHostAddress();
        setName("udp-unicast-" + this.bindIp + "-" + port);
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            DatagramSocket currentSocket = createSocket();
            socket = currentSocket;
            currentSocket.setSoTimeout(RECEIVE_TIMEOUT_MILLIS);
            running = !stopRequested;
            LOG.log(System.Logger.Level.INFO, "UDP unicast listener started: {0}:{1}", bindIp, port);
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
        }
    }

    DatagramSocket createSocket() throws SocketException {
        return new DatagramSocket(new InetSocketAddress(bindAddress, port));
    }

    private void receive(DatagramSocket currentSocket) throws IOException {
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
        closeSocket();
    }

    private void closeSocket() {
        DatagramSocket currentSocket = socket;
        if (currentSocket != null && !currentSocket.isClosed()) {
            currentSocket.close();
        }
    }

    private void reportError(Exception exception) {
        LOG.log(System.Logger.Level.ERROR, "UDP unicast listener error", exception);
        try {
            messageHandler.onError(exception);
        } catch (RuntimeException callbackException) {
            LOG.log(System.Logger.Level.ERROR, "UDP unicast error callback failed", callbackException);
        }
    }

    public String getKey() {
        return bindIp + ":" + port;
    }

    public String getBindIp() {
        return bindIp;
    }

    public int getPort() {
        return port;
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
