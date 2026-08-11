package com.ss.auth.domain;

/**
 * 通用登录请求参数。
 */
public class BaseLoginBody {
    private String tenantId;
    private String grantType;
    private String clientId;
    private String code;
    private String uuid;

    /**
     * 获取租户标识。
     *
     * @return 租户标识
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * 设置租户标识。
     *
     * @param tenantId 租户标识
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * 获取请求的授权类型。
     *
     * @return 授权类型
     */
    public String getGrantType() {
        return grantType;
    }

    /**
     * 设置请求的授权类型。
     *
     * @param grantType 授权类型
     */
    public void setGrantType(String grantType) {
        this.grantType = grantType;
    }

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
     * 获取验证码。
     *
     * @return 验证码
     */
    public String getCode() {
        return code;
    }

    /**
     * 设置验证码。
     *
     * @param code 验证码
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * 获取验证码唯一标识。
     *
     * @return 验证码唯一标识
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * 设置验证码唯一标识。
     *
     * @param uuid 验证码唯一标识
     */
    public void setUuid(String uuid) {
        this.uuid = uuid;
    }
}
