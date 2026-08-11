package com.ss.redis.operation;

import com.ss.redis.exception.RedisOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RList;
import org.redisson.api.RMap;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedissonOperationsDataTest {

    private RedissonClient client;
    private RBucket<Object> bucket;
    private RList<Object> list;
    private RSet<Object> set;
    private RMap<Object, Object> map;
    private RAtomicLong atomicLong;
    private RedissonOperations operations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        client = mock(RedissonClient.class);
        bucket = mock(RBucket.class);
        list = mock(RList.class);
        set = mock(RSet.class);
        map = mock(RMap.class);
        atomicLong = mock(RAtomicLong.class);
        operations = new RedissonOperations(client);
    }

    @Test
    void delegatesBucketOperationsToTheSelectedKey() {
        Duration ttl = Duration.ofSeconds(30);
        when(client.<Object>getBucket("session")).thenReturn(bucket);
        when(bucket.setIfAbsent("first")).thenReturn(true);
        when(bucket.setIfAbsent("second", ttl)).thenReturn(false);
        when(bucket.setIfExists("third")).thenReturn(true);
        when(bucket.setIfExists("fourth", ttl)).thenReturn(false);
        when(bucket.get()).thenReturn("stored");
        when(bucket.delete()).thenReturn(true);
        when(bucket.isExists()).thenReturn(true);
        when(bucket.expire(ttl)).thenReturn(true);
        when(bucket.remainTimeToLive()).thenReturn(12_000L);

        operations.set("session", "plain");
        operations.set("session", "expiring", ttl);
        operations.setKeepingTtl("session", "replacement");

        assertThat(operations.setIfAbsent("session", "first")).isTrue();
        assertThat(operations.setIfAbsent("session", "second", ttl)).isFalse();
        assertThat(operations.setIfExists("session", "third")).isTrue();
        assertThat(operations.setIfExists("session", "fourth", ttl)).isFalse();
        assertThat(operations.get("session", String.class)).isEqualTo("stored");
        assertThat(operations.delete("session")).isTrue();
        assertThat(operations.exists("session")).isTrue();
        assertThat(operations.expire("session", ttl)).isTrue();
        assertThat(operations.ttl("session")).isEqualTo(Duration.ofSeconds(12));

        verify(bucket).set("plain");
        verify(bucket).set("expiring", ttl);
        verify(bucket).setAndKeepTTL("replacement");
    }

    @Test
    void returnsImmutableCollectionSnapshots() {
        List<Object> listSource = new ArrayList<>(List.of("a", "b"));
        List<Object> rangeSource = new ArrayList<>(List.of("b"));
        Set<Object> setSource = new HashSet<>(Set.of("x", "y"));
        Map<Object, Object> mapSource = new HashMap<>(Map.of("one", 1));
        when(client.<Object>getList("items")).thenReturn(list);
        when(client.<Object>getSet("tags")).thenReturn(set);
        when(client.<Object, Object>getMap("attributes")).thenReturn(map);
        when(list.readAll()).thenReturn(listSource);
        when(list.range(1, 1)).thenReturn(rangeSource);
        when(set.readAll()).thenReturn(setSource);
        when(map.readAllMap()).thenReturn(mapSource);

        List<String> listSnapshot = operations.getList("items", String.class);
        List<String> rangeSnapshot = operations.getListRange("items", 1, 1, String.class);
        Set<String> setSnapshot = operations.getSet("tags", String.class);
        Map<String, Integer> mapSnapshot = operations.getMap("attributes", String.class, Integer.class);
        listSource.add("c");
        rangeSource.add("c");
        setSource.add("z");
        mapSource.put("two", 2);

        assertThat(listSnapshot).containsExactly("a", "b");
        assertThat(rangeSnapshot).containsExactly("b");
        assertThat(setSnapshot).containsExactlyInAnyOrder("x", "y");
        assertThat(mapSnapshot).containsExactly(Map.entry("one", 1));
        assertThatThrownBy(() -> listSnapshot.add("c")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> setSnapshot.add("z")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> mapSnapshot.put("two", 2)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void delegatesListSetAndMapMutations() {
        when(client.<Object>getList("items")).thenReturn(list);
        when(client.<Object>getSet("tags")).thenReturn(set);
        when(client.<Object, Object>getMap("attributes")).thenReturn(map);
        when(list.add("a")).thenReturn(true);
        when(list.addAll(List.of("b", "c"))).thenReturn(true);
        when(list.get(1)).thenReturn("b");
        when(list.set(1, "updated")).thenReturn("b");
        when(list.remove("a")).thenReturn(true);
        when(set.add("x")).thenReturn(true);
        when(set.addAll(Set.of("y", "z"))).thenReturn(true);
        when(set.remove("x")).thenReturn(true);
        when(map.put("one", 1)).thenReturn(0);
        when(map.get("one")).thenReturn(1);
        when(map.remove("one")).thenReturn(1);
        when(map.containsKey("one")).thenReturn(true);

        assertThat(operations.addToList("items", "a")).isTrue();
        assertThat(operations.addAllToList("items", List.of("b", "c"))).isTrue();
        assertThat(operations.getListValue("items", 1, String.class)).isEqualTo("b");
        assertThat(operations.setListValue("items", 1, "updated", String.class)).isEqualTo("b");
        assertThat(operations.removeFromList("items", "a")).isTrue();
        assertThat(operations.addToSet("tags", "x")).isTrue();
        assertThat(operations.addAllToSet("tags", Set.of("y", "z"))).isTrue();
        assertThat(operations.removeFromSet("tags", "x")).isTrue();
        assertThat(operations.putToMap("attributes", "one", 1, Integer.class)).isZero();
        operations.putAllToMap("attributes", Map.of("two", 2));
        assertThat(operations.getFromMap("attributes", "one", Integer.class)).isEqualTo(1);
        assertThat(operations.removeFromMap("attributes", "one", Integer.class)).isEqualTo(1);
        assertThat(operations.containsMapKey("attributes", "one")).isTrue();

        verify(map).putAll(Map.of("two", 2));
    }

    @Test
    void treatsEmptyBulkInputsAsNoOps() {
        assertThat(operations.addAllToList("items", List.of())).isFalse();
        assertThat(operations.addAllToSet("tags", Set.of())).isFalse();
        operations.putAllToMap("attributes", Map.of());

        verify(client, never()).getList("items");
        verify(client, never()).getSet("tags");
        verify(client, never()).getMap("attributes");
    }

    @Test
    void delegatesAtomicLongOperations() {
        when(client.getAtomicLong("sequence")).thenReturn(atomicLong);
        when(atomicLong.get()).thenReturn(8L);
        when(atomicLong.incrementAndGet()).thenReturn(9L);
        when(atomicLong.decrementAndGet()).thenReturn(7L);

        operations.setAtomicLong("sequence", 8L);

        assertThat(operations.getAtomicLong("sequence")).isEqualTo(8L);
        assertThat(operations.incrementAtomicLong("sequence")).isEqualTo(9L);
        assertThat(operations.decrementAtomicLong("sequence")).isEqualTo(7L);
        verify(atomicLong).set(8L);
    }

    @Test
    void validatesKeysValuesDurationsAndRangesBeforeCallingRedisson() {
        assertThatThrownBy(() -> operations.get(" ", Object.class))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> operations.set("key", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> operations.set("key", "value", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> operations.expire("key", Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> operations.getListRange("items", -1, 2, Object.class))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> operations.getListRange("items", 3, 2, Object.class))
                .isInstanceOf(IllegalArgumentException.class);

        verify(client, never()).getBucket("key");
        verify(client, never()).getList("items");
    }

    @Test
    void wrapsRedissonFailuresWithoutLeakingValuesOrCauseMessages() {
        RuntimeException cause = new RuntimeException("password=server-secret");
        when(client.<Object>getBucket("session")).thenReturn(bucket);
        when(bucket.get()).thenThrow(cause);

        assertThatThrownBy(() -> operations.get("session", String.class))
                .isInstanceOf(RedisOperationException.class)
                .hasMessageContaining("get")
                .hasMessageContaining("session")
                .hasMessageNotContaining("server-secret")
                .hasCause(cause);
    }
}
