package com.ss.easymedia.config;

import com.ss.easymedia.config.properties.EmsProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * EasyMedia 根配置校验测试。
 *
 * @author junpzx
 * @since 2026-08-13
 */
class EmsPropertiesTest {

    @Test
    void shouldDefaultTrackFrameLimitToEightMebibytes() {
        assertThat(new EmsProperties().getMaxTrackFrameBytes()).isEqualTo(8 * 1024 * 1024);
    }

    @Test
    void shouldRejectNonPositiveAndAboveHardMaximumTrackFrameLimits() {
        EmsProperties properties = new EmsProperties();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.setMaxTrackFrameBytes(0))
                .withMessage("max-track-frame-bytes must be positive");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.setMaxTrackFrameBytes(64 * 1024 * 1024 + 1))
                .withMessage("max-track-frame-bytes must not exceed 67108864");
    }
}
