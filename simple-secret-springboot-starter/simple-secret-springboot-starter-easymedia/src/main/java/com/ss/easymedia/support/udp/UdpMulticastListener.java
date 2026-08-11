package com.ss.easymedia.support.udp;

/**
 * EasyMedia 旧版 UDP 组播监听器。
 *
 * @deprecated 请直接依赖 {@code simple-secret-plugin-udp} 并使用
 * {@link com.ss.udp.UdpMulticastListener}。
 */
@Deprecated(since = "1.1.0")
public class UdpMulticastListener extends com.ss.udp.UdpMulticastListener {

    public UdpMulticastListener(String groupIp, int port, String localIp,
                                UdpMessageHandler messageHandler) {
        super(groupIp, port, localIp, messageHandler);
    }
}
