package com.ss.common.toolbox.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseExpiringCacheManagerTest {
    private final List<TestManager> managers = new ArrayList<>();

    @AfterEach
    void closeManagers() {
        managers.forEach(DatabaseExpiringCacheManager::close);
    }

    @Test
    void updatesStoreOnlyWhenValueChangesAndUpdateIsEnabled() {
        TestManager manager = manager(new AtomicLong());
        List<String> events = new ArrayList<>();
        manager.onValueChanged((key, value) -> events.add("changed:" + value));
        manager.onStoreUpdateSucceeded((key, value) -> events.add("success:" + value));

        manager.put("device", "online", false);
        manager.put("device", "online", true);
        manager.put("device", "offline", true);

        assertEquals(1, manager.updateCalls);
        assertEquals("offline", manager.lastUpdatedValue);
        assertEquals(List.of("changed:online", "changed:offline", "success:offline"), events);
        assertEquals("offline", manager.get("device"));
    }

    @Test
    void reportsFalseStoreUpdatesAndStillRefreshesCache() {
        TestManager manager = manager(new AtomicLong());
        manager.updateResult = false;
        List<String> failures = new ArrayList<>();
        manager.onStoreUpdateFailed((key, value) -> failures.add(key + ":" + value));

        manager.put("device", "offline", true);

        assertEquals(List.of("device:offline"), failures);
        assertEquals("offline", manager.get("device"));
    }

    @Test
    void propagatesStoreExceptionsAndDoesNotPublishUnpersistedValue() {
        TestManager manager = manager(new AtomicLong());
        manager.updateFailure = new IllegalStateException("database unavailable");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> manager.put("device", "offline", true));

        assertEquals("database unavailable", error.getMessage());
        assertNull(manager.get("device"));
    }

    @Test
    void invokesExpirationCallbackOnce() {
        AtomicLong ticker = new AtomicLong();
        TestManager manager = manager(ticker);
        List<String> expired = new ArrayList<>();
        manager.onExpired((key, value) -> expired.add(key + ":" + value));
        manager.put("device", "online", false);

        ticker.set(Duration.ofSeconds(5).toNanos());
        assertNull(manager.get("device"));
        assertNull(manager.get("device"));

        assertEquals(List.of("device:online"), expired);
    }

    @Test
    void rejectsNullKeysAndValuesBeforeUpdatingStore() {
        TestManager manager = manager(new AtomicLong());

        assertThrows(NullPointerException.class,
                () -> manager.put(null, "online", true));
        assertThrows(NullPointerException.class,
                () -> manager.put("device", null, true));

        assertEquals(0, manager.updateCalls);
    }

    private TestManager manager(AtomicLong ticker) {
        TestManager manager = new TestManager(ticker);
        managers.add(manager);
        return manager;
    }

    private static final class TestManager
            extends DatabaseExpiringCacheManager<String, String> {
        private boolean updateResult = true;
        private RuntimeException updateFailure;
        private int updateCalls;
        private String lastUpdatedValue;

        private TestManager(AtomicLong ticker) {
            super(Duration.ofSeconds(5), ValueComparator.natural(), ticker::get);
        }

        @Override
        protected boolean updateStore(String key, String value) {
            updateCalls++;
            lastUpdatedValue = value;
            if (updateFailure != null) {
                throw updateFailure;
            }
            return updateResult;
        }
    }
}
