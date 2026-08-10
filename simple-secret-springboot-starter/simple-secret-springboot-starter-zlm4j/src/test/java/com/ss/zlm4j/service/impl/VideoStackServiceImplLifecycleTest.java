package com.ss.zlm4j.service.impl;

import com.aizuda.zlm4j.core.ZLMApi;
import com.ss.zlm4j.config.properties.VideoStackValidationProperties;
import com.ss.zlm4j.context.VideoStackContext;
import com.ss.zlm4j.security.MediaResourcePolicy;
import com.ss.zlm4j.security.MediaResourceUsage;
import com.ss.zlm4j.service.domain.bo.VideoStackBO;
import com.ss.zlm4j.service.validation.VideoStackValidator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class VideoStackServiceImplLifecycleTest {

    @Test
    void shouldAtomicallyRejectConcurrentDuplicateTasks() throws Exception {
        CyclicBarrier factoryBarrier = new CyclicBarrier(2);
        VideoStackServiceImpl service = service(param -> {
            await(factoryBarrier);
            return new RecordingVideoStackContext(param, false);
        });
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<Throwable>> attempts = List.of(
                    callers.submit(() -> invoke(service, request())),
                    callers.submit(() -> invoke(service, request())));

            List<Throwable> results = Arrays.asList(
                    attempts.get(0).get(2, TimeUnit.SECONDS), attempts.get(1).get(2, TimeUnit.SECONDS));
            assertThat(results.stream().filter(throwable -> throwable == null)).hasSize(1);
            assertThat(results.stream().filter(IllegalStateException.class::isInstance)).hasSize(1);
        } finally {
            service.stopStack("wall-1");
            callers.shutdownNow();
        }
    }

    @Test
    void shouldRemoveRegistrationWhenTaskFinishesNaturally() {
        VideoStackServiceImpl service = service(param -> new RecordingVideoStackContext(param, true));

        service.startStack(request());

        assertThatCode(() -> service.startStack(request())).doesNotThrowAnyException();
    }

    @Test
    void shouldStopRegisteredContext() {
        AtomicBoolean stopped = new AtomicBoolean(false);
        VideoStackServiceImpl service = service(param -> new RecordingVideoStackContext(param, false, stopped));
        service.startStack(request());

        service.stopStack("wall-1");

        assertThat(stopped).isTrue();
    }

    private VideoStackServiceImpl service(Function<VideoStackBO, VideoStackContext> factory) {
        MediaResourcePolicy policy = new MediaResourcePolicy() {
            @Override
            public URI requireAllowed(String value, MediaResourceUsage usage) {
                return URI.create(value);
            }

            @Override
            public Path requireRecordingPath(String value) {
                throw new UnsupportedOperationException();
            }
        };
        ZLMApi zlmApi = (ZLMApi) Proxy.newProxyInstance(
                ZLMApi.class.getClassLoader(), new Class<?>[]{ZLMApi.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        return new VideoStackServiceImpl(policy,
                new VideoStackValidator(new VideoStackValidationProperties()), zlmApi, factory);
    }

    private static Throwable invoke(VideoStackServiceImpl service, VideoStackBO request) {
        try {
            service.startStack(request);
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private static VideoStackBO request() {
        return new VideoStackBO()
                .setId("wall-1")
                .setApp("live")
                .setPushUrl("rtmp://example.com/live/wall-1")
                .setRow(1)
                .setCol(1)
                .setWidth(640)
                .setHeight(480)
                .setFillColor("000000")
                .setGridLineEnable(false)
                .setGridLineWidth(1);
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

    private static final class RecordingVideoStackContext extends VideoStackContext {

        private final boolean completeDuringInit;
        private final AtomicBoolean stopped;
        private Runnable completion = () -> {
        };

        private RecordingVideoStackContext(VideoStackBO param, boolean completeDuringInit) {
            this(param, completeDuringInit, new AtomicBoolean());
        }

        private RecordingVideoStackContext(VideoStackBO param, boolean completeDuringInit, AtomicBoolean stopped) {
            super(param);
            this.completeDuringInit = completeDuringInit;
            this.stopped = stopped;
        }

        @Override
        public void setCompletionCallback(Runnable completion) {
            this.completion = completion;
        }

        @Override
        public void init() {
            if (completeDuringInit) {
                completion.run();
            }
        }

        @Override
        public void stop() {
            stopped.set(true);
        }
    }
}
