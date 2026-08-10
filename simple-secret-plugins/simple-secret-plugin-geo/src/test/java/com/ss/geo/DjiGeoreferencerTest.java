package com.ss.geo;

import com.ss.geo.domain.CameraState;
import com.ss.geo.domain.GeoCoordinate;
import com.ss.geo.domain.GeoTarget;
import com.ss.geo.domain.PipelineProjection;
import com.ss.geo.domain.PixelCoordinate;
import com.ss.geo.photo.DjiPhotoMetadata;
import com.ss.geo.spec.CameraType;
import com.ss.geo.spec.DjiCameraTelemetry;
import com.ss.geo.spec.DjiDroneModel;
import com.ss.geo.spec.DjiProjectionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DjiGeoreferencerTest {

    @TempDir
    Path tempDir;

    @Test
    void explicitCalibrationShouldTakePriorityOverDroneSpecification() {
        DjiPhotoMetadata metadata = baseMetadata()
                .setCalibratedFocalLength(1000.0)
                .setFocalLength(29.85)
                .setDroneTypeCode("0-91-1")
                .setImageSource("ZoomCamera")
                .setZoomFactor(7.0);

        CameraState state = DjiPhotoGeoreferencer.buildCameraState(metadata);

        assertThat(state.getIntrinsics().getFx()).isEqualTo(1000.0);
        assertThat(state.getIntrinsics().getFy()).isEqualTo(1000.0);
    }

    @Test
    void zoomContextShouldNarrowFovAndMoveOffCenterTargetTowardCamera() {
        DjiPhotoMetadata metadata = baseMetadata().setCalibratedFocalLength(null);
        DjiProjectionContext wide = context(1.0);
        DjiProjectionContext zoomed = context(7.0);

        CameraState wideState = DjiPhotoGeoreferencer.buildCameraState(metadata, wide);
        CameraState zoomedState = DjiPhotoGeoreferencer.buildCameraState(metadata, zoomed);
        GeoTarget wideTarget = GeoReferencer.pixelToGeo(new PixelCoordinate(750, 500), wideState, 0);
        GeoTarget zoomedTarget = GeoReferencer.pixelToGeo(new PixelCoordinate(750, 500), zoomedState, 0);

        assertThat(zoomedState.getFovH()).isLessThan(wideState.getFovH());
        assertThat(Math.abs(zoomedTarget.getLon())).isLessThan(Math.abs(wideTarget.getLon()));
    }

    @Test
    void focalPlaneResolutionShouldHonorExifUnitsAndIgnoreUnknownUnits() {
        DjiPhotoMetadata millimeters = baseMetadata()
                .setCalibratedFocalLength(null)
                .setFocalLength(10.0)
                .setFocalPlaneXResolution(100.0)
                .setFocalPlaneResolutionUnit(4);
        DjiPhotoMetadata unknownUnit = baseMetadata()
                .setCalibratedFocalLength(null)
                .setFocalLength(10.0)
                .setFocalPlaneXResolution(100.0)
                .setFocalPlaneResolutionUnit(1)
                .setFocalLength35mm(36.0);
        DjiPhotoMetadata noFocalPlaneResolution = baseMetadata()
                .setCalibratedFocalLength(null)
                .setFocalLength35mm(36.0);

        CameraState millimeterState = DjiPhotoGeoreferencer.buildCameraState(millimeters);
        CameraState fallbackState = DjiPhotoGeoreferencer.buildCameraState(unknownUnit);
        CameraState expectedFallback = DjiPhotoGeoreferencer.buildCameraState(noFocalPlaneResolution);

        assertThat(millimeterState.getIntrinsics().getFx()).isCloseTo(
                1000.0, org.assertj.core.data.Offset.offset(1.0e-9));
        assertThat(fallbackState.getIntrinsics().getFx()).isEqualTo(
                expectedFallback.getIntrinsics().getFx());
    }

    @Test
    void telemetrySpecificationShouldUseActualFrameAspectAndRejectExcessiveZoom() {
        DjiCameraTelemetry widescreen = telemetry()
                .setFrameWidth(1920)
                .setFrameHeight(1080);
        DjiCameraTelemetry excessiveZoom = telemetry()
                .setProjectionContext(context(57.0));

        CameraState state = DjiTelemetryGeoreferencer.buildCameraState(widescreen);

        assertThat(state.getIntrinsics().getFx()).isCloseTo(
                state.getIntrinsics().getFy(), org.assertj.core.data.Offset.offset(1.0e-9));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DjiTelemetryGeoreferencer.buildCameraState(excessiveZoom))
                .withMessageContaining("zoom");
    }

    @Test
    void flippedNadirPhotoShouldUseFlightYawAndPreserveRoll() {
        DjiPhotoMetadata metadata = baseMetadata()
                .setFlightYaw(180.0)
                .setGimbalYaw(0.0)
                .setGimbalPitch(-90.0)
                .setGimbalRoll(180.0);

        CameraState state = DjiPhotoGeoreferencer.buildCameraState(metadata);
        GeoTarget left = DjiPhotoGeoreferencer.refer(metadata, 250, 500, 0);
        GeoTarget right = DjiPhotoGeoreferencer.refer(metadata, 750, 500, 0);

        assertThat(state.getGimbalYaw()).isEqualTo(180.0);
        assertThat(state.getGimbalRoll()).isEqualTo(180.0);
        assertThat(left.getLon()).isLessThan(metadata.getGpsLon());
        assertThat(right.getLon()).isGreaterThan(metadata.getGpsLon());
    }

    @Test
    void photoAndTelemetryShouldProjectPipelines() {
        List<GeoCoordinate> pipeline = List.of(
                new GeoCoordinate(0, 0, 0),
                new GeoCoordinate(0, 20.0 / 111319.49079327358, 0));

        PipelineProjection photo = DjiPhotoGeoreferencer.referPipeline(
                baseMetadata(), "photo", pipeline, 2.0);
        PipelineProjection telemetry = DjiTelemetryGeoreferencer.pipelineToPixels(
                telemetry(), "telemetry", pipeline, 2.0);

        assertThat(photo.getCenterline()).hasSize(2);
        assertThat(photo.getArea()).hasSize(4);
        assertThat(telemetry.getCenterline()).hasSize(2);
        assertThat(telemetry.getArea()).hasSize(4);
    }

    @Test
    void telemetryShouldRequireSupportedCameraContext() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DjiTelemetryGeoreferencer.buildCameraState(new DjiCameraTelemetry()))
                .withMessageContaining("droneModel")
                .withMessageContaining("cameraType");
    }

    @Test
    void photoPathShouldBeReadThroughBoundedMetadataReader() throws Exception {
        Path photo = tempDir.resolve("dji.jpg");
        Files.write(photo, minimalDjiJpeg());

        GeoTarget target = DjiPhotoGeoreferencer.refer(photo, 500, 500, 0);

        assertThat(target).isNotNull();
        assertThat(target.getLat()).isCloseTo(0, org.assertj.core.data.Offset.offset(1.0e-8));
        assertThat(target.getLon()).isCloseTo(0, org.assertj.core.data.Offset.offset(1.0e-8));
    }

    @Test
    void photoMetadataShouldRequireAbsoluteWgs84PositionAndAltitude() {
        DjiPhotoMetadata missingPosition = baseMetadata().setGpsLat(null);
        DjiPhotoMetadata relativeOnly = baseMetadata()
                .setGpsAlt(null)
                .setRelativeAltitude(100.0);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> DjiPhotoGeoreferencer.buildCameraState(missingPosition))
                .withMessageContaining("latitude")
                .withMessageContaining("longitude");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DjiPhotoGeoreferencer.buildCameraState(relativeOnly))
                .withMessageContaining("absolute altitude");
    }

    @Test
    void photoAndTelemetryShouldRequirePositiveFrameDimensions() {
        DjiPhotoMetadata photo = baseMetadata().setImageWidth(0);
        DjiCameraTelemetry telemetry = telemetry().setFrameHeight(0);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> DjiPhotoGeoreferencer.buildCameraState(photo))
                .withMessageContaining("frame width")
                .withMessageContaining("frame height");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DjiTelemetryGeoreferencer.buildCameraState(telemetry))
                .withMessageContaining("frame width")
                .withMessageContaining("frame height");
    }

    @Test
    void photoAndTelemetryShouldRejectInvalidPoseValues() {
        DjiPhotoMetadata photo = baseMetadata()
                .setGimbalPitch(Double.NaN)
                .setFlightPitch(Double.NaN);
        DjiCameraTelemetry telemetry = telemetry().setLat(91);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> DjiPhotoGeoreferencer.buildCameraState(photo))
                .withMessageContaining("camera")
                .withMessageContaining("attitude");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DjiTelemetryGeoreferencer.buildCameraState(telemetry))
                .withMessageContaining("latitude");
    }

    private static DjiProjectionContext context(double zoom) {
        return new DjiProjectionContext()
                .setDroneModel(DjiDroneModel.M3TD)
                .setCameraType(CameraType.ZOOM)
                .setZoomFactor(zoom);
    }

    private static DjiPhotoMetadata baseMetadata() {
        return new DjiPhotoMetadata()
                .setGpsLat(0.0).setGpsLon(0.0).setGpsAlt(100.0)
                .setFlightYaw(0.0).setFlightPitch(0.0).setFlightRoll(0.0)
                .setGimbalYaw(0.0).setGimbalPitch(-90.0).setGimbalRoll(0.0)
                .setCalibratedFocalLength(500.0)
                .setImageWidth(1000).setImageHeight(1000);
    }

    private static DjiCameraTelemetry telemetry() {
        return new DjiCameraTelemetry()
                .setLat(0).setLon(0).setAlt(100)
                .setFlightYaw(0).setGimbalYaw(0).setGimbalPitch(-90).setGimbalRoll(0)
                .setFrameWidth(1000).setFrameHeight(1000)
                .setProjectionContext(context(1.0));
    }

    private static byte[] minimalDjiJpeg() {
        String xmp = "<rdf:Description xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\""
                + " xmlns:drone-dji=\"http://www.dji.com/drone-dji/1.0/\""
                + " drone-dji:GpsLatitude=\"0\" drone-dji:GpsLongitude=\"0\""
                + " drone-dji:AbsoluteAltitude=\"100\" drone-dji:GimbalYawDegree=\"0\""
                + " drone-dji:GimbalPitchDegree=\"-90\" drone-dji:GimbalRollDegree=\"0\""
                + " drone-dji:CalibratedFocalLength=\"500\"/>";
        byte[] id = "http://ns.adobe.com/xap/1.0/\0".getBytes(StandardCharsets.US_ASCII);
        byte[] xml = xmp.getBytes(StandardCharsets.UTF_8);
        int app1Length = 2 + id.length + xml.length;
        byte[] jpeg = new byte[2 + 9 + 2 + app1Length + 2];
        int offset = 0;
        jpeg[offset++] = (byte) 0xFF; jpeg[offset++] = (byte) 0xD8;
        jpeg[offset++] = (byte) 0xFF; jpeg[offset++] = (byte) 0xC0;
        jpeg[offset++] = 0; jpeg[offset++] = 7; jpeg[offset++] = 8;
        jpeg[offset++] = 3; jpeg[offset++] = (byte) 0xE8;
        jpeg[offset++] = 3; jpeg[offset++] = (byte) 0xE8;
        jpeg[offset++] = (byte) 0xFF; jpeg[offset++] = (byte) 0xE1;
        jpeg[offset++] = (byte) (app1Length >>> 8); jpeg[offset++] = (byte) app1Length;
        System.arraycopy(id, 0, jpeg, offset, id.length); offset += id.length;
        System.arraycopy(xml, 0, jpeg, offset, xml.length); offset += xml.length;
        jpeg[offset++] = (byte) 0xFF; jpeg[offset] = (byte) 0xD9;
        return jpeg;
    }
}
