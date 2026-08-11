package com.ss.web.config;

import com.ss.web.cors.WebCorsConfigurationFactory;
import com.ss.web.cors.WebCorsWebMvcConfigurer;
import com.ss.web.error.SimpleSecretExceptionHandler;
import com.ss.web.error.SimpleSecretValidationExceptionHandler;
import com.ss.web.observability.RequestTimingInterceptor;
import com.ss.web.observability.RequestTimingWebMvcConfigurer;
import org.springframework.context.MessageSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

/** Simple Secret WebMVC 自动配置入口。 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnProperty(prefix = "simple-secret.web", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(WebProperties.class)
@Import({
        SimpleSecretWebAutoConfiguration.RequestTimingAutoConfiguration.class,
        SimpleSecretWebAutoConfiguration.CorsAutoConfiguration.class,
        SimpleSecretWebAutoConfiguration.ValidationExceptionHandlerConfiguration.class
})
public class SimpleSecretWebAutoConfiguration {

    /** 注册可由消费者拦截器替换的请求耗时记录。 */
    @ConditionalOnProperty(
            prefix = "simple-secret.web.request-timing",
            name = "enabled",
            havingValue = "true")
    @ConditionalOnMissingBean(RequestTimingWebMvcConfigurer.class)
    static class RequestTimingAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean(RequestTimingInterceptor.class)
        RequestTimingInterceptor simpleSecretRequestTimingInterceptor(WebProperties properties) {
            return new RequestTimingInterceptor(
                    properties.getRequestTiming().getSlowRequestThreshold());
        }

        @Bean
        RequestTimingWebMvcConfigurer requestTimingWebMvcConfigurer(
                RequestTimingInterceptor interceptor) {
            return new RequestTimingWebMvcConfigurer(interceptor);
        }
    }

    /** 将 starter CORS source 与 WebMVC 映射作为一个可整体回退的配置单元注册。 */
    @ConditionalOnProperty(
            prefix = "simple-secret.web.cors",
            name = "enabled",
            havingValue = "true")
    @ConditionalOnMissingBean(
            value = CorsConfigurationSource.class,
            ignored = HandlerMappingIntrospector.class)
    static class CorsAutoConfiguration {

        /** 注册可由 Spring Security 复用的 CORS source。 */
        @Bean
        UrlBasedCorsConfigurationSource simpleSecretCorsConfigurationSource(
                WebProperties properties) {
            CorsConfiguration configuration = WebCorsConfigurationFactory.create(properties.getCors());
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration(
                    WebCorsConfigurationFactory.normalizedPath(properties.getCors()), configuration);
            return source;
        }

        /** 注册纯 WebMVC 使用的 CORS 映射配置。 */
        @Bean
        WebCorsWebMvcConfigurer webCorsWebMvcConfigurer(
                UrlBasedCorsConfigurationSource simpleSecretCorsConfigurationSource) {
            return new WebCorsWebMvcConfigurer(simpleSecretCorsConfigurationSource);
        }
    }

    /** 注册可被消费者覆盖的默认异常处理器。 */
    @Bean
    @ConditionalOnProperty(
            prefix = "simple-secret.web.exception-handler",
            name = "enabled",
            havingValue = "true")
    @ConditionalOnMissingBean(SimpleSecretExceptionHandler.class)
    public SimpleSecretExceptionHandler simpleSecretExceptionHandler(MessageSource messageSource) {
        return SimpleSecretExceptionHandler.create(messageSource);
    }

    /** Jakarta Validation 存在时注册约束校验异常处理。 */
    @ConditionalOnClass(name = "jakarta.validation.ConstraintViolationException")
    static class ValidationExceptionHandlerConfiguration {

        /** 注册可被消费者覆盖的约束校验异常处理器。 */
        @Bean
        @ConditionalOnProperty(
                prefix = "simple-secret.web.exception-handler",
                name = "enabled",
                havingValue = "true")
        @ConditionalOnMissingBean(SimpleSecretValidationExceptionHandler.class)
        SimpleSecretValidationExceptionHandler simpleSecretValidationExceptionHandler() {
            return SimpleSecretValidationExceptionHandler.create();
        }
    }
}
