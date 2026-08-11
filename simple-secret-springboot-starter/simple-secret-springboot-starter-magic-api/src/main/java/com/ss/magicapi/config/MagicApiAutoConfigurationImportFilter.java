package com.ss.magicapi.config;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/**
 * 控制 Magic API 上游自动配置是否进入 Spring Boot 应用。
 *
 * <p>上游 starter 没有总开关，因此必须在自动配置导入阶段拦截，避免默认创建
 * 资源目录、动态路由和 WebSocket 组件。</p>
 */
public final class MagicApiAutoConfigurationImportFilter
        implements AutoConfigurationImportFilter, EnvironmentAware {
    static final String ENABLED_PROPERTY = "simple-secret.magic-api.enabled";
    static final String MAGIC_API_AUTO_CONFIGURATION =
            "org.ssssssss.magicapi.spring.boot.starter.MagicAPIAutoConfiguration";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean[] match(String[] autoConfigurationClasses,
                           AutoConfigurationMetadata autoConfigurationMetadata) {
        boolean enabled = environment != null
                && "true".equalsIgnoreCase(environment.getProperty(ENABLED_PROPERTY, ""));
        boolean[] matches = new boolean[autoConfigurationClasses.length];
        for (int index = 0; index < autoConfigurationClasses.length; index++) {
            String autoConfiguration = autoConfigurationClasses[index];
            matches[index] = !MAGIC_API_AUTO_CONFIGURATION.equals(autoConfiguration) || enabled;
        }
        return matches;
    }
}
