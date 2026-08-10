package com.ss.easymedia.support.udp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 组播监听器
 *
 * @author JunPzx
 * @since 2025/11/25 18:37
 */
public class UdpMulticastListener extends Thread {

    private static final Logger log = LoggerFactory.getLogger(UdpMulticastListener.class);

    /** UDP 数据报允许的最大 payload 长度。 */
    private static final int MAX_UDP_PAYLOAD_LENGTH = 65_507;

    private final String groupIp;
    private final int port;
    private final String localIp;
    private final UdpMessageHandler messageHandler;
    private final InetAddress localAddress;
    private final NetworkInterface networkInterface;
    private MulticastSocket socket;
    private final InetAddress groupAddress;
    private volatile boolean running = true;
    private int maxMessageLength = 1024;
    /**
     * 连续接收消息超时次数
     */
    private final AtomicInteger recMessageTimeoutCount = new AtomicInteger(0);


    public UdpMulticastListener(String groupIp, int port, String localIp, UdpMessageHandler messageHandler) {
        this.port = validatePort(port);
        this.messageHandler = Objects.requireNonNull(messageHandler, "messageHandler");
        this.groupAddress = validateMulticastAddress(groupIp);
        this.localAddress = validateLocalAddress(localIp);
        validateAddressFamily(groupAddress, localAddress);
        this.networkInterface = requireNetworkInterface(localAddress);
        this.groupIp = groupAddress.getHostAddress();
        this.localIp = localAddress.getHostAddress();
    }

    @Override
    public void run() {
        try {
            // 1. 创建组播 Socket，绑定端口
            socket = new MulticastSocket(port);
            // 2. 获取网络接口 (关键步骤：在多网卡环境下，需要指定正确的网卡)
            // 使用 localIp 对应的接口，避免多网卡环境加入错误网卡。
            SocketAddress groupSockAddr = new InetSocketAddress(groupAddress, port);
            // 3. 加入组播组
            socket.joinGroup(groupSockAddr, networkInterface);
            // 设置socket超时时间0.1秒
            socket.setSoTimeout(100);
            log.info(">>> [加入成功] 已监听组播组: {}:{}:{}",
                    networkInterface.getDisplayName(), groupIp, port);
            byte[] buffer = new byte[maxMessageLength];
            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    // 4. 阻塞接收消息
                    socket.receive(packet);
                    recMessageTimeoutCount.set(0);
                    // 处理消息
                    messageHandler.handle(packet);
                } catch (SocketTimeoutException e) {
                    // 连续十个超时周期没有消息后，尝试重新加入组播组。
                    if (recMessageTimeoutCount.get() <= 10) {
                        recMessageTimeoutCount.incrementAndGet();
                        continue;
                    }
                    // 如果超过十个超时计算周期,那么重新加入组
                    try {
                        socket.leaveGroup(groupSockAddr, networkInterface);
                        socket.joinGroup(groupSockAddr, networkInterface);
                        log.info("已重新加入组:{}:{}:{}", localIp, groupIp, port);
                    } catch (IOException ignore) {
                        log.error("重新加入组时发生异常", e);
                    }
                    // 重新连接后将重置超时计数器
                    recMessageTimeoutCount.set(0);
                } catch (SocketException exception) {
                    if (running) {
                        log.error("组播 Socket 异常，监听器将退出", exception);
                        messageHandler.onError(exception);
                    }
                    break;
                } catch (Exception e) {
                    log.error("接收消息时发生错误", e);
                    if (running) {
                        messageHandler.onError(e);
                    }
                }
            }
        } catch (Exception exception) {
            log.error("创建组播时发生错误", exception);
            if (running) {
                messageHandler.onError(exception);
            }
        } finally {
            closeSocket();
        }
    }

    // 停止监听并清理资源
    public void stopListener() {
        running = false;
        MulticastSocket currentSocket = socket;
        if (currentSocket != null && !currentSocket.isClosed()) {
            try {
                // 离开组播组
                if (groupAddress != null) {
                    SocketAddress groupSockAddr = new InetSocketAddress(groupAddress, port);
                    currentSocket.leaveGroup(groupSockAddr, networkInterface);
                }
            } catch (IOException e) {
                log.error("离开组播组时发生错误", e);
            } finally {
                currentSocket.close();
            }
        }
        log.info("--- [退出成功] 已停止监听:{}:{}:{} ", localIp, groupIp, port);
    }

    private void closeSocket() {
        MulticastSocket currentSocket = socket;
        if (currentSocket != null && !currentSocket.isClosed()) {
            currentSocket.close();
        }
    }

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

    public Integer getMaxMessageLength() {
        return maxMessageLength;
    }

    public void setMaxMessageLength(Integer maxMessageLength) {
        if (getState() != State.NEW) {
            throw new IllegalStateException("maxMessageLength must be configured before listener start");
        }
        int value = Objects.requireNonNull(maxMessageLength, "maxMessageLength");
        if (value <= 0 || value > MAX_UDP_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException(
                    "maxMessageLength must be between 1 and " + MAX_UDP_PAYLOAD_LENGTH);
        }
        this.maxMessageLength = value;
    }

    private static InetAddress validateMulticastAddress(String groupIp) {
        InetAddress address = parseAddress(groupIp, "groupIp");
        if (!address.isMulticastAddress()) {
            throw new IllegalArgumentException("groupIp must be a multicast address");
        }
        return address;
    }

    private static InetAddress validateLocalAddress(String localIp) {
        InetAddress address = parseAddress(localIp, "localIp");
        if (address.isMulticastAddress() || address.isAnyLocalAddress()) {
            throw new IllegalArgumentException("localIp must identify a local unicast address");
        }
        return address;
    }

    private static InetAddress parseAddress(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String normalized = value.trim();
        if (!isIpv4Literal(normalized) && !isIpv6Literal(normalized)) {
            throw new IllegalArgumentException(name + " must be a numeric IP address");
        }
        try {
            return InetAddress.getByName(normalized);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException(name + " must be a valid address", exception);
        }
    }

    private static boolean isIpv4Literal(String value) {
        String[] segments = value.split("\\.", -1);
        if (segments.length != 4) {
            return false;
        }
        for (String segment : segments) {
            if (segment.isEmpty() || segment.length() > 3
                    || !segment.chars().allMatch(Character::isDigit)) {
                return false;
            }
            if (Integer.parseInt(segment) > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpv6Literal(String value) {
        return value.indexOf(':') >= 0 && value.chars().allMatch(character ->
                Character.digit(character, 16) >= 0 || character == ':' || character == '.');
    }

    private static void validateAddressFamily(InetAddress groupAddress, InetAddress localAddress) {
        boolean bothIpv4 = groupAddress instanceof Inet4Address && localAddress instanceof Inet4Address;
        boolean bothIpv6 = groupAddress instanceof Inet6Address && localAddress instanceof Inet6Address;
        if (!bothIpv4 && !bothIpv6) {
            throw new IllegalArgumentException("groupIp and localIp must use the same address family");
        }
    }

    private static NetworkInterface requireNetworkInterface(InetAddress localAddress) {
        try {
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(localAddress);
            if (networkInterface == null) {
                throw new IllegalArgumentException("localIp must belong to a local network interface");
            }
            return networkInterface;
        } catch (SocketException exception) {
            throw new IllegalArgumentException("unable to inspect local network interface", exception);
        }
    }

    private static int validatePort(int port) {
        if (port <= 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        return port;
    }
}
