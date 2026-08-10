package com.ss.easymedia.webrtc.repository;

import com.ss.easymedia.webrtc.domain.WebRtcSessionRecord;
import com.ss.easymedia.webrtc.domain.WebRtcSessionState;
import com.ss.easymedia.webrtc.domain.WebRtcSessionType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryWebRtcSessionRepositoryTest {

    @Test
    void findRemovesExpiredSessionFromStore() throws Exception {
        InMemoryWebRtcSessionRepository repository = new InMemoryWebRtcSessionRepository();
        repository.create(record("sid"), Duration.ofMillis(1));
        Thread.sleep(10);

        assertTrue(repository.find("sid").isEmpty());
        assertTrue(store(repository).isEmpty());
    }

    @Test
    void waitingThreadKeepsSessionLockRegisteredUntilItFinishes() throws Exception {
        InMemoryWebRtcSessionRepository repository = new InMemoryWebRtcSessionRepository();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        CountDownLatch thirdEntered = new CountDownLatch(1);

        try {
            Future<?> first = executor.submit(() -> repository.withSessionLock("sid", () -> {
                firstEntered.countDown();
                await(releaseFirst);
                return null;
            }));
            assertTrue(firstEntered.await(1, TimeUnit.SECONDS));

            Future<?> second = executor.submit(() -> repository.withSessionLock("sid", () -> {
                secondEntered.countDown();
                await(releaseSecond);
                return null;
            }));
            awaitQueuedThread(repository, "sid");
            releaseFirst.countDown();
            assertTrue(secondEntered.await(1, TimeUnit.SECONDS));
            first.get(1, TimeUnit.SECONDS);

            Future<?> third = executor.submit(() -> repository.withSessionLock("sid", () -> {
                thirdEntered.countDown();
                return null;
            }));

            assertFalse(thirdEntered.await(200, TimeUnit.MILLISECONDS));
            releaseSecond.countDown();
            second.get(1, TimeUnit.SECONDS);
            third.get(1, TimeUnit.SECONDS);
            assertTrue(thirdEntered.await(1, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            releaseSecond.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS));
        }
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentMap<String, ?> store(InMemoryWebRtcSessionRepository repository) throws Exception {
        Field field = InMemoryWebRtcSessionRepository.class.getDeclaredField("store");
        field.setAccessible(true);
        return (ConcurrentMap<String, ?>) field.get(repository);
    }

    private static void awaitQueuedThread(InMemoryWebRtcSessionRepository repository, String sessionId)
            throws Exception {
        Field field = InMemoryWebRtcSessionRepository.class.getDeclaredField("locks");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        ConcurrentMap<String, ?> locks = (ConcurrentMap<String, ?>) field.get(repository);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline) {
            Object entry = locks.get(sessionId);
            ReentrantLock lock = sessionLock(entry);
            if (lock != null && lock.hasQueuedThreads()) {
                return;
            }
            Thread.sleep(1);
        }
        throw new AssertionError("second thread did not queue for the session lock");
    }

    private static ReentrantLock sessionLock(Object entry) throws Exception {
        if (entry == null) {
            return null;
        }
        if (entry instanceof ReentrantLock lock) {
            return lock;
        }
        Field field = entry.getClass().getDeclaredField("lock");
        field.setAccessible(true);
        return (ReentrantLock) field.get(entry);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static WebRtcSessionRecord record(String sessionId) {
        return new WebRtcSessionRecord(
                sessionId, WebRtcSessionType.WHEP, WebRtcSessionState.ACTIVE,
                "tenant", "subject", "live", "cam-01", "local-zlm",
                null, null, 1L, 1L, 0L, 0);
    }
}
