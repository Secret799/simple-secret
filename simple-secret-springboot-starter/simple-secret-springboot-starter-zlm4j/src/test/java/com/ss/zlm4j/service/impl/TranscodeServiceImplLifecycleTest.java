package com.ss.zlm4j.service.impl;

import com.aizuda.zlm4j.core.ZLMApi;
import com.ss.zlm4j.context.TranscodeContext;
import com.ss.zlm4j.security.MediaResourcePolicy;
import com.ss.zlm4j.service.domain.bo.TranscodeBO;
import jakarta.annotation.PreDestroy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
class TranscodeServiceImplLifecycleTest {

    private final ExecutorService taskExecutor = Executors.newCachedThreadPool();
    private final ExecutorService callers = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() {
        callers.shutdownNow();
        taskExecutor.shutdownNow();
    }

    @Test
    void shouldAtomicallyRejectConcurrentDuplicateTasks() throws Exception {
        CyclicBarrier factoryBarrier = new CyclicBarrier(2);
        CountDownLatch releaseTasks = new CountDownLatch(1);
        TranscodeServiceImpl service = service((param, pushUrl) -> {
            await(factoryBarrier);
            return new RecordingTranscodeContext(param, pushUrl, () -> {
                releaseTasks.await();
            });
        });

        List<java.util.concurrent.Future<Throwable>> attempts = List.of(
                callers.submit(() -> invoke(service, request())),
                callers.submit(() -> invoke(service, request())));

        Throwable first = attempts.get(0).get(2, TimeUnit.SECONDS);
        Throwable second = attempts.get(1).get(2, TimeUnit.SECONDS);
        List<Throwable> results = Arrays.asList(first, second);
        assertThat(results.stream().filter(throwable -> throwable == null)).hasSize(1);
        assertThat(results.stream().filter(IllegalStateException.class::isInstance)).hasSize(1);
        releaseTasks.countDown();
    }

    @Test
    void shouldRemoveRegistrationWhenTaskFinishesNaturally() {
        TranscodeServiceImpl service = service((param, pushUrl) -> new RecordingTranscodeContext(param, pushUrl, () -> {
        }));

        service.transcode(request());

        assertThatCode(() -> awaitSuccessfulRestart(service)).doesNotThrowAnyException();
    }

    @Test
    void shouldStopContextAndCancelRunningFuture() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        AtomicBoolean stopped = new AtomicBoolean(false);
        RecordingTranscodeContext context = new RecordingTranscodeContext(request(), "rtmp://push", () -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
            }
        }, () -> stopped.set(true));
        TranscodeServiceImpl service = service((param, pushUrl) -> context);
        service.transcode(request());
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

        service.stopTranscode("stream-1");

        assertThat(stopped).isTrue();
        assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void closeShouldStopAllTasksAndBeIdempotent() throws Exception {
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch interrupted = new CountDownLatch(2);
        AtomicInteger stopped = new AtomicInteger();
        TranscodeServiceImpl service = service((param, pushUrl) ->
                new RecordingTranscodeContext(param, pushUrl, () -> {
                    started.countDown();
                    try {
                        new CountDownLatch(1).await();
                    } catch (InterruptedException exception) {
                        interrupted.countDown();
                        Thread.currentThread().interrupt();
                    }
                }, stopped::incrementAndGet));
        service.transcode(request("stream-1"));
        service.transcode(request("stream-2"));
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();

        service.close();
        service.close();

        assertThat(stopped).hasValue(2);
        assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(TranscodeServiceImpl.class.getMethod("close").isAnnotationPresent(PreDestroy.class)).isTrue();
    }

    private TranscodeServiceImpl service(java.util.function.BiFunction<TranscodeBO, String, TranscodeContext> factory) {
        MediaResourcePolicy policy = new MediaResourcePolicy() {
            @Override
            public URI requireAllowed(String value, com.ss.zlm4j.security.MediaResourceUsage usage) {
                return URI.create("https://example.com/live");
            }

            @Override
            public Path requireRecordingPath(String value) {
                throw new UnsupportedOperationException();
            }
        };
        ZLMApi zlmApi = (ZLMApi) Proxy.newProxyInstance(
                ZLMApi.class.getClassLoader(), new Class<?>[]{ZLMApi.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        return new TranscodeServiceImpl(policy, zlmApi, 7935, taskExecutor, factory);
    }

    private void awaitSuccessfulRestart(TranscodeServiceImpl service) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(1).toNanos();
        while (System.nanoTime() < deadline) {
            try {
                service.transcode(request());
                return;
            } catch (IllegalStateException ignored) {
                Thread.onSpinWait();
            }
        }
        throw new AssertionError("completed transcode task remained registered");
    }

    private static Throwable invoke(TranscodeServiceImpl service, TranscodeBO request) {
        try {
            service.transcode(request);
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private static TranscodeBO request() {
        return request("stream-1");
    }

    private static TranscodeBO request(String stream) {
        return new TranscodeBO()
                .setUrl("https://example.com/live")
                .setApp("live")
                .setStream(stream);
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }

    private static final class RecordingTranscodeContext extends TranscodeContext {

        private final InterruptibleAction startAction;
        private final Runnable stopAction;

        private RecordingTranscodeContext(TranscodeBO param, String pushUrl, InterruptibleAction startAction) {
            this(param, pushUrl, startAction, () -> {
            });
        }

        private RecordingTranscodeContext(TranscodeBO param, String pushUrl, InterruptibleAction startAction,
                                          Runnable stopAction) {
            super(param, pushUrl);
            this.startAction = startAction;
            this.stopAction = stopAction;
        }

        @Override
        public void start() {
            try {
                startAction.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void stop() {
            stopAction.run();
        }
    }

    @FunctionalInterface
    private interface InterruptibleAction {
        void run() throws InterruptedException;
    }
}
