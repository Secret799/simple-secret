package com.ss.ics.hikvision.config;

import com.ss.ics.hikvision.HikvisionCameraSdkService;
import com.ss.ics.hikvision.HikvisionSdkOptions;
import com.ss.ics.hikvision.TestHikvisionServices;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HikvisionCameraSdkAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    HikvisionCameraSdkAutoConfiguration.class));

    @Test
    void staysDisabledByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(HikvisionCameraSdkProperties.class);
            assertThat(context).doesNotHaveBean(HikvisionCameraSdkServiceFactory.class);
            assertThat(context).doesNotHaveBean(HikvisionCameraSdkService.class);
        });
    }

    @Test
    void createsConfiguredServiceAndClosesItWithTheContext() {
        AtomicReference<HikvisionSdkOptions> captured = new AtomicReference<>();
        AtomicReference<HikvisionCameraSdkService> service = new AtomicReference<>();
        AtomicInteger cleanupCalls = new AtomicInteger();

        runner.withBean(HikvisionCameraSdkServiceFactory.class,
                        () -> options -> {
                            captured.set(options);
                            HikvisionCameraSdkService created =
                                    TestHikvisionServices.create(options, cleanupCalls);
                            service.set(created);
                            return created;
                        })
                .withPropertyValues(
                        "simple-secret.camera-sdk.hikvision.enabled=true",
                        "simple-secret.camera-sdk.hikvision.library-directory=vendor/hikvision",
                        "simple-secret.camera-sdk.hikvision.file-search-timeout=8s",
                        "simple-secret.camera-sdk.hikvision.async-ptz-queue-capacity=64")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(HikvisionCameraSdkService.class);
                    assertThat(context.getBean(HikvisionCameraSdkService.class))
                            .isSameAs(service.get());
                    assertThat(captured.get().libraryDirectory())
                            .isEqualTo(Path.of("vendor/hikvision").toAbsolutePath().normalize());
                    assertThat(captured.get().fileSearchTimeout()).isEqualTo(Duration.ofSeconds(8));
                    assertThat(captured.get().asyncPtzQueueCapacity()).isEqualTo(64);
                });

        assertThat(cleanupCalls).hasValue(1);
    }

    @Test
    void failsFastWhenEnabledWithoutLibraryDirectory() {
        runner.withBean(HikvisionCameraSdkServiceFactory.class,
                        () -> options -> null)
                .withPropertyValues("simple-secret.camera-sdk.hikvision.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(
                            "simple-secret.camera-sdk.hikvision.library-directory must be configured");
                });
    }

    @Test
    void backsOffForApplicationProvidedService() {
        AtomicInteger cleanupCalls = new AtomicInteger();
        HikvisionCameraSdkService service = TestHikvisionServices.create(
                HikvisionSdkOptions.defaults(Path.of("vendor/custom-hikvision")),
                cleanupCalls);

        try {
            runner.withBean(HikvisionCameraSdkService.class, () -> service,
                            definition -> definition.setDestroyMethodName(""))
                    .withPropertyValues("simple-secret.camera-sdk.hikvision.enabled=true")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(HikvisionCameraSdkService.class);
                        assertThat(context.getBean(HikvisionCameraSdkService.class))
                                .isSameAs(service);
                    });
        } finally {
            service.close();
        }
        assertThat(cleanupCalls).hasValue(1);
    }
}
