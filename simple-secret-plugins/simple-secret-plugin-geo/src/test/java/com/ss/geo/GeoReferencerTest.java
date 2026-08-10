package com.ss.geo;

import com.ss.geo.domain.CameraState;
import com.ss.geo.domain.BoundingBox;
import com.ss.geo.domain.GeoCoordinate;
import com.ss.geo.domain.GeoTarget;
import com.ss.geo.domain.GeoTargetWithBox;
import com.ss.geo.domain.PipelineProjection;
import com.ss.geo.domain.PixelCoordinate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.data.Offset.offset;

class GeoReferencerTest {

    @Test
    void centerPixelShouldIntersectGroundDirectlyBelowCamera() {
        GeoTarget target = GeoReferencer.pixelToGeo(new PixelCoordinate(500, 500), downLookingCamera(), 0);

        assertThat(target).isNotNull();
        assertThat(target.getLat()).isCloseTo(0, offset(1.0e-8));
        assertThat(target.getLon()).isCloseTo(0, offset(1.0e-8));
        assertThat(target.getDistance()).isCloseTo(100, offset(0.01));
    }

    @Test
    void pointBelowCameraShouldProjectToImageCenter() {
        PixelCoordinate pixel = GeoReferencer.geoToPixel(new GeoCoordinate(0, 0, 0), downLookingCamera());

        assertThat(pixel).isNotNull();
        assertThat(pixel.getX()).isCloseTo(500, offset(0.01));
        assertThat(pixel.getY()).isCloseTo(500, offset(0.01));
    }

    @Test
    void eastPointShouldProjectToImageRightAndRoundTrip() {
        CameraState state = downLookingCamera();
        GeoCoordinate east = new GeoCoordinate(0, 10.0 / 111319.49079327358, 0);

        PixelCoordinate pixel = GeoReferencer.geoToPixel(east, state);
        GeoTarget restored = GeoReferencer.pixelToGeo(pixel, state, 0);

        assertThat(pixel.getX()).isGreaterThan(500);
        assertThat(restored.getLon()).isCloseTo(east.getLon(), offset(1.0e-8));
    }

    @Test
    void horizontalRayShouldNotIntersectLowerGround() {
        CameraState state = downLookingCamera().setGimbalPitch(0);

        assertThat(GeoReferencer.pixelToGeo(new PixelCoordinate(500, 500), state, 0)).isNull();
    }

    @Test
    void horizontalDistanceShouldIncludeNorthAndEastComponents() {
        GeoTarget north = GeoReferencer.pixelToGeo(
                new PixelCoordinate(500, 400), downLookingCamera(), 0);

        assertThat(north).isNotNull();
        assertThat(north.getHorizontalDistance()).isCloseTo(20.0, offset(0.05));
    }

    @Test
    void obliqueIntersectionShouldConvergeToRequestedAltitude() {
        CameraState state = downLookingCamera()
                .setAlt(1000)
                .setGimbalPitch(-30);

        GeoTarget target = GeoReferencer.pixelToGeo(new PixelCoordinate(500, 500), state, 0);

        assertThat(target).isNotNull();
        assertThat(target.getAlt()).isCloseTo(0, offset(0.05));
    }

    @Test
    void pipelineProjectionShouldHandleBufferDuplicatesAndVisibility() {
        CameraState state = downLookingCamera();
        List<GeoCoordinate> pipeline = List.of(
                new GeoCoordinate(0, 0, 0),
                new GeoCoordinate(0, 0, 0),
                new GeoCoordinate(0, 20.0 / 111319.49079327358, 0));

        PipelineProjection projection = GeoReferencer.pipelineToPixels("pipe-a", pipeline, state, 2.0);
        PipelineProjection behind = GeoReferencer.pipelineToPixels(
                "behind", List.of(new GeoCoordinate(0, 0, 200)), state);

        assertThat(projection.getCenterline()).hasSize(3);
        assertThat(projection.getArea()).hasSize(4);
        assertThat(behind.getCenterline()).singleElement().satisfies(point -> {
            assertThat(point.getPixel()).isNull();
            assertThat(point.isVisible()).isFalse();
            assertThat(point.isInsideFrame()).isFalse();
        });
    }

    @Test
    void nullAndDegeneratePipelinesShouldReturnEmptyArea() {
        CameraState state = downLookingCamera();

        PipelineProjection empty = GeoReferencer.pipelineToPixels("empty", null, state, 2.0);
        PipelineProjection duplicate = GeoReferencer.pipelineToPixels("duplicate", List.of(
                new GeoCoordinate(0, 0, 0),
                new GeoCoordinate(0, 0, 0)), state, 2.0);

        assertThat(empty.getCenterline()).isEmpty();
        assertThat(empty.getArea()).isEmpty();
        assertThat(duplicate.getCenterline()).hasSize(2);
        assertThat(duplicate.getArea()).isEmpty();
    }

    @Test
    void batchResultsShouldPreserveInputOrderAndExposeLocationStatus() {
        BoundingBox box = new BoundingBox(450, 450, 100, 100, "target", 0.9);

        List<GeoTargetWithBox> located = GeoReferencer.boxesToGeo(
                List.of(box), downLookingCamera(), 0);
        List<GeoTargetWithBox> missed = GeoReferencer.boxesToGeo(
                List.of(box), downLookingCamera().setGimbalPitch(0), 0);

        assertThat(located).singleElement().satisfies(result -> {
            assertThat(result.getBox()).isSameAs(box);
            assertThat(result.isLocated()).isTrue();
        });
        assertThat(missed).singleElement().satisfies(result -> {
            assertThat(result.getBox()).isSameAs(box);
            assertThat(result.isLocated()).isFalse();
        });
        assertThat(GeoReferencer.boxesToGeo(null, downLookingCamera(), 0)).isEmpty();
    }

    @Test
    void nullPipelinePointsShouldBeIgnoredConsistently() {
        List<GeoCoordinate> pipeline = new ArrayList<>();
        pipeline.add(new GeoCoordinate(0, 0, 0));
        pipeline.add(null);
        pipeline.add(new GeoCoordinate(0, 20.0 / 111319.49079327358, 0));

        PipelineProjection projection = GeoReferencer.pipelineToPixels(
                "nullable", pipeline, downLookingCamera(), 2.0);

        assertThat(projection.getCenterline()).hasSize(2);
        assertThat(projection.getArea()).hasSize(4);
    }

    @Test
    void invalidProjectionInputsShouldFailClearly() {
        CameraState invalidFov = downLookingCamera().setFovH(0);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> GeoReferencer.pixelToGeo(null, downLookingCamera(), 0))
                .withMessageContaining("pixel");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GeoReferencer.pixelToGeo(
                        new PixelCoordinate(Double.NaN, 0), downLookingCamera(), 0))
                .withMessageContaining("pixel");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GeoReferencer.pixelToGeo(
                        new PixelCoordinate(500, 500), invalidFov, 0))
                .withMessageContaining("camera")
                .withMessageContaining("FOV");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GeoReferencer.geoToPixel(
                        new GeoCoordinate(91, 0, 0), downLookingCamera()))
                .withMessageContaining("latitude");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GeoReferencer.pixelToGeo(
                        new PixelCoordinate(500, 500), downLookingCamera(), Double.NaN))
                .withMessageContaining("ground altitude");
    }

    @Test
    void demProjectionShouldRejectMissingCallbackAndFallbackForNonFiniteAltitude() {
        PixelCoordinate center = new PixelCoordinate(500, 500);
        GeoTarget direct = GeoReferencer.pixelToGeo(center, downLookingCamera(), 0);
        GeoTarget fallback = GeoReferencer.pixelToGeo(
                center, downLookingCamera(), ignored -> Double.NaN, 0);

        assertThat(fallback.getLat()).isCloseTo(direct.getLat(), offset(1.0e-12));
        assertThat(fallback.getLon()).isCloseTo(direct.getLon(), offset(1.0e-12));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> GeoReferencer.pixelToGeo(
                        center, downLookingCamera(), null, 0))
                .withMessageContaining("DEM");
    }

    @Test
    void pipelineBufferShouldKeepRequestedDistanceAtRightAngleJoin() {
        double metersPerDegreeLatitude = 110574.27582159435;
        double metersPerDegreeLongitude = 111319.49079327358;
        GeoCoordinate corner = new GeoCoordinate(0, 10.0 / metersPerDegreeLongitude, 0);
        List<GeoCoordinate> pipeline = List.of(
                new GeoCoordinate(0, 0, 0),
                corner,
                new GeoCoordinate(10.0 / metersPerDegreeLatitude, 10.0 / metersPerDegreeLongitude, 0));

        PipelineProjection projection = GeoReferencer.pipelineToPixels(
                "right-angle", pipeline, downLookingCamera(), 2.0);
        GeoCoordinate leftJoin = projection.getArea().get(1).getCoordinate();

        double northOffset = (leftJoin.getLat() - corner.getLat()) * metersPerDegreeLatitude;
        double eastOffset = (leftJoin.getLon() - corner.getLon()) * metersPerDegreeLongitude;
        assertThat(northOffset).isCloseTo(2.0, offset(0.01));
        assertThat(eastOffset).isCloseTo(-2.0, offset(0.01));
    }

    private static CameraState downLookingCamera() {
        return new CameraState()
                .setLat(0).setLon(0).setAlt(100)
                .setGimbalYaw(0).setGimbalPitch(-90).setGimbalRoll(0)
                .setFovH(90).setFovV(90)
                .setFrameWidth(1000).setFrameHeight(1000);
    }
}
