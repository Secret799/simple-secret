package com.ss.web.cors;

import com.ss.web.config.WebProperties;
import org.springframework.web.cors.CorsConfiguration;

import java.time.Duration;
import java.util.List;

/** 创建经过校验与归一化的 WebMVC CORS 配置。 */
public final class WebCorsConfigurationFactory {

    private WebCorsConfigurationFactory() {
    }

    /** 根据属性创建 CORS 配置。 */
    public static CorsConfiguration create(WebProperties.Cors properties) {
        normalizedPath(properties);
        List<String> allowedOrigins = normalizeList(properties.getAllowedOrigins(), "allowed origins");
        List<String> allowedOriginPatterns = normalizeList(
                properties.getAllowedOriginPatterns(), "allowed origin patterns");
        List<String> allowedMethods = normalizeList(properties.getAllowedMethods(), "allowed methods");
        List<String> allowedHeaders = normalizeList(properties.getAllowedHeaders(), "allowed headers");
        List<String> exposedHeaders = normalizeList(properties.getExposedHeaders(), "exposed headers");
        Duration maxAge = properties.getMaxAge();

        if (allowedOrigins.isEmpty() && allowedOriginPatterns.isEmpty()) {
            throw invalid("allowed origins");
        }
        if (maxAge == null || maxAge.isNegative()) {
            throw invalid("max age");
        }
        if (properties.isAllowCredentials()) {
            rejectWildcard(allowedOrigins, "allowed origins");
            rejectWildcard(allowedOriginPatterns, "allowed origin patterns");
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedOriginPatterns(allowedOriginPatterns);
        configuration.setAllowedMethods(allowedMethods);
        configuration.setAllowedHeaders(allowedHeaders);
        configuration.setExposedHeaders(exposedHeaders);
        configuration.setAllowCredentials(properties.isAllowCredentials());
        configuration.setMaxAge(maxAge);
        return configuration;
    }

    /** 返回校验并裁剪后的 CORS 映射路径。 */
    public static String normalizedPath(WebProperties.Cors properties) {
        return normalizeValue(properties.getPath(), "path");
    }

    private static List<String> normalizeList(List<String> values, String category) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> normalizeValue(value, category))
                .toList();
    }

    private static String normalizeValue(String value, String category) {
        if (value == null || value.isBlank()) {
            throw invalid(category);
        }
        return value.trim();
    }

    private static void rejectWildcard(List<String> values, String category) {
        if (values.stream().anyMatch(value -> value.contains(CorsConfiguration.ALL))) {
            throw invalid(category);
        }
    }

    private static IllegalArgumentException invalid(String category) {
        return new IllegalArgumentException("Invalid CORS " + category + " configuration.");
    }
}
