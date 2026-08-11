package com.ss.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Simple Secret 认证 starter 的配置属性。 */
@ConfigurationProperties(prefix = "simple-secret.auth")
public class AuthProperties {
    private boolean enabled;

    /**
     * 判断是否显式启用认证 starter。
     *
     * @return {@code true} 表示已启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否显式启用认证 starter。
     *
     * @param enabled {@code true} 表示启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
