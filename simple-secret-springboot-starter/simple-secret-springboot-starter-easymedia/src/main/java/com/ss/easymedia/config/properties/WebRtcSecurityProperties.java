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

    public boolean isAuthenticationRequired() {
        return authenticationRequired;
    }

    public void setAuthenticationRequired(boolean authenticationRequired) {
        this.authenticationRequired = authenticationRequired;
    }

    public String getDefaultSubject() {
        return defaultSubject;
    }

    public void setDefaultSubject(String defaultSubject) {
        this.defaultSubject = defaultSubject;
    }

    public String getDefaultTenantId() {
        return defaultTenantId;
    }

    public void setDefaultTenantId(String defaultTenantId) {
        this.defaultTenantId = defaultTenantId;
    }
}
