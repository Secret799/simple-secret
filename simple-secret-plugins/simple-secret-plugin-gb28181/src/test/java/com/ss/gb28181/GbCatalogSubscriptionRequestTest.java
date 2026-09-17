package com.ss.gb28181;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GbCatalogSubscriptionRequestTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 15, 10, 0);
    private static final LocalDateTime END = START.plusHours(1);

    @Test
    void createsWholeCatalogRequestWithNoTimeBounds() {
        var request = GbCatalogSubscriptionRequest.all(Duration.ofDays(1));

        assertEquals(Duration.ofSeconds(86_400), request.expires());
        assertNull(request.startTime());
        assertNull(request.endTime());
    }

    @Test
    void acceptsIndependentTimeBoundsAndDocumentedBoundaries() {
        assertEquals(START, new GbCatalogSubscriptionRequest(Duration.ofSeconds(1), START, null).startTime());
        assertEquals(END, new GbCatalogSubscriptionRequest(Duration.ofSeconds(1), null, END).endTime());
        var request = new GbCatalogSubscriptionRequest(Duration.ofSeconds(86_400),
                LocalDateTime.of(1, 1, 1, 0, 0),
                LocalDateTime.of(9999, 12, 31, 23, 59, 59));
        assertEquals(Duration.ofSeconds(86_400), request.expires());
    }

    @Test
    void rejectsMissingFractionalOrOutOfRangeExpiry() {
        assertThrows(NullPointerException.class, () -> GbCatalogSubscriptionRequest.all(null));
        for (Duration expires : new Duration[]{Duration.ZERO, Duration.ofSeconds(-1),
                Duration.ofSeconds(86_401), Duration.ofSeconds(1).plusNanos(1)}) {
            assertThrows(IllegalArgumentException.class, () -> GbCatalogSubscriptionRequest.all(expires));
        }
    }

    @Test
    void rejectsFractionalUnsupportedYearAndUnorderedTimes() {
        assertThrows(IllegalArgumentException.class,
                () -> new GbCatalogSubscriptionRequest(Duration.ofMinutes(1), START.plusNanos(1), null));
        assertThrows(IllegalArgumentException.class,
                () -> new GbCatalogSubscriptionRequest(Duration.ofMinutes(1), null, END.plusNanos(1)));
        assertThrows(IllegalArgumentException.class,
                () -> new GbCatalogSubscriptionRequest(Duration.ofMinutes(1), START.withYear(0), null));
        assertThrows(IllegalArgumentException.class,
                () -> new GbCatalogSubscriptionRequest(Duration.ofMinutes(1), null, END.withYear(10000)));
        assertThrows(IllegalArgumentException.class,
                () -> new GbCatalogSubscriptionRequest(Duration.ofMinutes(1), START, START));
        assertThrows(IllegalArgumentException.class,
                () -> new GbCatalogSubscriptionRequest(Duration.ofMinutes(1), END, START));
    }
}
