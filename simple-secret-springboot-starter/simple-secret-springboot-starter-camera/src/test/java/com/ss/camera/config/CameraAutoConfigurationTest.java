package com.ss.camera.config;

import com.ss.camera.domain.StreamUrlAssemblyDomain;
import com.ss.camera.service.UrlAssemblyHolder;
import com.ss.camera.service.UrlAssemblyService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CameraAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CameraAutoConfiguration.class));

    @Test
    void registersFourAssemblersAndHolder() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(UrlAssemblyHolder.class);
            assertThat(context).getBeans(UrlAssemblyService.class).hasSize(4);

            StreamUrlAssemblyDomain request = new StreamUrlAssemblyDomain()
                    .setIp("camera.example.com")
                    .setPort("554")
                    .setAccount("admin")
                    .setPassword("secret")
                    .setChannelNo("2")
                    .setStreamType("sub")
                    .setBrand("Dahua")
                    .setType("CAMERA");
            assertThat(context.getBean(UrlAssemblyHolder.class).assembly(request))
                    .isEqualTo("rtsp://admin:secret@camera.example.com:554/cam/realmonitor?channel=2&subtype=1");
        });
    }

    @Test
    void collectsApplicationProvidedAssembler() {
        UrlAssemblyService custom = new UrlAssemblyService() {
            @Override
            public String brand() {
                return "Acme";
            }

            @Override
            public String type() {
                return "CAMERA";
            }

            @Override
            public String assembly(StreamUrlAssemblyDomain domain) {
                return "rtsp://acme/" + domain.getChannelNo();
            }
        };

        runner.withBean("acmeUrlAssemblyService", UrlAssemblyService.class, () -> custom)
                .run(context -> assertThat(context.getBean(UrlAssemblyHolder.class)
                        .get("acme", "camera")).isSameAs(custom));
    }

    @Test
    void backsOffForApplicationProvidedHolder() {
        UrlAssemblyHolder custom = new UrlAssemblyHolder(java.util.List.of());

        runner.withBean(UrlAssemblyHolder.class, () -> custom)
                .run(context -> {
                    assertThat(context).hasSingleBean(UrlAssemblyHolder.class);
                    assertThat(context.getBean(UrlAssemblyHolder.class)).isSameAs(custom);
                });
    }

    @Test
    void canBeDisabled() {
        runner.withPropertyValues("simple-secret.camera.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(UrlAssemblyHolder.class);
                    assertThat(context).doesNotHaveBean(UrlAssemblyService.class);
                });
    }
}
