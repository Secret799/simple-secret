package com.ss.dji.camera.config;

import com.ss.dji.camera.diagnostic.DjiSeiEventListener;
import com.ss.dji.camera.web.DjiSeiSseController;
import com.ss.dji.camera.web.DjiSeiSseService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** DJI SEI SSE 自动配置测试。 */
class DjiSeiSseAutoConfigurationTest {

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DjiCameraAutoConfiguration.class,
                    DjiSeiSseAutoConfiguration.class));

    @Test
    void shouldRegisterSseEndpointInServletApplicationWhenEnabled() {
        webContextRunner.withPropertyValues("simple-secret.dji-sei.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(DjiSeiSseService.class);
                    assertThat(context).hasSingleBean(DjiSeiSseController.class);
                    assertThat(context.getBeansOfType(DjiSeiEventListener.class))
                            .containsValue(context.getBean(DjiSeiSseService.class));
                });
    }

    @Test
    void shouldNotRegisterSseEndpointWhenDisabled() {
        webContextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(DjiSeiSseService.class);
            assertThat(context).doesNotHaveBean(DjiSeiSseController.class);
        });
    }

    @Test
    void shouldNotRegisterSseEndpointOutsideServletApplication() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DjiCameraAutoConfiguration.class,
                        DjiSeiSseAutoConfiguration.class))
                .withPropertyValues("simple-secret.dji-sei.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DjiSeiSseService.class);
                    assertThat(context).doesNotHaveBean(DjiSeiSseController.class);
                });
    }
}
