package com.ss.idempotent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Simple Secret 重复提交保护配置。 */
@ConfigurationProperties("simple-secret.idempotent")
public class IdempotentProperties {

    /** 是否启用自动配置。 */
    private boolean enabled = true;

    /** 分布式存储 key 前缀。 */
    private String keyPrefix = "simple-secret:idempotent:";

    /** 默认可信身份请求头。 */
    private String identityHeader = "Authorization";

    /**
     * 返回是否启用。
     *
     * @return true 表示启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回 key 前缀。
     *
     * @return key 前缀
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }

    /**
     * 设置 key 前缀。
     *
     * @param keyPrefix 非空 key 前缀
     */
    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = requireText(keyPrefix, "key-prefix");
    }

    /**
     * 返回身份请求头。
     *
     * @return 请求头名称
     */
    public String getIdentityHeader() {
        return identityHeader;
    }

    /**
     * 设置身份请求头。
     *
     * @param identityHeader 非空请求头名称
     */
    public void setIdentityHeader(String identityHeader) {
        this.identityHeader = requireText(identityHeader, "identity-header");
    }

    private static String requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "simple-secret.idempotent." + property + " must not be blank.");
        }
        return value.trim();
    }
}
