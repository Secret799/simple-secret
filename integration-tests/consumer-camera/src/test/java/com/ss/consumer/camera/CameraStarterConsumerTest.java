package com.ss.consumer.camera;

import com.ss.camera.domain.StreamUrlAssemblyDomain;
import com.ss.camera.service.UrlAssemblyHolder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证第三方应用只声明 Camera starter 时可以发现并使用自动配置。 */
class CameraStarterConsumerTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @Test
    void assemblesUrlThroughPublishedAutoConfiguration() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(UrlAssemblyHolder.class);

            StreamUrlAssemblyDomain request = new StreamUrlAssemblyDomain()
                    .setBrand("Hikvision")
                    .setType("NVR")
                    .setIp("192.0.2.20")
                    .setPort("554")
                    .setAccount("consumer")
                    .setPassword("secret")
                    .setChannelNo("3")
                    .setStreamType("sub");
            assertThat(context.getBean(UrlAssemblyHolder.class).assembly(request))
                    .isEqualTo("rtsp://consumer:secret@192.0.2.20:554/Streaming/Channels/302?transportmode=multicast");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {
    }
}
