package com.ss.application.djisei.diagnostic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * 有界负载预览测试。
 *
 * @author junpzx
 * @since 2026-08-13
 */
class PayloadPreviewTest {

    @Test
    void shouldBoundHexAndPrintableAsciiPreview() {
        PayloadPreview preview = PayloadPreview.from(
                new byte[]{'D', 'J', 'I', 0, 1, 2, 'X'}, 4);

        assertThat(preview.hex()).isEqualTo("444a4900...");
        assertThat(preview.text()).isEqualTo("DJI.");
        assertThat(preview.truncated()).isTrue();
    }

    @Test
    void shouldNotAppendEllipsisWhenPayloadFitsLimit() {
        PayloadPreview preview = PayloadPreview.from(new byte[]{0x20, 0x7e, 0x7f}, 3);

        assertThat(preview.hex()).isEqualTo("207e7f");
        assertThat(preview.text()).isEqualTo(" ~.");
        assertThat(preview.truncated()).isFalse();
    }

    @Test
    void shouldRenderEmptyPayloadWithoutData() {
        PayloadPreview preview = PayloadPreview.from(new byte[0], 4);

        assertThat(preview.hex()).isEmpty();
        assertThat(preview.text()).isEmpty();
        assertThat(preview.truncated()).isFalse();
    }

    @Test
    void shouldRejectInvalidArguments() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PayloadPreview.from(new byte[]{1}, 0));
    }
}
