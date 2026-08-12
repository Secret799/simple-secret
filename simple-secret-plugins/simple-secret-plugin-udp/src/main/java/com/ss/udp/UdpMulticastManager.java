package com.ss.udp;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UDP 组播监听器管理器。
 *
 * @author JunPzx
 * @since 1.1.0
 */
public class UdpMulticastManager {

    private static final System.Logger LOG = System.getLogger(UdpMulticastManager.class.getName());
    private static final long STOP_WAIT_MILLIS = 1_000;

    private final Map<String, UdpMulticastListener> activeGroups = new ConcurrentHashMap<>();

    /**
     * 注册并启动监听器。
     *
     * @return key 未被占用并成功调用 start 时返回 true

     *
     * @param listener 事件监听器
     */
    public boolean joinGroup(UdpMulticastListener listener) {
        Objects.requireNonNull(listener, "listener");
        if (listener.getState() != Thread.State.NEW) {
            throw new IllegalThreadStateException("listener has already been started");
        }
        String key = key(listener.getLocalIp(), listener.getGroupIp(), listener.getPort());
        if (activeGroups.putIfAbsent(key, listener) != null) {
            return false;
        }
        try {
            listener.setTerminationCallback(() -> activeGroups.remove(key, listener));
            listener.start();
            return true;
        } catch (RuntimeException exception) {
            activeGroups.remove(key, listener);
            throw exception;
        }
    }

    /**
     * 创建、注册并启动监听器。
     *
     * @param groupIp 组播组 IP 地址
     * @param port 监听或连接端口
     * @param localIp 本地网卡 IP 地址
     * @param messageHandler 文本消息处理器
     * @return 返回的 {@code boolean} 结果
     */
    public boolean joinGroup(String groupIp, int port, String localIp,
                             UdpMessageHandler messageHandler) {
        return joinGroup(new UdpMulticastListener(groupIp, port, localIp, messageHandler));
    }

    /**
     * 停止并移除指定监听器。
     *
     * @param groupIp 组播组 IP 地址
     * @param port 监听或连接端口
     * @param localIp 本地网卡 IP 地址
     * @return 返回的 {@code boolean} 结果
     */
    public boolean leaveGroup(String groupIp, int port, String localIp) {
        String key = key(localIp, groupIp, port);
        UdpMulticastListener listener = activeGroups.remove(key);
        if (listener == null) {
            return false;
        }
        stopAndAwait(listener, key);
        return true;
    }

    /** 停止全部监听器。 */
    public void shutdownAll() {
        activeGroups.forEach((key, listener) -> {
            if (activeGroups.remove(key, listener)) {
                stopAndAwait(listener, key);
            }
        });
    }

    /**
     * 返回当前活动的组播监听组数量。
     *
     * @return 当前活动的组播监听组数量
     */
    public int getActiveGroupCount() {
        return activeGroups.size();
    }

    private void stopAndAwait(UdpMulticastListener listener, String key) {
        listener.stopListener();
        if (Thread.currentThread() == listener) {
            return;
        }
        try {
            listener.join(STOP_WAIT_MILLIS);
            if (listener.isAlive()) {
                LOG.log(System.Logger.Level.WARNING,
                        "Timed out waiting for UDP multicast listener: {0}", key);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOG.log(System.Logger.Level.WARNING,
                    "Interrupted while waiting for UDP multicast listener: {0}", key);
        }
    }

    private static String key(String localIp, String groupIp, int port) {
        var localAddress = UdpAddressValidator.requireLocalInterfaceAddress(localIp);
        var groupAddress = UdpAddressValidator.requireMulticastAddress(groupIp);
        UdpAddressValidator.requireSameAddressFamily(groupAddress, localAddress);
        return localAddress.getHostAddress() + ":" + groupAddress.getHostAddress()
                + ":" + UdpAddressValidator.requirePort(port);
    }
}
