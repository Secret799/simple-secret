package com.ss.easymedia.support.udp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.DatagramPacket;

/**
 * udp 消息处理器
 *
 * @author JunPzx
 * @since 2025/11/25 19:03
 */
public interface UdpMessageHandler {

    /**
     * 处理 udp 消息
     *
     * @param message 消息内容
     */
    void handle(DatagramPacket message);


    /**
     * 处理 udp 错误
     *
     * @param e 错误信息
     */
    default void onError(Exception e) {
    }
}
