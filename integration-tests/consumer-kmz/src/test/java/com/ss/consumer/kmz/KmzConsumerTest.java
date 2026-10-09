package com.ss.consumer.kmz;

import com.ss.kmz.KmzReadLimits;
import com.ss.kmz.domain.Coordinate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the public KMZ API from a BOM-managed dependency. */
class KmzConsumerTest {

    @Test
    void usesKmzTypesWithoutExplicitDependencyVersion() {
        Coordinate coordinate = Coordinate.fromKml("121.4737,31.2304,12");
        KmzReadLimits limits = KmzReadLimits.defaults();

        assertThat(coordinate.toKml()).isEqualTo("121.47370000,31.23040000,12.00");
        assertThat(limits.maxEntries()).isEqualTo(KmzReadLimits.DEFAULT_MAX_ENTRIES);
    }
}
