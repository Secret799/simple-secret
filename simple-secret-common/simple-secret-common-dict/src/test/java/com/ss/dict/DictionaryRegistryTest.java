package com.ss.dict;

import com.ss.dict.model.DictElement;
import com.ss.dict.model.DictScope;
import com.ss.dict.model.DictValue;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证显式字典注册、查询和缓存的一致行为。 */
class DictionaryRegistryTest {

    @Test
    void registersExplicitSourcesAndEnumSources() {
        try (DictionaryRegistry registry = new DictionaryRegistry(Duration.ofMinutes(1))) {
            registry.register(" status ", () -> List.of(value("1", "启用", "system")));
            registry.registerEnum("level", Level.class);

            assertEquals("启用", registry.query("status").get(0).label());
            assertEquals(List.of("H", "L"), registry.query("level").stream()
                    .map(DictElement::code).toList());
            assertThrows(IllegalStateException.class,
                    () -> registry.register("status", List::of));
        }
    }

    @Test
    void rejectsNonEnumTypesAtRegistrationTime() {
        try (DictionaryRegistry registry = new DictionaryRegistry(Duration.ofMinutes(1))) {
            assertThrows(IllegalArgumentException.class,
                    () -> registry.registerEnum("invalid", NonEnumValue.class));
        }
    }

    @Test
    void returnsImmutableSnapshotsAndSupportsTypeAndElementQueries() {
        try (DictionaryRegistry registry = registryWithStatuses(new AtomicInteger())) {
            List<DictElement> elements = registry.query("status", "system");

            assertEquals(2, elements.size());
            assertThrows(UnsupportedOperationException.class,
                    () -> elements.add(value("2", "未知", "system")));
            assertEquals("启用", registry.find("status", "1").label());
            assertEquals("禁用", registry.find("status", "system", "0").label());
            assertNull(registry.find("status", "other", "0"));

            Map<String, List<DictElement>> all = registry.queryAll(List.of("status"));
            assertEquals("启用", all.get("status").get(0).label());
            assertThrows(UnsupportedOperationException.class,
                    () -> all.put("other", List.of()));
        }
    }

    @Test
    void cachesLoadsUntilInvalidated() {
        AtomicInteger loads = new AtomicInteger();
        try (DictionaryRegistry registry = registryWithStatuses(loads)) {
            registry.queryCached("status");
            registry.queryCached("status");
            assertEquals(1, loads.get());

            registry.invalidate("status");
            registry.queryCached("status");
            assertEquals(2, loads.get());

            registry.clearCache();
            registry.queryCached("status");
            assertEquals(3, loads.get());
        }
    }

    @Test
    void honorsPerCallTtl() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        try (DictionaryRegistry registry = registryWithStatuses(loads)) {
            registry.queryCached("status", Duration.ofMillis(5));
            long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            while (loads.get() == 1 && System.nanoTime() < deadline) {
                Thread.sleep(10);
                registry.queryCached("status", Duration.ofMillis(5));
            }

            assertTrue(loads.get() >= 2, "cache entry should expire using the supplied TTL");
        }
    }

    @Test
    void loadsOneValuePerKeyUnderConcurrency() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        try (DictionaryRegistry registry = new DictionaryRegistry(Duration.ofMinutes(1))) {
            registry.register("status", () -> {
                loads.incrementAndGet();
                return List.of(value("1", "启用", "system"));
            });
            ExecutorService executor = Executors.newFixedThreadPool(8);
            try {
                List<Future<List<DictElement>>> futures = new ArrayList<>();
                for (int index = 0; index < 8; index++) {
                    futures.add(executor.submit(() -> {
                        start.await();
                        return registry.queryCached("status");
                    }));
                }
                start.countDown();
                for (Future<List<DictElement>> future : futures) {
                    assertEquals("启用", future.get().get(0).label());
                }
                assertEquals(1, loads.get());
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void doesNotCacheFailuresOrInvalidSourceResults() {
        AtomicInteger loads = new AtomicInteger();
        try (DictionaryRegistry registry = new DictionaryRegistry(Duration.ofMinutes(1))) {
            registry.register("unstable", () -> {
                if (loads.incrementAndGet() == 1) {
                    throw new IllegalStateException("database unavailable");
                }
                return List.of(value("1", "启用", "system"));
            });

            assertThrows(IllegalStateException.class, () -> registry.queryCached("unstable"));
            assertEquals("启用", registry.queryCached("unstable").get(0).label());
            assertEquals(2, loads.get());
        }

        try (DictionaryRegistry registry = new DictionaryRegistry(Duration.ofMinutes(1))) {
            registry.register("null-list", () -> null);
            assertThrows(NullPointerException.class, () -> registry.query("null-list"));
        }
    }

    private static DictionaryRegistry registryWithStatuses(AtomicInteger loads) {
        DictionaryRegistry registry = new DictionaryRegistry(Duration.ofMinutes(1));
        registry.register("status", () -> {
            loads.incrementAndGet();
            return List.of(value("1", "启用", "system"), value("0", "禁用", "system"));
        });
        return registry;
    }

    private static DictElement value(String code, String label, String type) {
        return new DictElement(DictScope.GLOBAL, "global", code, label, type);
    }

    private enum Level implements DictValue {
        HIGH("H", "高"),
        LOW("L", "低");

        private final String code;
        private final String label;

        Level(String code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public String getDictCode() {
            return code;
        }

        @Override
        public String getDictLabel() {
            return label;
        }
    }

    private static final class NonEnumValue implements DictValue {
        @Override
        public String getDictCode() {
            return "1";
        }

        @Override
        public String getDictLabel() {
            return "无效";
        }
    }
}
