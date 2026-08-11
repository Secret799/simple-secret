package com.ss.magicapi.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.ssssssss.magicapi.core.config.MagicConfiguration;
import org.ssssssss.magicapi.core.context.RequestEntity;
import org.ssssssss.magicapi.core.interceptor.ResultProvider;
import org.ssssssss.magicapi.core.service.MagicAPIService;
import org.ssssssss.magicapi.modules.db.model.Page;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 starter 与 Spring Boot 3.5、Magic API 2.2.2 的实际上下文集成。 */
class SimpleSecretMagicApiAutoConfigurationTest {
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(TestApplication.class);

    @TempDir
    Path workspace;

    @Test
    void shouldCreateNoMagicApiRuntimeBeansWhenDisabledByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MagicConfiguration.class);
            assertThat(context).doesNotHaveBean(MagicAPIService.class);
            assertThat(context).doesNotHaveBean(org.ssssssss.magicapi.core.resource.Resource.class);
            assertThat(context).doesNotHaveBean(MagicApiStarterProperties.class);
        });
    }

    @Test
    void shouldFailBeforeMagicApiInitializationWhenFileLocationIsMissing() {
        runner.withPropertyValues("simple-secret.magic-api.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("magic-api.resource.location");
                });
    }

    @Test
    void shouldStartMagicApiWithExplicitReadonlyFileWorkspace() {
        runner.withPropertyValues(enabledFileProperties())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MagicApiStarterProperties.class);
                    assertThat(context).hasSingleBean(MagicConfiguration.class);
                    assertThat(context).hasSingleBean(MagicAPIService.class);
                    assertThat(context).hasSingleBean(org.ssssssss.magicapi.core.resource.Resource.class);
                });
    }

    @Test
    void shouldBackOffToConsumerResultProvider() {
        ResultProvider custom = new TestResultProvider();

        runner.withBean(ResultProvider.class, () -> custom)
                .withPropertyValues(enabledFileProperties())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ResultProvider.class);
                    assertThat(context.getBean(ResultProvider.class)).isSameAs(custom);
                });
    }

    private String[] enabledFileProperties() {
        return new String[]{
                "simple-secret.magic-api.enabled=true",
                "magic-api.resource.type=file",
                "magic-api.resource.location=" + workspace,
                "magic-api.resource.readonly=true",
                "magic-api.banner=false",
                "magic-api.support-cross-domain=false",
                "magic-api.show-sql=false",
                "magic-api.show-url=false"
        };
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApplication {
    }

    private static final class TestResultProvider implements ResultProvider {
        @Override
        public Object buildResult(RequestEntity requestEntity, int code, String message, Object data) {
            return Map.of("code", code, "message", message, "data", data == null ? "" : data);
        }

        @Override
        public Object buildPageResult(RequestEntity requestEntity, Page page, long total,
                                      List<Map<String, Object>> data) {
            return Map.of("total", total, "data", data);
        }
    }
}
