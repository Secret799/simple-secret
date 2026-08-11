package com.ss.ics.dahua;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DahuaSdkOptionsTest {

    @Test
    void providesSafeDefaultsWithoutLoadingNativeLibraries() {
        Path libraryDirectory = Path.of("sdk", "dahua").toAbsolutePath().normalize();

        DahuaSdkOptions options = DahuaSdkOptions.defaults(libraryDirectory);

        assertThat(options.libraryDirectory()).isEqualTo(libraryDirectory);
        assertThat(options.operationTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(options.radiometrySearchTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(options.asyncPtzQueueCapacity()).isEqualTo(256);
        assertThat(options.maxRadiometryResults()).isEqualTo(10_000);
    }

    @Test
    void rejectsInvalidOptions() {
        Path libraryDirectory = Path.of("sdk").toAbsolutePath();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> DahuaSdkOptions.defaults(null))
                .withMessage("libraryDirectory must not be null");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DahuaSdkOptions(
                        libraryDirectory, Duration.ZERO, Duration.ofSeconds(5), 1, 1))
                .withMessage("operationTimeout must be positive");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DahuaSdkOptions(
                        libraryDirectory, Duration.ofSeconds(3), Duration.ZERO, 1, 1))
                .withMessage("radiometrySearchTimeout must be positive");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DahuaSdkOptions(
                        libraryDirectory, Duration.ofSeconds(3), Duration.ofSeconds(5), 0, 1))
                .withMessage("asyncPtzQueueCapacity must be positive");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DahuaSdkOptions(
                        libraryDirectory, Duration.ofSeconds(3), Duration.ofSeconds(5), 1, 0))
                .withMessage("maxRadiometryResults must be positive");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DahuaSdkOptions(
                        libraryDirectory, Duration.ofMillis((long) Integer.MAX_VALUE + 1),
                        Duration.ofSeconds(5), 1, 1))
                .withMessage("operationTimeout must not exceed 2147483647 milliseconds");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DahuaSdkOptions(
                        libraryDirectory, Duration.ofSeconds(3),
                        Duration.ofMillis((long) Integer.MAX_VALUE + 1), 1, 1))
                .withMessage("radiometrySearchTimeout must not exceed 2147483647 milliseconds");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DahuaSdkOptions(
                        libraryDirectory, Duration.ofSeconds(3), Duration.ofSeconds(5),
                        10_001, 1))
                .withMessage("asyncPtzQueueCapacity must not exceed 10000");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DahuaSdkOptions(
                        libraryDirectory, Duration.ofSeconds(3), Duration.ofSeconds(5),
                        1, 100_001))
                .withMessage("maxRadiometryResults must not exceed 100000");
    }
}
