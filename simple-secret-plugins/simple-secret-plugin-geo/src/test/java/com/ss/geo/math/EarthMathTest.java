package com.ss.geo.math;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class EarthMathTest {

    @Test
    void exactPolesShouldConvertWithoutNaN() {
        double semiMinorAxis = EarthMath.WGS84_A * (1.0 - EarthMath.WGS84_F);

        double[] north = EarthMath.ecefToGps(0, 0, semiMinorAxis + 100);
        double[] south = EarthMath.ecefToGps(0, 0, -semiMinorAxis - 50);

        assertThat(north[0]).isCloseTo(90, offset(1.0e-12));
        assertThat(north[2]).isCloseTo(100, offset(1.0e-9));
        assertThat(south[0]).isCloseTo(-90, offset(1.0e-12));
        assertThat(south[2]).isCloseTo(50, offset(1.0e-9));
    }
}
