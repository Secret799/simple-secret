package com.ss.easymedia.support.udp;

/**
 * EasyMedia 旧版 UDP 消息处理器。
 *
 * @deprecated 请直接依赖 {@code simple-secret-plugin-udp} 并使用
 * {@link com.ss.udp.UdpMessageHandler}。
 */
@Deprecated(since = "1.1.0")
@FunctionalInterface
public interface UdpMessageHandler extends com.ss.udp.UdpMessageHandler {
}
