package com.ss.redis.operation;

import com.ss.redis.exception.RedisLockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RKeys;
import org.redisson.api.RLock;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RTopic;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.redisson.api.options.KeysScanOptions;
import org.redisson.api.options.KeysScanParams;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class RedissonOperationsCoordinationTest {

    private RedissonClient client;
    private RLock lock;
    private RRateLimiter limiter;
    private RTopic topic;
    private RKeys keys;
    private RedissonOperations operations;

    @BeforeEach
    void setUp() {
        client = mock(RedissonClient.class);
        lock = mock(RLock.class);
        limiter = mock(RRateLimiter.class);
        topic = mock(RTopic.class);
        keys = mock(RKeys.class);
        operations = new RedissonOperations(client);
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void usesWatchdogLockAndUnlocksOnlyWhenHeldByCurrentThread() throws InterruptedException {
        when(client.getLock("order:42")).thenReturn(lock);
        when(lock.tryLock(500, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = operations.withLock(
                "order:42", Duration.ofMillis(500), () -> "completed");

        assertThat(result).isEqualTo("completed");
        verify(lock).tryLock(500, TimeUnit.MILLISECONDS);
        verify(lock).unlock();
    }

    @Test
    void usesExplicitLeaseAndSkipsUnlockWhenOwnershipWasLost() throws InterruptedException {
        when(client.getLock("order:42")).thenReturn(lock);
        when(lock.tryLock(500, 2_000, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(false);

        operations.withLock(
                "order:42", Duration.ofMillis(500), Duration.ofSeconds(2), () -> "completed");

        verify(lock).tryLock(500, 2_000, TimeUnit.MILLISECONDS);
        verify(lock, never()).unlock();
    }

    @Test
    void rejectsUnavailableLockWithoutExecutingAction() throws InterruptedException {
        AtomicReference<String> execution = new AtomicReference<>();
        when(client.getLock("order:42")).thenReturn(lock);
        when(lock.tryLock(500, TimeUnit.MILLISECONDS)).thenReturn(false);

        assertThatThrownBy(() -> operations.withLock(
                "order:42", Duration.ofMillis(500), () -> execution.getAndSet("ran")))
                .isInstanceOf(RedisLockException.class)
                .hasMessageContaining("order:42");

        assertThat(execution).hasValue(null);
        verify(lock, never()).unlock();
    }

    @Test
    void restoresInterruptFlagWhenLockWaitIsInterrupted() throws InterruptedException {
        InterruptedException interrupted = new InterruptedException("interrupted");
        when(client.getLock("order:42")).thenReturn(lock);
        when(lock.tryLock(500, TimeUnit.MILLISECONDS)).thenThrow(interrupted);

        assertThatThrownBy(() -> operations.withLock(
                "order:42", Duration.ofMillis(500), () -> "unused"))
                .isInstanceOf(RedisLockException.class)
                .hasCause(interrupted);

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void propagatesActionFailureAndStillUnlocks() throws InterruptedException {
        IllegalStateException actionFailure = new IllegalStateException("business failure");
        when(client.getLock("order:42")).thenReturn(lock);
        when(lock.tryLock(500, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        assertThatThrownBy(() -> operations.withLock(
                "order:42", Duration.ofMillis(500), () -> {
                    throw actionFailure;
                }))
                .isSameAs(actionFailure);

        verify(lock).unlock();
    }

    @Test
    void reportsUnlockFailureWithoutTryingToUnlockTwice() throws InterruptedException {
        RuntimeException unlockFailure = new RuntimeException("unlock failed");
        when(client.getLock("order:42")).thenReturn(lock);
        when(lock.tryLock(500, TimeUnit.MILLISECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        doThrow(unlockFailure).when(lock).unlock();

        assertThatThrownBy(() -> operations.withLock(
                "order:42", Duration.ofMillis(500), () -> "completed"))
                .isInstanceOf(RedisLockException.class)
                .hasCause(unlockFailure);

        verify(lock, times(1)).unlock();
    }

    @Test
    void configuresRateLimiterAndAttemptsOnePermit() {
        Duration interval = Duration.ofSeconds(1);
        when(client.getRateLimiter("api:login")).thenReturn(limiter);
        when(limiter.trySetRate(RateType.OVERALL, 10, interval)).thenReturn(true);
        when(limiter.tryAcquire()).thenReturn(true);

        assertThat(operations.tryAcquire("api:login", RateType.OVERALL, 10, interval)).isTrue();

        verify(limiter).trySetRate(RateType.OVERALL, 10, interval);
        verify(limiter).tryAcquire();
    }

    @Test
    void publishesAndRemovesExactlyTheCreatedSubscription() {
        when(client.getTopic("events")).thenReturn(topic);
        when(topic.publish("created")).thenReturn(3L);
        when(topic.addListener(eq(String.class), any(MessageListener.class))).thenReturn(17);
        ArgumentCaptor<MessageListener> listenerCaptor = ArgumentCaptor.forClass(MessageListener.class);
        AtomicReference<String> received = new AtomicReference<>();

        assertThat(operations.publish("events", "created")).isEqualTo(3L);
        RedisSubscription subscription = operations.subscribe(
                "events", String.class, received::set);
        verify(topic).addListener(eq(String.class), listenerCaptor.capture());
        listenerCaptor.getValue().onMessage("events", "payload");

        assertThat(received).hasValue("payload");
        subscription.close();
        subscription.close();
        verify(topic, times(1)).removeListener(17);
    }

    @Test
    void scansWithServerSideLimitAndDeletesOnlyExplicitPattern() {
        when(client.getKeys()).thenReturn(keys);
        when(keys.getKeys(any(KeysScanOptions.class))).thenReturn(List.of("user:1", "user:2"));
        when(keys.deleteByPattern("expired:*")).thenReturn(4L);

        assertThat(operations.scanKeys("user:*", 2)).containsExactly("user:1", "user:2");
        assertThat(operations.deleteByPattern("expired:*")).isEqualTo(4L);

        ArgumentCaptor<KeysScanOptions> optionsCaptor = ArgumentCaptor.forClass(KeysScanOptions.class);
        verify(keys).getKeys(optionsCaptor.capture());
        assertThat(optionsCaptor.getValue()).isInstanceOfSatisfying(KeysScanParams.class, options -> {
            assertThat(options.getPattern()).isEqualTo("user:*");
            assertThat(options.getLimit()).isEqualTo(2);
        });
        verify(keys).deleteByPattern("expired:*");
    }

    @Test
    void validatesCoordinationArgumentsBeforeCallingRedisson() {
        assertThatThrownBy(() -> operations.withLock("lock", Duration.ZERO, () -> "unused"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> operations.tryAcquire(
                "rate", RateType.OVERALL, 0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> operations.tryAcquire(
                "rate", RateType.OVERALL, 1, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> operations.subscribe("events", String.class, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> operations.scanKeys("*", 0))
                .isInstanceOf(IllegalArgumentException.class);

        verify(client, never()).getLock("lock");
        verify(client, never()).getRateLimiter("rate");
        verify(client, never()).getTopic("events");
    }

    @Test
    void wrapsSubscriptionRemovalFailureWithoutLeakingCauseMessage() {
        RuntimeException cause = new RuntimeException("password=server-secret");
        when(client.getTopic("events")).thenReturn(topic);
        when(topic.addListener(eq(String.class), any(MessageListener.class))).thenReturn(17);
        doThrow(cause).when(topic).removeListener(17);
        RedisSubscription subscription = operations.subscribe(
                "events", String.class, ignored -> { });

        assertThatThrownBy(subscription::close)
                .hasMessageContaining("unsubscribe")
                .hasMessageNotContaining("server-secret")
                .hasCause(cause);
    }
}
