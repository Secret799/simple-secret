package com.ss.magicapi.config;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 校验 Magic API 会产生外部资源或管理入口的关键配置。
 */
final class MagicApiConfigurationValidator {
    private MagicApiConfigurationValidator() {
    }

    static void validate(Environment environment) {
        String resourceType = environment.getProperty("magic-api.resource.type", "file")
                .trim()
                .toLowerCase(Locale.ROOT);
        if (!"file".equals(resourceType) && !"database".equals(resourceType)) {
            throw new IllegalStateException(
                    "magic-api.resource.type must be either 'file' or 'database'");
        }
        if ("file".equals(resourceType)
                && !StringUtils.hasText(environment.getProperty("magic-api.resource.location"))) {
            throw new IllegalStateException(
                    "magic-api.resource.location must be configured explicitly for file resources");
        }

        boolean usernameConfigured = StringUtils.hasText(
                environment.getProperty("magic-api.security.username"));
        boolean passwordConfigured = StringUtils.hasText(
                environment.getProperty("magic-api.security.password"));
        if (usernameConfigured != passwordConfigured) {
            throw new IllegalStateException(
                    "magic-api.security.username and magic-api.security.password must be configured together");
        }
        if (StringUtils.hasText(environment.getProperty("magic-api.web"))
                && !usernameConfigured) {
            throw new IllegalStateException(
                    "magic-api.web requires both magic-api.security.username and magic-api.security.password");
        }
    }
}
