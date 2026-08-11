package com.ss.auth.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ss.auth.web.SimpleSecretAuthExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 Auth Web 自动配置的开关、回退和发布资源。 */
class AuthPublishedResourcesTest {

    private static final String AUTO_CONFIGURATION_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
    private static final String CONFIGURATION_METADATA =
            "META-INF/spring-configuration-metadata.json";

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SimpleSecretAuthAutoConfiguration.class,
                    SimpleSecretAuthWebAutoConfiguration.class));

    private final ApplicationContextRunner nonServletRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SimpleSecretAuthAutoConfiguration.class,
                    SimpleSecretAuthWebAutoConfiguration.class));

    @Test
    void shouldNotRegisterAdviceWhenMasterSwitchIsDisabled() {
        webRunner.withPropertyValues("simple-secret.auth.exception-handler.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(SimpleSecretAuthExceptionHandler.class));
    }

    @Test
    void shouldNotRegisterAdviceWhenOnlyMasterSwitchIsEnabled() {
        webRunner.withPropertyValues("simple-secret.auth.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(SimpleSecretAuthExceptionHandler.class));
    }

    @Test
    void shouldRegisterAdviceWhenBothSwitchesAreEnabled() {
        webRunner.withPropertyValues(
                        "simple-secret.auth.enabled=true",
                        "simple-secret.auth.exception-handler.enabled=true")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(SimpleSecretAuthExceptionHandler.class)
                        .hasBean("simpleSecretAuthExceptionHandler"));
    }

    @Test
    void shouldNotRegisterAdviceOutsideServletApplication() {
        nonServletRunner.withPropertyValues(
                        "simple-secret.auth.enabled=true",
                        "simple-secret.auth.exception-handler.enabled=true")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(SimpleSecretAuthExceptionHandler.class));
    }

    @Test
    void shouldBackOffWhenConsumerProvidesAdvice() {
        SimpleSecretAuthExceptionHandler consumerHandler =
                SimpleSecretAuthExceptionHandler.create();

        webRunner.withBean("consumerAuthExceptionHandler",
                        SimpleSecretAuthExceptionHandler.class, () -> consumerHandler)
                .withPropertyValues(
                        "simple-secret.auth.enabled=true",
                        "simple-secret.auth.exception-handler.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("simpleSecretAuthExceptionHandler");
                    assertThat(context).hasSingleBean(SimpleSecretAuthExceptionHandler.class);
                    assertThat(context.getBean(SimpleSecretAuthExceptionHandler.class))
                            .isSameAs(consumerHandler);
                });
    }

    @Test
    void shouldBackOffWhenDispatcherServletIsUnavailable() {
        webRunner.withClassLoader(new FilteredClassLoader(
                        "org.springframework.web.servlet.DispatcherServlet"))
                .withPropertyValues(
                        "simple-secret.auth.enabled=true",
                        "simple-secret.auth.exception-handler.enabled=true")
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .doesNotHaveBean(SimpleSecretAuthExceptionHandler.class));
    }

    @Test
    void shouldPublishExactAutoConfigurationImportsAndDisabledMetadataDefaults() throws Exception {
        ClassLoader classLoader = getClass().getClassLoader();
        try (InputStream imports = classLoader.getResourceAsStream(AUTO_CONFIGURATION_IMPORTS);
             InputStream metadata = classLoader.getResourceAsStream(CONFIGURATION_METADATA)) {
            assertThat(imports).isNotNull();
            assertThat(read(imports).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList())
                    .containsExactly(
                            SimpleSecretAuthAutoConfiguration.class.getName(),
                            SimpleSecretAuthWebAutoConfiguration.class.getName());

            assertThat(metadata).isNotNull();
            JsonNode root = new ObjectMapper().readTree(read(metadata));
            assertDisabledDefault(root, "simple-secret.auth.enabled");
            assertDisabledDefault(root, "simple-secret.auth.exception-handler.enabled");
        }
    }

    private static void assertDisabledDefault(JsonNode metadata, String name) {
        JsonNode property = StreamSupport.stream(metadata.path("properties").spliterator(), false)
                .filter(candidate -> name.equals(candidate.path("name").asText()))
                .findFirst()
                .orElseThrow();
        assertThat(property.path("defaultValue").asBoolean()).isFalse();
    }

    private static String read(InputStream input) throws Exception {
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
}
