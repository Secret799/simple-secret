package com.ss.ics.hikvision;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class HikvisionSdkOptionsTest {

    @Test
    void providesSafeDefaultsWithoutLoadingNativeLibraries() {
        Path libraryDirectory = Path.of("sdk", "hikvision").toAbsolutePath().normalize();

        HikvisionSdkOptions options = HikvisionSdkOptions.defaults(libraryDirectory);

        assertThat(options.libraryDirectory()).isEqualTo(libraryDirectory);
        assertThat(options.fileSearchTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(options.asyncPtzQueueCapacity()).isEqualTo(256);
    }

    @Test
    void rejectsInvalidOptions() {
        Path libraryDirectory = Path.of("sdk").toAbsolutePath();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> HikvisionSdkOptions.defaults(null))
                .withMessage("libraryDirectory must not be null");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HikvisionSdkOptions(
                        libraryDirectory, Duration.ofSeconds(4), 1))
                .withMessage("fileSearchTimeout must be at least 5 seconds");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HikvisionSdkOptions(
                        libraryDirectory, Duration.ofSeconds(5), 0))
                .withMessage("asyncPtzQueueCapacity must be positive");
    }
}
