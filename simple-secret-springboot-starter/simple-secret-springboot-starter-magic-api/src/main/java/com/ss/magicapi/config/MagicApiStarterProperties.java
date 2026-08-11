package com.ss.magicapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Simple Secret 对 Magic API starter 的启用配置。
 */
@ConfigurationProperties("simple-secret.magic-api")
public class MagicApiStarterProperties {
    /** 是否允许加载 Magic API 上游自动配置。 */
    private boolean enabled;

    /**
     * 返回是否启用 Magic API。
     *
     * @return 启用状态
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用 Magic API。
     *
     * @param enabled 启用状态
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
