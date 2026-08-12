package com.ss.easymedia.config.properties;

/**
 * WebRTC 安全配置。
 */
public class WebRtcSecurityProperties {

    /**
     * 是否要求登录身份。
     */
    private boolean authenticationRequired = true;

    /**
     * 未接入统一认证时使用的固定主体标识；配置后所有请求以此身份认证。
     */
    private String defaultSubject;

    /**
     * 固定主体所在租户标识。
     */
    private String defaultTenantId = "000000";

    /**
     * 判断是否要求认证。
     *
     * @return 满足条件时返回 true
     */
    public boolean isAuthenticationRequired() {
        return authenticationRequired;
    }

    /**
     * 设置是否要求认证。
     *
     * @param authenticationRequired 是否要求认证
     */
    public void setAuthenticationRequired(boolean authenticationRequired) {
        this.authenticationRequired = authenticationRequired;
    }

    /**
     * 返回未接入认证时使用的默认主体。
     *
     * @return 未接入认证时使用的默认主体
     */
    public String getDefaultSubject() {
        return defaultSubject;
    }

    /**
     * 设置{@code defaultSubject}。
     *
     * @param defaultSubject 未接入认证时使用的默认主体
     */
    public void setDefaultSubject(String defaultSubject) {
        this.defaultSubject = defaultSubject;
    }

    /**
     * 返回默认租户标识。
     *
     * @return 默认租户标识
     */
    public String getDefaultTenantId() {
        return defaultTenantId;
    }

    /**
     * 设置{@code defaultTenantId}。
     *
     * @param defaultTenantId 默认租户标识
     */
    public void setDefaultTenantId(String defaultTenantId) {
        this.defaultTenantId = defaultTenantId;
    }
}
