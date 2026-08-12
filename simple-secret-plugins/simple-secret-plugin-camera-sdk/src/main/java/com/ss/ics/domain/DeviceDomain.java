package com.ss.ics.domain;

import java.io.Serial;
import java.io.Serializable;

/** 厂商 SDK 操作所需的设备信息。 */
public class DeviceDomain implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 设备标识。
     */
    private String deviceId;
    /**
     * 设备名称。
     */
    private String deviceName;
    /**
     * 用户名。
     */
    private String username;
    /**
     * 密码。
     */
    private String password;
    /**
     * 设备 IP 地址。
     */
    private String ip;
    /**
     * 监听或连接端口。
     */
    private String port;
    /**
     * 通道。
     */
    private String channel;

    /** @return SDK 内部设备标识 */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * @param deviceId SDK 内部设备标识
     *
     * @return 当前对象
     */
    public DeviceDomain setDeviceId(String deviceId) {
        this.deviceId = deviceId;
        return this;
    }

    /** @return 设备名称 */
    public String getDeviceName() {
        return deviceName;
    }

    /**
     * @param deviceName 设备名称
     *
     * @return 当前对象
     */
    public DeviceDomain setDeviceName(String deviceName) {
        this.deviceName = deviceName;
        return this;
    }

    /** @return 设备用户名 */
    public String getUsername() {
        return username;
    }

    /**
     * @param username 设备用户名
     *
     * @return 当前对象
     */
    public DeviceDomain setUsername(String username) {
        this.username = username;
        return this;
    }

    /** @return 设备密码 */
    public String getPassword() {
        return password;
    }

    /**
     * @param password 设备密码
     *
     * @return 当前对象
     */
    public DeviceDomain setPassword(String password) {
        this.password = password;
        return this;
    }

    /** @return 设备 IP 或主机名 */
    public String getIp() {
        return ip;
    }

    /**
     * @param ip 设备 IP 或主机名
     *
     * @return 当前对象
     */
    public DeviceDomain setIp(String ip) {
        this.ip = ip;
        return this;
    }

    /** @return 厂商 SDK 登录端口 */
    public String getPort() {
        return port;
    }

    /**
     * @param port 厂商 SDK 登录端口
     *
     * @return 当前对象
     */
    public DeviceDomain setPort(String port) {
        this.port = port;
        return this;
    }

    /** @return 设备通道 */
    public String getChannel() {
        return channel;
    }

    /**
     * @param channel 设备通道
     *
     * @return 当前对象
     */
    public DeviceDomain setChannel(String channel) {
        this.channel = channel;
        return this;
    }

    /**
     * 转换为登录参数，不修剪或记录凭据。
     *
     * @return 登录参数副本
     */
    public LoginDomain toLoginDomain() {
        return new LoginDomain()
                .setUsername(username)
                .setPassword(password)
                .setIp(ip)
                .setPort(port);
    }
}
