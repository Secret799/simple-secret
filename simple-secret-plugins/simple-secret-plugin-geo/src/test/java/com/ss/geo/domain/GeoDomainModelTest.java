package com.ss.geo.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class GeoDomainModelTest {

    @Test
    void valueObjectsShouldExposeConstructorsAndChainableAccessors() {
        PixelCoordinate pixel = new PixelCoordinate(12.5, 24.5).setX(13.5);
        GeoCoordinate coordinate = new GeoCoordinate(31.2, 121.5, 18.0).setAlt(20.0);
        BoundingBox box = new BoundingBox(10, 20, 40, 60, "person", 0.9).setWidth(42);

        assertThat(pixel.getX()).isEqualTo(13.5);
        assertThat(pixel.getY()).isEqualTo(24.5);
        assertThat(coordinate.getAlt()).isEqualTo(20.0);
        assertThat(box.centerX()).isEqualTo(31.0);
        assertThat(box.centerY()).isEqualTo(50.0);
    }

    @Test
    void cameraStateShouldRefreshIntrinsicsAfterFovAndFrameAreComplete() {
        CameraState state = new CameraState()
                .setLat(31.2)
                .setLon(121.5)
                .setAlt(100)
                .setGimbalYaw(20)
                .setGimbalPitch(-90)
                .setGimbalRoll(0)
                .setFovH(90)
                .setFovV(60)
                .setFrameWidth(1000)
                .setFrameHeight(500);

        assertThat(state.getPose().getLat()).isEqualTo(31.2);
        assertThat(state.getFrame()).isEqualTo(new FrameSize(1000, 500));
        assertThat(state.getIntrinsics().getCx()).isEqualTo(500);
        assertThat(state.getIntrinsics().getCy()).isEqualTo(250);
        assertThat(state.getIntrinsics().getFx()).isCloseTo(500, offset(1.0e-9));
    }

    @Test
    void pipelineCollectionsShouldBeOwnedByTheModel() {
        List<GeoCoordinate> source = new java.util.ArrayList<>();
        source.add(new GeoCoordinate(1, 2, 3));

        GeoPipeline pipeline = new GeoPipeline().setName("line-a").setPoints(source);
        source.clear();

        assertThat(pipeline.getPoints()).hasSize(1);
        assertThat(pipeline.getPoints()).isNotSameAs(source);
    }
}
