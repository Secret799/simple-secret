package com.ss.camera.domain;

/**
 * 摄像机 RTSP 地址组装参数。
 *
 * <p>该类型保留链式 setter，便于从 Honeybee 迁移现有调用代码。</p>
 */
public class StreamUrlAssemblyDomain {
    private String ip;
    private String port;
    private String account;
    private String password;
    private String channelNo;
    private String streamType;
    private String brand;
    private String type;

    /** @return 设备 IP 地址或主机名 */
    public String getIp() {
        return ip;
    }

    /**
     * @param ip 设备 IP 地址或主机名
     * @return 当前参数对象
     */
    public StreamUrlAssemblyDomain setIp(String ip) {
        this.ip = ip;
        return this;
    }

    /** @return RTSP 端口 */
    public String getPort() {
        return port;
    }

    /**
     * @param port RTSP 端口
     * @return 当前参数对象
     */
    public StreamUrlAssemblyDomain setPort(String port) {
        this.port = port;
        return this;
    }

    /** @return 登录账号 */
    public String getAccount() {
        return account;
    }

    /**
     * @param account 登录账号
     * @return 当前参数对象
     */
    public StreamUrlAssemblyDomain setAccount(String account) {
        this.account = account;
        return this;
    }

    /** @return 登录密码 */
    public String getPassword() {
        return password;
    }

    /**
     * @param password 登录密码
     * @return 当前参数对象
     */
    public StreamUrlAssemblyDomain setPassword(String password) {
        this.password = password;
        return this;
    }

    /** @return 设备通道号 */
    public String getChannelNo() {
        return channelNo;
    }

    /**
     * @param channelNo 设备通道号
     * @return 当前参数对象
     */
    public StreamUrlAssemblyDomain setChannelNo(String channelNo) {
        this.channelNo = channelNo;
        return this;
    }

    /** @return 码流类型，例如 {@code main} 或 {@code sub} */
    public String getStreamType() {
        return streamType;
    }

    /**
     * @param streamType 码流类型，例如 {@code main} 或 {@code sub}
     * @return 当前参数对象
     */
    public StreamUrlAssemblyDomain setStreamType(String streamType) {
        this.streamType = streamType;
        return this;
    }

    /** @return 设备品牌编码 */
    public String getBrand() {
        return brand;
    }

    /**
     * @param brand 设备品牌编码
     * @return 当前参数对象
     */
    public StreamUrlAssemblyDomain setBrand(String brand) {
        this.brand = brand;
        return this;
    }

    /** @return 设备类型编码 */
    public String getType() {
        return type;
    }

    /**
     * @param type 设备类型编码
     * @return 当前参数对象
     */
    public StreamUrlAssemblyDomain setType(String type) {
        this.type = type;
        return this;
    }
}
