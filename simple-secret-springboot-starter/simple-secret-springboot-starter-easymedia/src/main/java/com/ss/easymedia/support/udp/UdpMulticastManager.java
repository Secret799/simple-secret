package com.ss.easymedia.support.udp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * udp组播管理器
 *
 * @author JunPzx
 * @since 2025/11/25 18:37
 */
public class UdpMulticastManager {

    private static final Logger log = LoggerFactory.getLogger(UdpMulticastManager.class);

    // 存储活跃的组播监听器，Key 为 "本地IP:组播IP:Port"
    private final Map<String, UdpMulticastListener> activeGroups = new ConcurrentHashMap<>();


    /**
     * 加入一个组播监听
     *
     * @param listener 监听器
     */
    public void joinGroup(UdpMulticastListener listener) {
        String key = listener.getLocalIp() + ":" + listener.getGroupIp() + ":" + listener.getPort();
        if (activeGroups.putIfAbsent(key, listener) != null) {
            log.warn("已经在监听组:{}", key);
            return;
        }
        try {
            listener.start();
        } catch (RuntimeException exception) {
            activeGroups.remove(key, listener);
            throw exception;
        }
    }

    /**
     * 启动一个新的组播监听
     *
     * @param groupIp        组播组名
     * @param port           端口
     * @param localIp        本地IP
     * @param messageHandler 消息处理器
     */
    public void joinGroup(String groupIp, int port, String localIp, UdpMessageHandler messageHandler) {
        UdpMulticastListener listener = new UdpMulticastListener(groupIp, port, localIp, messageHandler);
        joinGroup(listener);
    }

    /**
     * 离开并停止一个组播监听
     *
     * @param ip      组播组名
     * @param port    端口
     * @param localIp 本地IP
     */
    public void leaveGroup(String ip, int port, String localIp) {
        String key = localIp + ":" + ip + ":" + port;
        UdpMulticastListener listener = activeGroups.remove(key);
        if (listener != null) {
            stopAndAwait(listener, key);
        } else {
            log.warn("未找到正在运行的组:{}", key);
        }
    }


    /**
     * 关闭所有监听器
     */
    public void shutdownAll() {
        activeGroups.forEach((key, listener) -> {
            if (activeGroups.remove(key, listener)) {
                stopAndAwait(listener, key);
            }
        });
    }

    private void stopAndAwait(UdpMulticastListener listener, String key) {
        listener.stopListener();
        if (Thread.currentThread() == listener) {
            return;
        }
        try {
            listener.join(1_000);
            if (listener.isAlive()) {
                log.warn("等待组播监听器退出超时:{}", key);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("等待组播监听器退出时线程被中断:{}", key);
        }
    }
}
