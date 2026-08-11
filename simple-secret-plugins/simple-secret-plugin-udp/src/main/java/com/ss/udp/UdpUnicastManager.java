package com.ss.udp;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UDP 单播监听器管理器。
 *
 * @author JunPzx
 * @since 1.1.0
 */
public class UdpUnicastManager {

    private static final System.Logger LOG = System.getLogger(UdpUnicastManager.class.getName());
    private static final long STOP_WAIT_MILLIS = 1_000;

    private final Map<String, UdpUnicastListener> activeListeners = new ConcurrentHashMap<>();

    /** 注册并启动监听器。 */
    public boolean startListener(UdpUnicastListener listener) {
        Objects.requireNonNull(listener, "listener");
        if (listener.getState() != Thread.State.NEW) {
            throw new IllegalThreadStateException("listener has already been started");
        }
        String key = listener.getKey();
        if (activeListeners.putIfAbsent(key, listener) != null) {
            return false;
        }
        try {
            listener.setTerminationCallback(() -> activeListeners.remove(key, listener));
            listener.start();
            return true;
        } catch (RuntimeException exception) {
            activeListeners.remove(key, listener);
            throw exception;
        }
    }

    /** 创建、注册并启动监听器。 */
    public boolean startListener(String bindIp, int port, UdpMessageHandler messageHandler) {
        return startListener(new UdpUnicastListener(bindIp, port, messageHandler));
    }

    /** 停止并移除指定监听器。 */
    public boolean stopListener(String bindIp, int port) {
        String key = key(bindIp, port);
        UdpUnicastListener listener = activeListeners.remove(key);
        if (listener == null) {
            return false;
        }
        stopAndAwait(listener, key);
        return true;
    }

    /** 停止全部监听器。 */
    public void shutdownAll() {
        activeListeners.forEach((key, listener) -> {
            if (activeListeners.remove(key, listener)) {
                stopAndAwait(listener, key);
            }
        });
    }

    public int getActiveListenerCount() {
        return activeListeners.size();
    }

    private static String key(String bindIp, int port) {
        return UdpAddressValidator.requireBindAddress(bindIp).getHostAddress()
                + ":" + UdpAddressValidator.requirePort(port);
    }

    private void stopAndAwait(UdpUnicastListener listener, String key) {
        listener.stopListener();
        if (Thread.currentThread() == listener) {
            return;
        }
        try {
            listener.join(STOP_WAIT_MILLIS);
            if (listener.isAlive()) {
                LOG.log(System.Logger.Level.WARNING,
                        "Timed out waiting for UDP unicast listener: {0}", key);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOG.log(System.Logger.Level.WARNING,
                    "Interrupted while waiting for UDP unicast listener: {0}", key);
        }
    }
}
