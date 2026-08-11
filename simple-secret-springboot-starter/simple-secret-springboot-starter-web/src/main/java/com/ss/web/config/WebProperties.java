package com.ss.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/** Simple Secret WebMVC 自动配置属性。 */
@ConfigurationProperties(prefix = "simple-secret.web")
public class WebProperties {

    private boolean enabled;
    private final ExceptionHandler exceptionHandler = new ExceptionHandler();
    private final Cors cors = new Cors();
    private final RequestTiming requestTiming = new RequestTiming();

    /** 返回是否启用 WebMVC 自动配置。 */
    public boolean isEnabled() {
        return enabled;
    }

    /** 设置是否启用 WebMVC 自动配置。 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** 返回异常处理配置。 */
    public ExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    /** 返回 CORS 配置。 */
    public Cors getCors() {
        return cors;
    }

    /** 返回请求耗时记录配置。 */
    public RequestTiming getRequestTiming() {
        return requestTiming;
    }

    /** 全局异常处理配置。 */
    public static class ExceptionHandler {

        private boolean enabled;

        /** 返回是否启用全局异常处理。 */
        public boolean isEnabled() {
            return enabled;
        }

        /** 设置是否启用全局异常处理。 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /** CORS 配置。 */
    public static class Cors {

        private boolean enabled;
        private String path = "/**";
        private List<String> allowedOrigins = List.of();
        private List<String> allowedOriginPatterns = List.of();
        private List<String> allowedMethods = List.of("GET", "HEAD", "POST");
        private List<String> allowedHeaders = List.of();
        private List<String> exposedHeaders = List.of();
        private boolean allowCredentials;
        private Duration maxAge = Duration.ofMinutes(30);

        /** 返回是否启用 CORS。 */
        public boolean isEnabled() {
            return enabled;
        }

        /** 设置是否启用 CORS。 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** 返回 CORS 生效路径。 */
        public String getPath() {
            return path;
        }

        /** 设置 CORS 生效路径。 */
        public void setPath(String path) {
            this.path = path;
        }

        /** 返回允许跨域请求的来源。 */
        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        /** 设置允许跨域请求的来源。 */
        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }

        /** 返回允许跨域请求的来源模式。 */
        public List<String> getAllowedOriginPatterns() {
            return allowedOriginPatterns;
        }

        /** 设置允许跨域请求的来源模式。 */
        public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
            this.allowedOriginPatterns = allowedOriginPatterns;
        }

        /** 返回允许的请求方法。 */
        public List<String> getAllowedMethods() {
            return allowedMethods;
        }

        /** 设置允许的请求方法。 */
        public void setAllowedMethods(List<String> allowedMethods) {
            this.allowedMethods = allowedMethods;
        }

        /** 返回允许的请求头。 */
        public List<String> getAllowedHeaders() {
            return allowedHeaders;
        }

        /** 设置允许的请求头。 */
        public void setAllowedHeaders(List<String> allowedHeaders) {
            this.allowedHeaders = allowedHeaders;
        }

        /** 返回暴露给客户端的响应头。 */
        public List<String> getExposedHeaders() {
            return exposedHeaders;
        }

        /** 设置暴露给客户端的响应头。 */
        public void setExposedHeaders(List<String> exposedHeaders) {
            this.exposedHeaders = exposedHeaders;
        }

        /** 返回是否允许携带凭据。 */
        public boolean isAllowCredentials() {
            return allowCredentials;
        }

        /** 设置是否允许携带凭据。 */
        public void setAllowCredentials(boolean allowCredentials) {
            this.allowCredentials = allowCredentials;
        }

        /** 返回预检响应最大缓存时长。 */
        public Duration getMaxAge() {
            return maxAge;
        }

        /** 设置预检响应最大缓存时长。 */
        public void setMaxAge(Duration maxAge) {
            this.maxAge = maxAge;
        }
    }

    /** 请求耗时记录配置。 */
    public static class RequestTiming {

        private boolean enabled;
        private Duration slowRequestThreshold = Duration.ofSeconds(1);

        /** 返回是否启用请求耗时记录。 */
        public boolean isEnabled() {
            return enabled;
        }

        /** 设置是否启用请求耗时记录。 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** 返回慢请求阈值。 */
        public Duration getSlowRequestThreshold() {
            return slowRequestThreshold;
        }

        /** 设置慢请求阈值。 */
        public void setSlowRequestThreshold(Duration slowRequestThreshold) {
            this.slowRequestThreshold = slowRequestThreshold;
        }
    }
}
