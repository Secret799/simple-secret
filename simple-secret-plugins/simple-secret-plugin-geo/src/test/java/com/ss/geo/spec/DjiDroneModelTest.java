package com.ss.geo.spec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DjiDroneModelTest {

    @Test
    void shouldResolveDroneByTypeCodeAndCommonPhotoNames() {
        assertThat(DjiDroneModel.getByTypeCode("0-91-1")).isEqualTo(DjiDroneModel.M3TD);
        assertThat(DjiDroneModel.findByName("DJI M3TD")).isEqualTo(DjiDroneModel.M3TD);
        assertThat(DjiDroneModel.findByName("Matrice 4TD")).isEqualTo(DjiDroneModel.M4TD);
        assertThat(DjiDroneModel.findByName("M350 RTK")).isEqualTo(DjiDroneModel.M350);
        assertThat(DjiDroneModel.findByName(null)).isNull();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DjiDroneModel.getByTypeCode("unknown"))
                .withMessageContaining("unknown");
    }

    @Test
    void zoomShouldNarrowFovWithoutMutatingCameraSpecification() {
        CameraSpec spec = DjiDroneModel.M3TD.getSpec(CameraType.ZOOM);

        double wide = spec.diagonalFovAtZoom(1.0);
        double zoomed = spec.diagonalFovAtZoom(7.0);

        assertThat(zoomed).isLessThan(wide);
        assertThat(DjiDroneModel.M3TD.getAvailableCameras()).contains(CameraType.WIDE, CameraType.ZOOM);
        assertThat(DjiDroneModel.M3TD.getAvailableCameras())
                .isUnmodifiable();
    }

    @Test
    void zoomRangeShouldBeNormalizedFromCameraBaseline() {
        CameraSpec spec = DjiDroneModel.M30.getSpec(CameraType.ZOOM);
        double expectedAtMaximum = 2.0 * Math.toDegrees(Math.atan(
                Math.tan(Math.toRadians(spec.diagonalFov()) / 2.0) / 40.0));

        assertThat(spec.diagonalFovAtZoom(5.0)).isEqualTo(spec.diagonalFov());
        assertThat(spec.diagonalFovAtZoom(200.0)).isCloseTo(
                expectedAtMaximum, org.assertj.core.data.Offset.offset(1.0e-12));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> spec.diagonalFovAtZoom(4.0))
                .withMessageContaining("zoom");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> spec.diagonalFovAtZoom(201.0))
                .withMessageContaining("zoom");
    }
}
