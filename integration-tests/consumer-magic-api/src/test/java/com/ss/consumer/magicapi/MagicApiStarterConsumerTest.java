package com.ss.consumer.magicapi;

import com.ss.magicapi.config.SimpleSecretMagicApiAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;
import org.ssssssss.magicapi.core.config.MagicConfiguration;
import org.ssssssss.magicapi.core.service.MagicAPIService;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证第三方应用仅依赖已发布 Magic API starter 时的默认行为。 */
class MagicApiStarterConsumerTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @Test
    void discoversStarterWithoutEnablingMagicApiOrJavaEeServlet() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(MagicConfiguration.class);
            assertThat(context).doesNotHaveBean(MagicAPIService.class);

            ConditionEvaluationReport report = ConditionEvaluationReport.get(context.getBeanFactory());
            assertThat(report.getConditionAndOutcomesBySource())
                    .containsKey(SimpleSecretMagicApiAutoConfiguration.class.getName());
            assertThat(ClassUtils.isPresent(
                    "javax.servlet.http.HttpServletRequest", getClass().getClassLoader())).isFalse();

            try (InputStream imports = getClass().getClassLoader().getResourceAsStream(
                    "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
                assertThat(imports).isNotNull();
            }
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {
    }
}
