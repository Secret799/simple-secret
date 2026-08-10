package com.ss.udp;

import java.net.DatagramPacket;

/**
 * UDP 消息处理器。
 *
 * @author JunPzx
 * @since 1.1.0
 */
@FunctionalInterface
public interface UdpMessageHandler {

    /**
     * 处理收到的数据报。每次回调都持有独立的 payload 缓冲区。
     *
     * @param message 数据报
     */
    void handle(DatagramPacket message);

    /**
     * 处理监听器运行期间发生的错误。
     *
     * @param exception 异常
     */
    default void onError(Exception exception) {
    }
}
