package com.ss.consumer.doc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证第三方 WebMVC 应用通过 BOM 使用 doc starter 的行为。 */
class DocStarterConsumerTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @Test
    void shouldKeepApiDocsUnavailableByDefaultThroughHttp() throws Exception {
        assertThat(Files.readString(Path.of("../pom.xml")))
                .contains("<module>consumer-doc</module>");
        assertThat(ObjectMapper.class.getPackage().getImplementationVersion())
                .isEqualTo("2.21.5");

        try (ConfigurableApplicationContext context = startWebApplication()) {
            assertThat(getApiDocs(context).statusCode()).isEqualTo(404);
        }
    }

    @Test
    void shouldExposeApiDocsWithProbePathWhenEnabledThroughHttp() throws Exception {
        try (ConfigurableApplicationContext context = startWebApplication(
                "--simple-secret.doc.enabled=true")) {
            HttpResponse<String> response = getApiDocs(context);

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(new ObjectMapper().readTree(response.body())
                    .path("paths").has("/consumer-doc-probe")).isTrue();
        }
    }

    @Test
    void shouldKeepApiDocsUnavailableWhenSpringdocIsExplicitlyDisabledThroughHttp()
            throws Exception {
        try (ConfigurableApplicationContext context = startWebApplication(
                "--simple-secret.doc.enabled=true",
                "--springdoc.api-docs.enabled=false")) {
            assertThat(getApiDocs(context).statusCode()).isEqualTo(404);
        }
    }

    @Test
    void shouldCreateConfiguredOpenApiWhenEnabled() {
        runner.withPropertyValues(
                        "simple-secret.doc.enabled=true",
                        "simple-secret.doc.info.title=Consumer API",
                        "simple-secret.doc.security.schemes.bearerAuth.type=http-bearer",
                        "simple-secret.doc.security.schemes.bearerAuth.bearer-format=JWT",
                        "simple-secret.doc.security.globally-required[0]=bearerAuth")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    OpenAPI openApi = context.getBean(OpenAPI.class);
                    assertThat(openApi.getInfo().getTitle()).isEqualTo("Consumer API");
                    assertThat(openApi.getComponents().getSecuritySchemes())
                            .containsKey("bearerAuth");
                    assertThat(openApi.getSecurity()).singleElement().satisfies(requirement ->
                            assertThat(requirement).containsOnlyKeys("bearerAuth"));
                });
    }

    @Test
    void shouldPreferConsumerOpenApiBean() {
        runner.withUserConfiguration(ConsumerOpenApiConfiguration.class)
                .withPropertyValues("simple-secret.doc.enabled=true")
                .run(context -> assertThat(context.getBean(OpenAPI.class))
                        .isSameAs(ConsumerOpenApiConfiguration.OPEN_API));
    }

    private static ConfigurableApplicationContext startWebApplication(String... properties) {
        SpringApplication application = new SpringApplication(ConsumerApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.setDefaultProperties(Map.of(
                "server.port", "0",
                "spring.main.banner-mode", "off",
                "logging.level.root", "OFF"));
        return application.run(properties);
    }

    private static HttpResponse<String> getApiDocs(ConfigurableApplicationContext context)
            throws IOException, InterruptedException {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + "/v3/api-docs"))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
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

        @GetMapping("/consumer-doc-probe")
        String probe() {
            return "ok";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerOpenApiConfiguration {
        private static final OpenAPI OPEN_API = new OpenAPI();

        @Bean
        OpenAPI consumerOpenApi() {
            return OPEN_API;
        }
    }
}
