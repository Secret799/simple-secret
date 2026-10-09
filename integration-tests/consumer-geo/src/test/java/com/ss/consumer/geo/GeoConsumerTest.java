package com.ss.consumer.geo;

import com.ss.geo.domain.GeoCoordinate;
import com.ss.geo.math.EarthMath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the public geospatial API from a BOM-managed dependency. */
class GeoConsumerTest {

    @Test
    void convertsPublishedGeoCoordinatesWithoutExplicitDependencyVersion() {
        GeoCoordinate coordinate = new GeoCoordinate(31.2304, 121.4737, 12.0);
        double[] ecef = EarthMath.gpsToEcef(
                coordinate.getLat(), coordinate.getLon(), coordinate.getAlt());
        double[] roundTrip = EarthMath.ecefToGps(ecef[0], ecef[1], ecef[2]);

        assertThat(roundTrip[0]).isCloseTo(coordinate.getLat(), within(1.0e-6));
        assertThat(roundTrip[1]).isCloseTo(coordinate.getLon(), within(1.0e-6));
        assertThat(roundTrip[2]).isCloseTo(coordinate.getAlt(), within(1.0e-3));
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
