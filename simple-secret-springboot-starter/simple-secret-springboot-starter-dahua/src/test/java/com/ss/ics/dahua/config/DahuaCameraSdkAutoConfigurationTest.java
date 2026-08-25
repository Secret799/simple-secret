package com.ss.ics.dahua.config;

import com.ss.ics.dahua.DahuaCameraSdkService;
import com.ss.ics.dahua.DahuaSdkOptions;
import com.ss.ics.dahua.TestDahuaServices;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DahuaCameraSdkAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    DahuaCameraSdkAutoConfiguration.class));

    @Test
    void staysDisabledByDefault() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(DahuaCameraSdkProperties.class);
            assertThat(context).doesNotHaveBean(DahuaCameraSdkServiceFactory.class);
            assertThat(context).doesNotHaveBean(DahuaCameraSdkService.class);
        });
    }

    @Test
    void createsConfiguredServiceAndClosesItWithTheContext() {
        AtomicReference<DahuaSdkOptions> captured = new AtomicReference<>();
        AtomicReference<DahuaCameraSdkService> service = new AtomicReference<>();
        AtomicInteger cleanupCalls = new AtomicInteger();

        runner.withBean(DahuaCameraSdkServiceFactory.class,
                        () -> options -> {
                            captured.set(options);
                            DahuaCameraSdkService created =
                                    TestDahuaServices.create(options, cleanupCalls);
                            service.set(created);
                            return created;
                        })
                .withPropertyValues(
                        "simple-secret.camera-sdk.dahua.enabled=true",
                        "simple-secret.camera-sdk.dahua.library-directory=vendor/dahua",
                        "simple-secret.camera-sdk.dahua.operation-timeout=4s",
                        "simple-secret.camera-sdk.dahua.radiometry-search-timeout=9s",
                        "simple-secret.camera-sdk.dahua.async-ptz-queue-capacity=96",
                        "simple-secret.camera-sdk.dahua.max-radiometry-results=12000")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DahuaCameraSdkService.class);
                    assertThat(context.getBean(DahuaCameraSdkService.class))
                            .isSameAs(service.get());
                    assertThat(captured.get().libraryDirectory())
                            .isEqualTo(Path.of("vendor/dahua").toAbsolutePath().normalize());
                    assertThat(captured.get().operationTimeout()).isEqualTo(Duration.ofSeconds(4));
                    assertThat(captured.get().radiometrySearchTimeout())
                            .isEqualTo(Duration.ofSeconds(9));
                    assertThat(captured.get().asyncPtzQueueCapacity()).isEqualTo(96);
                    assertThat(captured.get().maxRadiometryResults()).isEqualTo(12_000);
                });

        assertThat(cleanupCalls).hasValue(1);
    }

    @Test
    void failsFastWhenEnabledWithoutLibraryDirectory() {
        runner.withBean(DahuaCameraSdkServiceFactory.class,
                        () -> options -> null)
                .withPropertyValues("simple-secret.camera-sdk.dahua.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(
                            "simple-secret.camera-sdk.dahua.library-directory must be configured");
                });
    }

    @Test
    void backsOffForApplicationProvidedService() {
        AtomicInteger cleanupCalls = new AtomicInteger();
        DahuaCameraSdkService service = TestDahuaServices.create(
                DahuaSdkOptions.defaults(Path.of("vendor/custom-dahua")),
                cleanupCalls);

        try {
            runner.withBean(DahuaCameraSdkService.class, () -> service,
                            definition -> definition.setDestroyMethodName(""))
                    .withPropertyValues("simple-secret.camera-sdk.dahua.enabled=true")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(DahuaCameraSdkService.class);
                        assertThat(context.getBean(DahuaCameraSdkService.class)).isSameAs(service);
                    });
        } finally {
            service.close();
        }
        assertThat(cleanupCalls).hasValue(1);
    }
}
