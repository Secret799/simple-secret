package com.ss.easymedia.support.udp;

/**
 * EasyMedia 旧版 UDP 组播管理器。
 *
 * @deprecated 请直接依赖 {@code simple-secret-plugin-udp} 并使用
 * {@link com.ss.udp.UdpMulticastManager}。
 */
@Deprecated(since = "1.1.0")
public class UdpMulticastManager {

    private final com.ss.udp.UdpMulticastManager delegate = new com.ss.udp.UdpMulticastManager();

    /**
     * 注册并启动旧版监听器。
     *
     * @param listener 事件监听器
     */
    public void joinGroup(UdpMulticastListener listener) {
        delegate.joinGroup(listener);
    }

    /**
     * 创建并启动组播监听器。
     *
     * @param groupIp 组播组 IP 地址
     * @param port 监听或连接端口
     * @param localIp 本地网卡 IP 地址
     * @param messageHandler 文本消息处理器
     */
    public void joinGroup(String groupIp, int port, String localIp,
                          UdpMessageHandler messageHandler) {
        delegate.joinGroup(groupIp, port, localIp, messageHandler);
    }

    /**
     * 停止并移除组播监听器。
     *
     * @param groupIp 组播组 IP 地址
     * @param port 监听或连接端口
     * @param localIp 本地网卡 IP 地址
     */
    public void leaveGroup(String groupIp, int port, String localIp) {
        delegate.leaveGroup(groupIp, port, localIp);
    }

    /** 停止全部组播监听器。 */
    public void shutdownAll() {
        delegate.shutdownAll();
    }

    /** @return 当前活跃组播监听器数量 */
    public int getActiveGroupCount() {
        return delegate.getActiveGroupCount();
    }
}
