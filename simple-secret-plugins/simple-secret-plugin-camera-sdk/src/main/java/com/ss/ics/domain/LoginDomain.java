package com.ss.ics.domain;

import java.io.Serial;
import java.io.Serializable;

/** 厂商 SDK 设备登录参数。 */
public class LoginDomain implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String username;
    private String password;
    private String ip;
    private String port;

    /** @return 设备用户名 */
    public String getUsername() {
        return username;
    }

    /** @param username 设备用户名 @return 当前对象 */
    public LoginDomain setUsername(String username) {
        this.username = username;
        return this;
    }

    /** @return 设备密码 */
    public String getPassword() {
        return password;
    }

    /** @param password 设备密码 @return 当前对象 */
    public LoginDomain setPassword(String password) {
        this.password = password;
        return this;
    }

    /** @return 设备 IP 或主机名 */
    public String getIp() {
        return ip;
    }

    /** @param ip 设备 IP 或主机名 @return 当前对象 */
    public LoginDomain setIp(String ip) {
        this.ip = ip;
        return this;
    }

    /** @return 厂商 SDK 登录端口 */
    public String getPort() {
        return port;
    }

    /** @param port 厂商 SDK 登录端口 @return 当前对象 */
    public LoginDomain setPort(String port) {
        this.port = port;
        return this;
    }
}
