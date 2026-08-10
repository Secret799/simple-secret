package com.ss.easymedia.webrtc.repository;

import com.ss.easymedia.webrtc.domain.WebRtcSessionRecord;
import com.ss.easymedia.webrtc.domain.WebRtcSessionState;
import com.ss.easymedia.webrtc.domain.WebRtcSessionType;
import com.ss.easymedia.webrtc.exception.WebRtcSessionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedissonWebRtcSessionRepositoryTest {

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RBucket<WebRtcSessionRecord> bucket;
    @Mock
    private RLock lock;
    @Mock
    private RScoredSortedSet<String> closingSet;

    private WebRtcRedisKeys keys;
    private RedissonWebRtcSessionRepository repository;
    private WebRtcSessionRecord record;

    @BeforeEach
    void setUp() {
        keys = new WebRtcRedisKeys();
        repository = new RedissonWebRtcSessionRepository(redissonClient, keys);
        record = record("sid");
    }

    @Test
    void shouldCreateSessionWithoutOverwritingExistingRecord() {
        when(redissonClient.<WebRtcSessionRecord>getBucket(keys.session("sid"))).thenReturn(bucket);
        when(bucket.setIfAbsent(any(WebRtcSessionRecord.class), any(Duration.class))).thenReturn(true);

        assertTrue(repository.create(record, Duration.ofHours(1)));
        verify(bucket).setIfAbsent(any(WebRtcSessionRecord.class), any(Duration.class));
        verify(bucket, never()).set(any(WebRtcSessionRecord.class));
    }

    @Test
    void shouldReturnCopyAndPreserveTtlWhenSavingMetadata() {
        when(redissonClient.<WebRtcSessionRecord>getBucket(keys.session("sid"))).thenReturn(bucket);
        when(bucket.get()).thenReturn(record);
        when(bucket.remainTimeToLive()).thenReturn(Duration.ofSeconds(30).toMillis());
        when(bucket.setIfExists(any(WebRtcSessionRecord.class), any(Duration.class))).thenReturn(true);

        Optional<WebRtcSessionRecord> found = repository.find("sid");
        assertTrue(found.isPresent());
        assertNotSame(record, found.orElseThrow());

        assertTrue(repository.savePreservingTtl(record));
        verify(bucket).setIfExists(any(WebRtcSessionRecord.class), eq(Duration.ofSeconds(30)));
    }

    @Test
    void shouldNotSaveMetadataWhenSessionNoLongerExists() {
        when(redissonClient.<WebRtcSessionRecord>getBucket(keys.session("sid"))).thenReturn(bucket);
        when(bucket.remainTimeToLive()).thenReturn(-2L);

        assertFalse(repository.savePreservingTtl(record));
        verify(bucket, never()).setIfExists(any(), any(Duration.class));
    }

    @Test
    void shouldExecuteActionUnderSessionLock() throws Exception {
        when(redissonClient.getLock(keys.sessionLock("sid"))).thenReturn(lock);
        when(lock.tryLock(2, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = repository.withSessionLock("sid", () -> "done");

        assertEquals("done", result);
        verify(lock).unlock();
    }

    @Test
    void shouldReturnServiceUnavailableWhenSessionLockTimesOut() throws Exception {
        when(redissonClient.getLock(keys.sessionLock("sid"))).thenReturn(lock);
        when(lock.tryLock(2, TimeUnit.SECONDS)).thenReturn(false);

        WebRtcSessionException error = assertThrows(WebRtcSessionException.class,
                () -> repository.withSessionLock("sid", () -> "never"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatus());
        assertEquals("WEBRTC_SESSION_BUSY", error.getErrorCode());
        verify(lock, never()).unlock();
    }

    @Test
    void shouldPollClosingRetriesInScoreOrderWithLimit() {
        when(redissonClient.<String>getScoredSortedSet(keys.closingIndex())).thenReturn(closingSet);
        Collection<String> values = List.of("sid-1", "sid-2");
        when(closingSet.valueRange(Double.NEGATIVE_INFINITY, true, 1234D, true, 0, 2))
                .thenReturn(values);

        assertEquals(List.of("sid-1", "sid-2"),
                repository.pollClosingRetries(Instant.ofEpochMilli(1234), 2));
    }

    @Test
    void shouldDeleteSessionAndClosingIndex() {
        when(redissonClient.<WebRtcSessionRecord>getBucket(keys.session("sid"))).thenReturn(bucket);
        when(redissonClient.<String>getScoredSortedSet(keys.closingIndex())).thenReturn(closingSet);
        when(bucket.delete()).thenReturn(true);

        assertTrue(repository.delete("sid"));
        verify(closingSet).remove("sid");
    }

    @Test
    void shouldHashSensitiveRedisKeyParts() {
        String key = keys.rateLimit(
                com.ss.easymedia.webrtc.domain.WebRtcOperation.PLAY,
                "tenant-a:user-1", "2001:db8::1");

        assertFalse(key.contains("tenant-a"));
        assertFalse(key.contains("user-1"));
        assertFalse(key.contains("2001:db8"));
        assertTrue(key.startsWith("ems:webrtc:rate:play:"));
    }

    private static WebRtcSessionRecord record(String sessionId) {
        return new WebRtcSessionRecord(
                sessionId, WebRtcSessionType.WHEP, WebRtcSessionState.ACTIVE,
                "tenant", "subject", "live", "cam-01", "local-zlm",
                "http://127.0.0.1:7080/index/api/whep/upstream", "\"v1\"",
                1L, 1L, 0L, 0);
    }
}
