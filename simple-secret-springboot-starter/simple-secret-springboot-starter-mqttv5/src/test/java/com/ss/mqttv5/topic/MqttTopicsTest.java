package com.ss.mqttv5.topic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MqttTopicsTest {

    @Test
    void matchesSingleAndMultiLevelWildcards() {
        assertTrue(MqttTopics.matches("devices/+/state", "devices/a/state"));
        assertTrue(MqttTopics.matches("devices/#", "devices/a/state"));
        assertTrue(MqttTopics.matches("devices/+/state", "devices//state"));
        assertFalse(MqttTopics.matches("devices/+", "devices/a/state"));
        assertFalse(MqttTopics.matches("devices/a", "devices/b"));
    }

    @Test
    void wildcardAtRootDoesNotMatchSystemTopic() {
        assertFalse(MqttTopics.matches("#", "$SYS/status"));
        assertFalse(MqttTopics.matches("+/status", "$SYS/status"));
        assertTrue(MqttTopics.matches("$SYS/#", "$SYS/status"));
    }

    @Test
    void validatesFiltersAndPublishTopics() {
        MqttTopics.validateFilter("devices/+/state");
        MqttTopics.validateFilter("devices/#");
        MqttTopics.validateTopic("devices/a/state");

        assertThrows(IllegalArgumentException.class,
                () -> MqttTopics.validateFilter("devices/#/state"));
        assertThrows(IllegalArgumentException.class,
                () -> MqttTopics.validateFilter("devices/a+"));
        assertThrows(IllegalArgumentException.class,
                () -> MqttTopics.validateFilter("devices/#suffix"));
        assertThrows(IllegalArgumentException.class,
                () -> MqttTopics.validateTopic("devices/+"));
        assertThrows(IllegalArgumentException.class,
                () -> MqttTopics.validateTopic(""));
        assertThrows(IllegalArgumentException.class,
                () -> MqttTopics.validateTopic("devices/\u0000/state"));
    }

    @Test
    void createsValidatedSharedSubscription() {
        assertEquals("$share/workers/devices/+",
                MqttTopics.shared("workers", "devices/+"));
        assertThrows(IllegalArgumentException.class,
                () -> MqttTopics.shared("", "devices/+"));
        assertThrows(IllegalArgumentException.class,
                () -> MqttTopics.shared("worker/group", "devices/+"));
        assertThrows(IllegalArgumentException.class,
                () -> MqttTopics.shared("worker+", "devices/+"));
    }

    @Test
    void validatesAndMatchesNativeSharedSubscriptionFilters() {
        MqttTopics.validateFilter("$share/workers/devices/+");
        assertEquals("devices/+", MqttTopics.normalizeFilter("$share/workers/devices/+"));
        assertTrue(MqttTopics.matches("$share/workers/devices/+", "devices/a"));
        assertFalse(MqttTopics.matches("$share/workers/#", "$SYS/status"));

        assertThrows(IllegalArgumentException.class,
                () -> MqttTopics.validateFilter("$share//devices/+"));
        assertThrows(IllegalArgumentException.class,
                () -> MqttTopics.validateFilter("$share/workers"));
        assertThrows(IllegalArgumentException.class,
                () -> MqttTopics.validateFilter("$share/workers/$share/other/devices/+"));
        assertThrows(IllegalArgumentException.class,
                () -> MqttTopics.shared("workers", "$share/other/devices/+"));
    }

    @Test
    void boundsMatchCache() {
        for (int index = 0; index < 1_500; index++) {
            MqttTopics.matches("devices/+", "devices/" + index);
        }

        assertTrue(MqttTopics.cacheSize() <= 1_024);
    }
}
