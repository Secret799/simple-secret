package com.ss.geo.math;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class IntersectionMathTest {

    @Test
    void nearTangentIntersectionShouldOnlyReturnAfterAltitudeConverges() {
        double[] camera = EarthMath.gpsToEcef(0, 0, 100);
        double down = 0.0057;
        double[] rayNed = {Math.sqrt(1.0 - down * down), 0, down};
        double[] rayEcef = EarthMath.nedToEcef(rayNed, 0, 0);

        double[] intersection = IntersectionMath.intersect(camera, rayEcef, 0);

        assertThat(intersection).isNotNull();
        double[] coordinate = EarthMath.ecefToGps(
                intersection[0], intersection[1], intersection[2]);
        assertThat(coordinate[2]).isCloseTo(0, offset(0.01));
    }
}
