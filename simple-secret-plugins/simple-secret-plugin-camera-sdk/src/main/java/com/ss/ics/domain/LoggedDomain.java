package com.ss.ics.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 厂商 SDK 登录成功后的设备会话摘要。 */
public class LoggedDomain implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String userId;
    private String deviceId;
    private String channelNo;
    private String deviceCategory;
    private String deviceType;
    private LocalDateTime loginTime;

    /** @return 厂商登录句柄的字符串表示 */
    public String getUserId() {
        return userId;
    }

    /** @param userId 厂商登录句柄的字符串表示 @return 当前对象 */
    public LoggedDomain setUserId(String userId) {
        this.userId = userId;
        return this;
    }

    /** @return 设备序列号 */
    public String getDeviceId() {
        return deviceId;
    }

    /** @param deviceId 设备序列号 @return 当前对象 */
    public LoggedDomain setDeviceId(String deviceId) {
        this.deviceId = deviceId;
        return this;
    }

    /** @return 厂商返回的起始通道号 */
    public String getChannelNo() {
        return channelNo;
    }

    /** @param channelNo 厂商返回的起始通道号 @return 当前对象 */
    public LoggedDomain setChannelNo(String channelNo) {
        this.channelNo = channelNo;
        return this;
    }

    /** @return 设备分类 */
    public String getDeviceCategory() {
        return deviceCategory;
    }

    /** @param deviceCategory 设备分类 @return 当前对象 */
    public LoggedDomain setDeviceCategory(String deviceCategory) {
        this.deviceCategory = deviceCategory;
        return this;
    }

    /** @return 设备型号 */
    public String getDeviceType() {
        return deviceType;
    }

    /** @param deviceType 设备型号 @return 当前对象 */
    public LoggedDomain setDeviceType(String deviceType) {
        this.deviceType = deviceType;
        return this;
    }

    /** @return 登录时间 */
    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    /** @param loginTime 登录时间 @return 当前对象 */
    public LoggedDomain setLoginTime(LocalDateTime loginTime) {
        this.loginTime = loginTime;
        return this;
    }
}
