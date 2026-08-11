package com.ss.idempotent.store;

import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Redisson 存储使用 TTL 获取并只释放自己的租约。 */
class RedissonIdempotencyStoreTest {

    @Test
    void shouldAcquireWithTtlAndReleaseByOwner() {
        RedissonClient client = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        when(client.<String>getBucket("key-1")).thenReturn(bucket);
        when(bucket.setIfAbsent("owner-1", Duration.ofSeconds(5))).thenReturn(true);
        when(bucket.compareAndSet("owner-1", null)).thenReturn(true);
        RedissonIdempotencyStore store = new RedissonIdempotencyStore(client);

        assertThat(store.tryAcquire("key-1", "owner-1", Duration.ofSeconds(5))).isTrue();
        assertThat(store.release("key-1", "owner-1")).isTrue();

        verify(bucket).setIfAbsent("owner-1", Duration.ofSeconds(5));
        verify(bucket).compareAndSet("owner-1", null);
    }
}
