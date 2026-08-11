package com.ss.consumer.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ss.core.exception.ServiceException;
import com.ss.web.cors.WebCorsWebMvcConfigurer;
import com.ss.web.error.SimpleSecretExceptionHandler;
import com.ss.web.error.SimpleSecretValidationExceptionHandler;
import com.ss.web.observability.RequestTimingInterceptor;
import com.ss.web.observability.RequestTimingWebMvcConfigurer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证第三方 WebMVC 应用通过 BOM 接入 Web starter 的发布契约。 */
@ExtendWith(OutputCaptureExtension.class)
class WebStarterConsumerTest {

    private static final String STARTER_EXCEPTION_SECRET = "starter-exception-secret";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldKeepStarterFeaturesDisabledByDefaultThroughHttp() throws Exception {
        String consumerPom = Files.readString(Path.of("pom.xml"));
        int starterDependency = consumerPom.indexOf(
                "<artifactId>simple-secret-springboot-starter-web</artifactId>");
        assertThat(consumerPom)
                .contains("simple-secret-springboot-starter-web")
                .contains("spring-boot-starter-web");
        assertThat(consumerPom.substring(starterDependency,
                consumerPom.indexOf("</dependency>", starterDependency)))
                .doesNotContain("<version>");
        assertThat(Files.readString(Path.of("../pom.xml")))
                .contains("simple-secret-common-bom");

        try (ConfigurableApplicationContext context = startWebApplication(ConsumerApplication.class)) {
            assertThat(context.getBeansOfType(SimpleSecretExceptionHandler.class)).isEmpty();
            assertThat(context.getBeansOfType(SimpleSecretValidationExceptionHandler.class)).isEmpty();
            assertThat(context.getBeansOfType(RequestTimingInterceptor.class)).isEmpty();
            assertThat(context.getBeansOfType(RequestTimingWebMvcConfigurer.class)).isEmpty();
            assertThat(context.containsBean("simpleSecretCorsConfigurationSource")).isFalse();
            assertThat(context.getBeansOfType(WebCorsWebMvcConfigurer.class)).isEmpty();

            assertThat(get(context, "/web/probe").statusCode()).isEqualTo(200);
            HttpResponse<String> preflight = options(context, "https://app.example.com");
            assertThat(preflight.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        }
    }

    @Test
    void shouldApplyExceptionCorsAndTimingFeaturesWhenExplicitlyEnabledThroughHttp(
            CapturedOutput output)
            throws Exception {
        try (ConfigurableApplicationContext context = startWebApplication(ConsumerApplication.class,
                "--simple-secret.web.enabled=true",
                "--simple-secret.web.exception-handler.enabled=true",
                "--simple-secret.web.cors.enabled=true",
                "--simple-secret.web.cors.allowed-origins[0]=https://app.example.com",
                "--simple-secret.web.cors.allowed-methods[0]=GET",
                "--simple-secret.web.cors.allowed-headers[0]=Authorization",
                "--simple-secret.web.cors.allow-credentials=true",
                "--simple-secret.web.cors.max-age=10m",
                "--simple-secret.web.request-timing.enabled=true",
                "--simple-secret.web.request-timing.slow-request-threshold=1h",
                "--logging.level.com.ss.web.observability=DEBUG")) {
            assertThat(context.getBeansOfType(SimpleSecretExceptionHandler.class)).hasSize(1);
            assertThat(context.getBeansOfType(SimpleSecretValidationExceptionHandler.class)).hasSize(1);
            assertThat(context.containsBean("simpleSecretCorsConfigurationSource")).isTrue();
            assertThat(context.getBean("simpleSecretCorsConfigurationSource",
                    CorsConfigurationSource.class)).isNotNull();
            assertThat(context.getBeansOfType(RequestTimingInterceptor.class)).hasSize(1);
            assertThat(context.getBeansOfType(RequestTimingWebMvcConfigurer.class)).hasSize(1);

            HttpResponse<String> failedRequest = get(context, "/web/fail");
            JsonNode error = OBJECT_MAPPER.readTree(failedRequest.body());
            assertThat(failedRequest.statusCode()).isEqualTo(500);
            assertThat(error.path("code").asInt()).isEqualTo(500);
            assertThat(error.path("message").asText()).isEqualTo("服务执行失败");
            assertThat(failedRequest.body()).doesNotContain(STARTER_EXCEPTION_SECRET);
            assertThat(output).contains("route=/web/fail")
                    .doesNotContain(STARTER_EXCEPTION_SECRET);

            HttpResponse<String> preflight = options(context, "https://app.example.com");
            assertThat(preflight.statusCode()).isEqualTo(200);
            assertThat(preflight.headers().firstValue("Access-Control-Allow-Origin"))
                    .contains("https://app.example.com");
            assertThat(preflight.headers().firstValue("Access-Control-Allow-Credentials"))
                    .contains("true");
            assertThat(preflight.headers().firstValue("Access-Control-Max-Age")).contains("600");
        }
    }

    @Test
    void shouldPreferConsumerAdviceCorsSourceAndTimingBeans() throws Exception {
        try (ConfigurableApplicationContext context = startWebApplication(ConsumerApplication.class,
                ConsumerOverrides.class,
                "--simple-secret.web.enabled=true",
                "--simple-secret.web.exception-handler.enabled=true",
                "--simple-secret.web.cors.enabled=true",
                "--simple-secret.web.cors.allowed-origins[0]=https://app.example.com",
                "--simple-secret.web.request-timing.enabled=true")) {
            assertThat(context.getBean(SimpleSecretExceptionHandler.class))
                    .isSameAs(ConsumerOverrides.EXCEPTION_HANDLER);
            assertThat(context.getBean(SimpleSecretValidationExceptionHandler.class))
                    .isSameAs(ConsumerOverrides.VALIDATION_EXCEPTION_HANDLER);
            assertThat(context.getBean("consumerCorsConfigurationSource", CorsConfigurationSource.class))
                    .isSameAs(ConsumerOverrides.CORS_CONFIGURATION_SOURCE);
            assertThat(context.getBean(RequestTimingInterceptor.class))
                    .isSameAs(ConsumerOverrides.TIMING_INTERCEPTOR);
            assertThat(context.containsBean("simpleSecretCorsConfigurationSource")).isFalse();
            assertThat(context.getBeansOfType(WebCorsWebMvcConfigurer.class)).isEmpty();
            assertThat(context.getBeansOfType(RequestTimingWebMvcConfigurer.class)).hasSize(1);
            assertThat(get(context, "/web/probe").statusCode()).isEqualTo(200);
        }
    }

    private static ConfigurableApplicationContext startWebApplication(
            Class<?> application, String... properties) {
        return startWebApplication(new Class<?>[]{application}, properties);
    }

    private static ConfigurableApplicationContext startWebApplication(
            Class<?> application, Class<?> configuration, String... properties) {
        return startWebApplication(new Class<?>[]{application, configuration}, properties);
    }

    private static ConfigurableApplicationContext startWebApplication(
            Class<?>[] sources, String... properties) {
        SpringApplication application = new SpringApplication(sources);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.setDefaultProperties(Map.of(
                "server.port", "0",
                "spring.main.banner-mode", "off",
                "logging.level.root", "OFF"));
        return application.run(properties);
    }

    private static HttpResponse<String> get(ConfigurableApplicationContext context, String path)
            throws IOException, InterruptedException {
        return request(context, path, "GET", null);
    }

    private static HttpResponse<String> options(
            ConfigurableApplicationContext context, String origin)
            throws IOException, InterruptedException {
        return request(context, "/web/probe", "OPTIONS", origin);
    }

    private static HttpResponse<String> request(
            ConfigurableApplicationContext context, String path, String method, String origin)
            throws IOException, InterruptedException {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (origin != null) {
            request.header("Origin", origin)
                    .header("Access-Control-Request-Method", "GET")
                    .header("Access-Control-Request-Headers", "Authorization");
        }
        return HttpClient.newHttpClient().send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }

    @RestController
    static class ProbeController {

        @GetMapping("/web/probe")
        String probe() {
            return "ok";
        }

        @GetMapping("/web/fail")
        String fail() {
            throw new ServiceException(
                    STARTER_EXCEPTION_SECRET, new IllegalStateException(STARTER_EXCEPTION_SECRET));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerOverrides {

        private static final MessageSource MESSAGE_SOURCE = new StaticMessageSource();
        private static final SimpleSecretExceptionHandler EXCEPTION_HANDLER =
                SimpleSecretExceptionHandler.create(MESSAGE_SOURCE);
        private static final SimpleSecretValidationExceptionHandler VALIDATION_EXCEPTION_HANDLER =
                SimpleSecretValidationExceptionHandler.create();
        private static final RequestTimingInterceptor TIMING_INTERCEPTOR =
                new RequestTimingInterceptor(Duration.ofSeconds(1));
        private static final CorsConfigurationSource CORS_CONFIGURATION_SOURCE = corsSource();

        @Bean
        SimpleSecretExceptionHandler consumerExceptionHandler() {
            return EXCEPTION_HANDLER;
        }

        @Bean
        SimpleSecretValidationExceptionHandler consumerValidationExceptionHandler() {
            return VALIDATION_EXCEPTION_HANDLER;
        }

        @Bean
        CorsConfigurationSource consumerCorsConfigurationSource() {
            return CORS_CONFIGURATION_SOURCE;
        }

        @Bean
        RequestTimingInterceptor consumerRequestTimingInterceptor() {
            return TIMING_INTERCEPTOR;
        }

        private static CorsConfigurationSource corsSource() {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedOrigins(List.of("https://consumer.example.com"));
            configuration.setAllowedMethods(List.of("GET"));
            configuration.setAllowedHeaders(List.of("Authorization"));
            configuration.setAllowCredentials(true);
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", configuration);
            return source;
        }
    }
}
