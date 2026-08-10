package com.ss.zlm4j.service.validation;

import com.ss.zlm4j.config.properties.VideoStackValidationProperties;
import com.ss.zlm4j.service.domain.bo.VideoStackBO;
import com.ss.zlm4j.service.domain.bo.VideoStackWindowBO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoStackValidatorTest {

    private final VideoStackValidator validator = new VideoStackValidator(new VideoStackValidationProperties());

    @Test
    void acceptsValidRectangularLayout() {
        VideoStackBO value = baseStack().setWindowList(List.of(
                window(List.of(1, 2), "112233"),
                window(List.of(3, 4), "AABBCC")));

        assertThatCode(() -> validator.validate(value)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonPositiveOverflowingAndOversizedDimensions() {
        assertInvalid(baseStack().setWidth(0));
        assertInvalid(baseStack().setHeight(-1));
        assertInvalid(baseStack().setRow(Integer.MAX_VALUE).setCol(2));
        assertInvalid(baseStack().setWidth(100_000).setHeight(100_000));
        assertInvalid(baseStack().setWidth(641));
    }

    @Test
    void rejectsInvalidColorsAndGridLineWidth() {
        assertInvalid(baseStack().setFillColor("not-a-color"));
        assertInvalid(baseStack().setGridLineEnable(true).setGridLineColor("FFFF").setGridLineWidth(1));
        assertInvalid(baseStack().setGridLineEnable(true).setGridLineWidth(400));
        assertInvalid(baseStack().setWindowList(List.of(window(List.of(1), "XYZXYZ"))));
    }

    @Test
    void rejectsOutOfRangeDuplicateOverlappingAndNonRectangularSpans() {
        assertInvalid(baseStack().setWindowList(List.of(window(List.of(0), "000000"))));
        assertInvalid(baseStack().setWindowList(List.of(window(List.of(1, 1), "000000"))));
        assertInvalid(baseStack().setWindowList(List.of(
                window(List.of(1, 2), "000000"),
                window(List.of(2, 3), "000000"))));
        assertInvalid(baseStack().setRow(2).setCol(3).setWindowList(List.of(
                window(List.of(1, 2, 4), "000000"))));
    }

    @Test
    void rejectsWindowWithBothVideoAndImageSources() {
        VideoStackWindowBO window = window(List.of(1), "000000")
                .setVideoUrl("rtsp://camera/live")
                .setImgUrl("https://image/fill.jpg");

        assertInvalid(baseStack().setWindowList(List.of(window)));
    }

    private void assertInvalid(VideoStackBO value) {
        assertThatThrownBy(() -> validator.validate(value)).isInstanceOf(IllegalArgumentException.class);
    }

    private static VideoStackBO baseStack() {
        return new VideoStackBO()
                .setId("wall")
                .setRow(2)
                .setCol(2)
                .setWidth(640)
                .setHeight(480)
                .setFillColor("BFBFBF")
                .setGridLineEnable(false)
                .setGridLineWidth(1);
    }

    private static VideoStackWindowBO window(List<Integer> span, String color) {
        return new VideoStackWindowBO().setSpan(span).setFillColor(color);
    }
}
