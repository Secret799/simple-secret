package com.ss.application.pushstream.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * 推流扫描生命周期测试。
 *
 * @author junpzx
 * @since 2026-08-12
 */
class PublishStreamSchedulerTest {

    @Test
    void shouldCompleteStopCallbackAfterRunningSynchronizationFinishes() throws Exception {
        CountDownLatch synchronizationStarted = new CountDownLatch(1);
        CountDownLatch allowSynchronizationToFinish = new CountDownLatch(1);
        PublishStreamService service = blockingService(synchronizationStarted, allowSynchronizationToFinish);
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        PublishStreamScheduler scheduler = new PublishStreamScheduler(service, executor, Duration.ofHours(1));
        AtomicBoolean stopped = new AtomicBoolean();

        try {
            scheduler.start();
            assertThat(synchronizationStarted.await(1, TimeUnit.SECONDS)).isTrue();

            scheduler.stop(() -> stopped.set(true));

            assertThat(stopped).isFalse();
            allowSynchronizationToFinish.countDown();
            awaitStopCallback(stopped);
        } finally {
            allowSynchronizationToFinish.countDown();
            executor.shutdownNow();
        }
    }

    private PublishStreamService blockingService(CountDownLatch started, CountDownLatch finish) throws IOException {
        PublishStreamService service = mock(PublishStreamService.class);
        doAnswer(invocation -> {
            started.countDown();
            finish.await(1, TimeUnit.SECONDS);
            return null;
        }).when(service).synchronize();
        return service;
    }

    private void awaitStopCallback(AtomicBoolean stopped) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (!stopped.get() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertThat(stopped).isTrue();
    }
}
