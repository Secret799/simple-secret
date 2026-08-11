package com.ss.web.cors;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Map;

/** 将 starter 创建的 CORS 配置应用到纯 WebMVC 映射。 */
public final class WebCorsWebMvcConfigurer implements WebMvcConfigurer {

    private final String path;
    private final CorsConfiguration configuration;

    /** 从 starter CORS source 读取同一份已校验配置。 */
    public WebCorsWebMvcConfigurer(UrlBasedCorsConfigurationSource source) {
        Map.Entry<String, CorsConfiguration> entry = source.getCorsConfigurations()
                .entrySet()
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing CORS configuration."));
        this.path = entry.getKey();
        this.configuration = entry.getValue();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        CorsRegistration registration = registry.addMapping(path)
                .allowedOrigins(configuration.getAllowedOrigins().toArray(String[]::new))
                .allowedOriginPatterns(configuration.getAllowedOriginPatterns().toArray(String[]::new))
                .allowedMethods(configuration.getAllowedMethods().toArray(String[]::new))
                .allowedHeaders(configuration.getAllowedHeaders().toArray(String[]::new))
                .exposedHeaders(configuration.getExposedHeaders().toArray(String[]::new))
                .allowCredentials(Boolean.TRUE.equals(configuration.getAllowCredentials()));
        if (configuration.getMaxAge() != null) {
            registration.maxAge(configuration.getMaxAge());
        }
    }
}
