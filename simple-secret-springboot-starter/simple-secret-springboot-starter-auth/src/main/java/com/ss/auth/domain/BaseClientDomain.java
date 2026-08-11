package com.ss.auth.domain;

import java.util.List;

/**
 * 客户端认证配置。
 */
public class BaseClientDomain {
    private String clientId;
    private String clientKey;
    private String clientSecret;
    private List<String> grantTypeList;
    private String deviceType;
    private Long activeTimeout;
    private Long timeout;
    private ClientStatus status;

    /**
     * 获取客户端标识。
     *
     * @return 客户端标识
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * 设置客户端标识。
     *
     * @param clientId 客户端标识
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * 获取客户端键。
     *
     * @return 客户端键
     */
    public String getClientKey() {
        return clientKey;
    }

    /**
     * 设置客户端键。
     *
     * @param clientKey 客户端键
     */
    public void setClientKey(String clientKey) {
        this.clientKey = clientKey;
    }

    /**
     * 获取客户端密钥。
     *
     * @return 客户端密钥
     */
    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * 设置客户端密钥。
     *
     * @param clientSecret 客户端密钥
     */
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    /**
     * 获取客户端状态。
     *
     * @return 客户端状态
     */
    public ClientStatus getStatus() {
        return status;
    }

    /**
     * 设置客户端状态。
     *
     * @param status 客户端状态
     */
    public void setStatus(ClientStatus status) {
        this.status = status;
    }

    /**
     * 获取客户端允许的授权类型。
     *
     * @return 允许的授权类型列表
     */
    public List<String> getGrantTypeList() {
        return grantTypeList;
    }

    /**
     * 设置客户端允许的授权类型。
     *
     * @param grantTypeList 允许的授权类型列表
     */
    public void setGrantTypeList(List<String> grantTypeList) {
        this.grantTypeList = grantTypeList;
    }

    /**
     * 获取设备类型。
     *
     * @return 设备类型
     */
    public String getDeviceType() {
        return deviceType;
    }

    /**
     * 设置设备类型。
     *
     * @param deviceType 设备类型
     */
    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    /**
     * 获取令牌活跃超时时间。
     *
     * @return 令牌活跃超时时间
     */
    public Long getActiveTimeout() {
        return activeTimeout;
    }

    /**
     * 设置令牌活跃超时时间。
     *
     * @param activeTimeout 令牌活跃超时时间
     */
    public void setActiveTimeout(Long activeTimeout) {
        this.activeTimeout = activeTimeout;
    }

    /**
     * 获取令牌固定超时时间。
     *
     * @return 令牌固定超时时间
     */
    public Long getTimeout() {
        return timeout;
    }

    /**
     * 设置令牌固定超时时间。
     *
     * @param timeout 令牌固定超时时间
     */
    public void setTimeout(Long timeout) {
        this.timeout = timeout;
    }
}
