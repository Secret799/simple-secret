package com.ss.common.toolbox.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpiringCacheTest {
    private final List<ExpiringCache<?, ?>> caches = new ArrayList<>();

    @AfterEach
    void closeCaches() {
        caches.forEach(ExpiringCache::close);
    }

    @Test
    void expiresEntriesLazilyAndReportsCauseOnce() {
        AtomicLong ticker = new AtomicLong();
        ExpiringCache<String, String> cache = cache(
                new ExpiringCache<>(Duration.ofSeconds(5), ticker::get));
        List<CacheRemovalCause> causes = new ArrayList<>();
        cache.addRemovalListener((key, value, cause) -> causes.add(cause));

        cache.put("device", "online");
        ticker.set(Duration.ofSeconds(4).toNanos());
        assertEquals("online", cache.get("device"));

        ticker.set(Duration.ofSeconds(5).toNanos());
        assertNull(cache.get("device"));
        assertNull(cache.get("device"));
        assertEquals(List.of(CacheRemovalCause.EXPIRED), causes);
        assertFalse(cache.containsKey("device"));
    }

    @Test
    void reportsReplacementAndExplicitRemovalWithoutBreakingOnListenerFailure() {
        ExpiringCache<String, String> cache = cache(
                new ExpiringCache<>(Duration.ofMinutes(1)));
        List<CacheRemovalCause> causes = new ArrayList<>();
        cache.addRemovalListener((key, value, cause) -> {
            throw new IllegalStateException("listener failure");
        });
        cache.addRemovalListener((key, value, cause) -> causes.add(cause));

        cache.put("device", "online");
        cache.put("device", "offline");
        assertEquals("offline", cache.remove("device"));

        assertEquals(List.of(CacheRemovalCause.REPLACED, CacheRemovalCause.EXPLICIT), causes);
    }

    @Test
    void computesOneValuePerKeyUnderConcurrency() throws Exception {
        ExpiringCache<String, String> cache = cache(
                new ExpiringCache<>(Duration.ofMinutes(1)));
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return cache.computeIfAbsent("device", key -> {
                        loads.incrementAndGet();
                        return "online";
                    });
                }));
            }

            start.countDown();
            for (Future<String> future : futures) {
                assertEquals("online", future.get());
            }
            assertEquals(1, loads.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void doesNotCacheNullLoaderResultsAndRejectsInvalidDurations() {
        ExpiringCache<String, String> cache = cache(
                new ExpiringCache<>(Duration.ofMinutes(1)));
        AtomicInteger loads = new AtomicInteger();

        assertNull(cache.computeIfAbsent("missing", key -> {
            loads.incrementAndGet();
            return null;
        }));
        assertNull(cache.computeIfAbsent("missing", key -> {
            loads.incrementAndGet();
            return null;
        }));

        assertEquals(2, loads.get());
        assertThrows(IllegalArgumentException.class,
                () -> new ExpiringCache<>(Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> cache.put("device", "online", Duration.ZERO));
    }

    @Test
    void closesScheduledCleanupResourceIdempotently() {
        ExpiringCache<String, String> cache = cache(
                new ExpiringCache<>(Duration.ofMinutes(1)));

        cache.scheduleCleanup(Duration.ofSeconds(1));
        assertTrue(cache.isCleanupScheduled());
        cache.close();
        cache.close();

        assertFalse(cache.isCleanupScheduled());
        assertThrows(IllegalStateException.class,
                () -> cache.scheduleCleanup(Duration.ofSeconds(1)));
    }

    private <K, V> ExpiringCache<K, V> cache(ExpiringCache<K, V> cache) {
        caches.add(cache);
        return cache;
    }
}
