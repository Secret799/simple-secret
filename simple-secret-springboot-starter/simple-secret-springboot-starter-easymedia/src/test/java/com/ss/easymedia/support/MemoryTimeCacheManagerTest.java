package com.ss.easymedia.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryTimeCacheManagerTest {

    @Test
    void zeroTimeoutShouldKeepEntriesUntilExplicitRemoval() throws Exception {
        MemoryTimeCacheManager<String, String> cache = new MemoryTimeCacheManager<>(0L);

        assertThat(cache.get("stream", false, () -> "first")).isEqualTo("first");
        Thread.sleep(5L);

        assertThat(cache.get("stream", false, () -> "second")).isEqualTo("first");
        cache.destroy();
    }
}
