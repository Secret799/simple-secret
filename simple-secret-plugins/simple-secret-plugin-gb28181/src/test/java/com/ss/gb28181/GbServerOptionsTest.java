package com.ss.gb28181;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GbServerOptionsTest {
    @Test
    void mobilePositionCapacitiesDefaultPreserveCanonicalConstructorAndValidateBounds() {
        GbServerOptions oldCanonical = new GbServerOptions("34020000002000000001", "example.test",
                "127.0.0.1", "127.0.0.1", 5060, Duration.ofDays(1), Duration.ofSeconds(60), 3,
                Duration.ofSeconds(10), 1000, 256, 10000, 65536,
                Duration.ofSeconds(10), Duration.ofSeconds(5), 256, 10000, 256, 256, 256, 256);
        assertEquals(builder().build(), oldCanonical);
        assertEquals(256, oldCanonical.maxMobilePositionSubscriptions());
        assertEquals(256, oldCanonical.maxPendingMobilePositionNotifications());
        assertEquals(1000, oldCanonical.maxMobilePositionItems());

        for (int valid : new int[] {1, 10000}) {
            assertEquals(valid, builder().maxMobilePositionSubscriptions(valid).build()
                    .maxMobilePositionSubscriptions());
            assertEquals(valid, builder().maxPendingMobilePositionNotifications(valid).build()
                    .maxPendingMobilePositionNotifications());
            assertEquals(valid, builder().maxMobilePositionItems(valid).build().maxMobilePositionItems());
        }
        for (int invalid : new int[] {0, -1, 10001, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> builder().maxMobilePositionSubscriptions(invalid).build());
            assertThrows(IllegalArgumentException.class,
                    () -> builder().maxPendingMobilePositionNotifications(invalid).build());
            assertThrows(IllegalArgumentException.class,
                    () -> builder().maxMobilePositionItems(invalid).build());
        }
    }

    @Test
    void catalogSubscriptionCapacitiesPreserveOldConstructorAndValidateBounds() {
        GbServerOptions old = new GbServerOptions("34020000002000000001", "example.test",
                "127.0.0.1", "127.0.0.1", 5060, Duration.ofDays(1), Duration.ofSeconds(60), 3,
                Duration.ofSeconds(10), 1000, 256, 10000, 65536,
                Duration.ofSeconds(10), Duration.ofSeconds(5), 256, 10000, 256, 256);
        assertEquals(builder().build(), old);
        assertEquals(256, old.maxCatalogSubscriptions());
        assertEquals(256, old.maxPendingCatalogNotifications());
        for (int valid : new int[] {1, 10000}) {
            assertEquals(valid, builder().maxCatalogSubscriptions(valid).build().maxCatalogSubscriptions());
            assertEquals(valid, builder().maxPendingCatalogNotifications(valid).build().maxPendingCatalogNotifications());
        }
        for (int invalid : new int[] {0, -1, 10001, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> builder().maxCatalogSubscriptions(invalid).build());
            assertThrows(IllegalArgumentException.class, () -> builder().maxPendingCatalogNotifications(invalid).build());
        }
    }

    @Test
    void recordCapacityDefaultsAndOldConstructorsRemainCompatible() {
        GbServerOptions defaults = builder().build();
        assertEquals(10000, defaults.maxRecordItems());
        assertEquals(256, defaults.maxPendingAlarms());
        assertEquals(256, defaults.maxAlarmSubscriptions());
        assertEquals(defaults, new GbServerOptions("34020000002000000001", "example.test",
                "127.0.0.1", "127.0.0.1", 5060, Duration.ofDays(1), Duration.ofSeconds(60), 3,
                Duration.ofSeconds(10), 1000, 256, 10000, 65536));
        GbServerOptions oldPlaybackOptions = new GbServerOptions("34020000002000000001", "example.test",
                "127.0.0.1", "127.0.0.1", 5060, Duration.ofDays(1), Duration.ofSeconds(60), 3,
                Duration.ofSeconds(10), 1000, 256, 10000, 65536,
                Duration.ofSeconds(12), Duration.ofSeconds(3), 17);
        assertEquals(10000, oldPlaybackOptions.maxRecordItems());
        assertEquals(Duration.ofSeconds(12), oldPlaybackOptions.inviteTimeout());
        assertEquals(Duration.ofSeconds(3), oldPlaybackOptions.stopTimeout());
        assertEquals(17, oldPlaybackOptions.maxPlaySessions());

        GbServerOptions oldRecordOptions = new GbServerOptions("34020000002000000001", "example.test",
                "127.0.0.1", "127.0.0.1", 5060, Duration.ofDays(1), Duration.ofSeconds(60), 3,
                Duration.ofSeconds(10), 1000, 256, 10000, 65536,
                Duration.ofSeconds(12), Duration.ofSeconds(3), 17, 42);
        assertEquals(42, oldRecordOptions.maxRecordItems());
        assertEquals(256, oldRecordOptions.maxPendingAlarms());

        GbServerOptions oldAlarmOptions = new GbServerOptions("34020000002000000001", "example.test",
                "127.0.0.1", "127.0.0.1", 5060, Duration.ofDays(1), Duration.ofSeconds(60), 3,
                Duration.ofSeconds(10), 1000, 256, 10000, 65536,
                Duration.ofSeconds(12), Duration.ofSeconds(3), 17, 42, 23);
        assertEquals(23, oldAlarmOptions.maxPendingAlarms());
        assertEquals(256, oldAlarmOptions.maxAlarmSubscriptions());
    }

    @Test
    void alarmSubscriptionCapacityAcceptsBoundsAndRejectsValuesOutsideThem() {
        assertEquals(1, builder().maxAlarmSubscriptions(1).build().maxAlarmSubscriptions());
        assertEquals(10000, builder().maxAlarmSubscriptions(10000).build().maxAlarmSubscriptions());
        for (int invalid : new int[] {0, -1, 10001, Integer.MAX_VALUE}) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> builder().maxAlarmSubscriptions(invalid).build());
            assertEquals("maxAlarmSubscriptions outside supported range", failure.getMessage());
        }
    }

    @Test
    void alarmCapacityAcceptsBoundsAndRejectsValuesOutsideThem() {
        assertEquals(1, builder().maxPendingAlarms(1).build().maxPendingAlarms());
        assertEquals(10000, builder().maxPendingAlarms(10000).build().maxPendingAlarms());
        for (int invalid : new int[] {0, -1, 10001, Integer.MAX_VALUE}) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> builder().maxPendingAlarms(invalid).build());
            assertEquals("maxPendingAlarms outside supported range", failure.getMessage());
        }
    }

    @Test
    void recordCapacityAcceptsBoundsAndRejectsValuesOutsideThem() {
        assertEquals(1, builder().maxRecordItems(1).build().maxRecordItems());
        assertEquals(100000, builder().maxRecordItems(100000).build().maxRecordItems());
        for (int invalid : new int[] {0, -1, 100001, Integer.MAX_VALUE}) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> builder().maxRecordItems(invalid).build());
            assertEquals("maxRecordItems outside supported range", failure.getMessage());
        }
    }

    private static GbServerOptions.Builder builder() {
        return GbServerOptions.builder().serverId("34020000002000000001").realm("example.test");
    }
}
