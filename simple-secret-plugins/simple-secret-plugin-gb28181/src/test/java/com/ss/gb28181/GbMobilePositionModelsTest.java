package com.ss.gb28181;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GbMobilePositionModelsTest {
    private static final String OWNER = "34020000002000000001";
    private static final String CHANNEL = "34020000001320000001";

    @Test
    void requestDefaultsIntervalAndAcceptsIndependentWholeSecondBounds() {
        var request = new GbMobilePositionSubscriptionRequest(Duration.ofMinutes(10));
        assertEquals(Duration.ofMinutes(10), request.expires());
        assertEquals(Duration.ofSeconds(5), request.interval());

        assertEquals(Duration.ofSeconds(1),
                new GbMobilePositionSubscriptionRequest(Duration.ofSeconds(1), Duration.ofSeconds(86_400)).expires());
        assertEquals(Duration.ofSeconds(86_400),
                new GbMobilePositionSubscriptionRequest(Duration.ofSeconds(1), Duration.ofSeconds(86_400)).interval());
    }

    @Test
    void requestRejectsMissingFractionalAndOutOfRangeDurations() {
        assertThrows(NullPointerException.class,
                () -> new GbMobilePositionSubscriptionRequest(null, Duration.ofSeconds(5)));
        assertThrows(NullPointerException.class,
                () -> new GbMobilePositionSubscriptionRequest(Duration.ofSeconds(5), null));
        for (Duration invalid : List.of(Duration.ZERO, Duration.ofSeconds(-1), Duration.ofSeconds(86_401),
                Duration.ofSeconds(1).plusNanos(1))) {
            assertThrows(IllegalArgumentException.class,
                    () -> new GbMobilePositionSubscriptionRequest(invalid, Duration.ofSeconds(5)));
            assertThrows(IllegalArgumentException.class,
                    () -> new GbMobilePositionSubscriptionRequest(Duration.ofSeconds(5), invalid));
        }
    }

    @Test
    void notificationCopiesPositionsAndPreservesOrderAndDuplicates() {
        var position = new GbMobilePosition(CHANNEL, "2026-09-15T10:20:30+08:00",
                121.5, 31.2, 12.5, 359.9, 8.0, 2.0);
        var positions = new ArrayList<>(List.of(position, position));

        var notification = new GbMobilePositionNotification(
                OWNER, 7, "2026-09-15T10:20:31+08:00", 2, positions);
        positions.clear();

        assertEquals(List.of(position, position), notification.positions());
        assertThrows(UnsupportedOperationException.class, () -> notification.positions().clear());
    }

    @Test
    void modelsRejectMissingRequiredValues() {
        assertThrows(NullPointerException.class,
                () -> new GbMobilePosition(null, "2026-09-15T10:20:30Z", 0, 0, null, null, null, null));
        assertThrows(NullPointerException.class,
                () -> new GbMobilePosition(CHANNEL, null, 0, 0, null, null, null, null));
        assertThrows(NullPointerException.class,
                () -> new GbMobilePositionNotification(null, 1, "2026-09-15T10:20:30Z", 0, List.of()));
        assertThrows(NullPointerException.class,
                () -> new GbMobilePositionNotification(OWNER, 1, null, 0, List.of()));
        assertThrows(NullPointerException.class,
                () -> new GbMobilePositionNotification(OWNER, 1, "2026-09-15T10:20:30Z", 0, null));
    }

    @Test
    void eventAndListenerExposeAuthenticatedContext() {
        var notification = new GbMobilePositionNotification(
                OWNER, 7, "2026-09-15T10:20:31Z", 0, List.of());
        var receivedAt = Instant.parse("2026-09-15T02:20:32Z");
        var event = new GbMobilePositionEvent(OWNER, "position-call", receivedAt, notification);
        GbMobilePositionListener listener = delivered -> assertSame(event, delivered);

        listener.onMobilePosition(event);
        assertEquals(OWNER, event.sourceDeviceId());
        assertEquals("position-call", event.callId());
        assertEquals(receivedAt, event.receivedAt());
        assertSame(notification, event.notification());
    }

    @Test
    void eventRejectsMissingOrInvalidAuthenticatedContext() {
        var notification = new GbMobilePositionNotification(
                OWNER, 7, "2026-09-15T10:20:31Z", 0, List.of());
        var receivedAt = Instant.parse("2026-09-15T02:20:32Z");

        assertThrows(NullPointerException.class,
                () -> new GbMobilePositionEvent(null, "position-call", receivedAt, notification));
        assertThrows(IllegalArgumentException.class,
                () -> new GbMobilePositionEvent("123", "position-call", receivedAt, notification));
        assertThrows(NullPointerException.class,
                () -> new GbMobilePositionEvent(OWNER, null, receivedAt, notification));
        assertThrows(NullPointerException.class,
                () -> new GbMobilePositionEvent(OWNER, "position-call", null, notification));
        assertThrows(NullPointerException.class,
                () -> new GbMobilePositionEvent(OWNER, "position-call", receivedAt, null));
    }
}
