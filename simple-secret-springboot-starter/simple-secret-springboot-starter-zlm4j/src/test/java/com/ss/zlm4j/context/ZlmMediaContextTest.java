package com.ss.zlm4j.context;

import com.aizuda.zlm4j.core.ZLMApi;
import com.ss.zlm4j.config.properties.ZlmMediaProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZlmMediaContextTest {

    @Test
    void startFailsAndRollsBackWhenAnyRequiredServerFails() {
        FakeZlmApi fake = new FakeZlmApi(Map.of(
                "mk_http_server_start", (short) 0,
                "mk_rtsp_server_start", (short) 7554,
                "mk_rtmp_server_start", (short) 7935,
                "mk_rtc_server_start", (short) 8000));
        ZlmMediaContext context = new TestZlmMediaContext(fake.api());

        assertThat(context.startMediaServer()).isFalse();
        assertThat(fake.stopCalls()).isEqualTo(1);
    }

    @Test
    void startSucceedsOnlyWhenAllRequiredServersStart() {
        FakeZlmApi fake = new FakeZlmApi(Map.of(
                "mk_http_server_start", (short) 7080,
                "mk_rtsp_server_start", (short) 7554,
                "mk_rtmp_server_start", (short) 7935,
                "mk_rtc_server_start", (short) 8000));
        ZlmMediaContext context = new TestZlmMediaContext(fake.api());

        assertThat(context.startMediaServer()).isTrue();
        assertThat(fake.stopCalls()).isZero();
    }

    @Test
    void initializationThrowsWhenRequiredServerCannotStart() {
        FakeZlmApi fake = new FakeZlmApi(Map.of(
                "mk_http_server_start", (short) 0,
                "mk_rtsp_server_start", (short) 7554,
                "mk_rtmp_server_start", (short) 7935,
                "mk_rtc_server_start", (short) 8000));

        assertThatThrownBy(new TestZlmMediaContext(fake.api())::initMediaServer)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MediaServer");
        assertThat(fake.stopCalls()).isEqualTo(1);
    }

    @Test
    void startsOnlyEnabledRtmpListener() {
        ZlmMediaProperties properties = rtmpOnlyProperties();
        FakeZlmApi fake = new FakeZlmApi(Map.of("mk_rtmp_server_start", (short) 7935));
        ZlmMediaContext context = new TestZlmMediaContext(properties, fake.api());

        assertThat(context.startMediaServer()).isTrue();
        assertThat(fake.calls("mk_rtmp_server_start")).isEqualTo(1);
        assertThat(fake.calls("mk_http_server_start")).isZero();
        assertThat(fake.calls("mk_rtsp_server_start")).isZero();
        assertThat(fake.calls("mk_rtc_server_start")).isZero();
        assertThat(fake.stopCalls()).isZero();
    }

    @Test
    void rollsBackWhenEnabledRtmpListenerFails() {
        ZlmMediaProperties properties = rtmpOnlyProperties();
        FakeZlmApi fake = new FakeZlmApi(Map.of("mk_rtmp_server_start", (short) 0));
        ZlmMediaContext context = new TestZlmMediaContext(properties, fake.api());

        assertThat(context.startMediaServer()).isFalse();
        assertThat(fake.calls("mk_rtmp_server_start")).isEqualTo(1);
        assertThat(fake.calls("mk_http_server_start")).isZero();
        assertThat(fake.calls("mk_rtsp_server_start")).isZero();
        assertThat(fake.calls("mk_rtc_server_start")).isZero();
        assertThat(fake.stopCalls()).isEqualTo(1);
    }

    @Test
    void rejectsConfigurationWithAllListenersDisabled() {
        ZlmMediaProperties properties = rtmpOnlyProperties();
        properties.setRtmpListenerEnabled(false);
        FakeZlmApi fake = new FakeZlmApi(Map.of());
        ZlmMediaContext context = new TestZlmMediaContext(properties, fake.api());

        assertThat(context.startMediaServer()).isFalse();
        assertThat(fake.totalStartCalls()).isZero();
        assertThat(fake.stopCalls()).isZero();
    }

    private static ZlmMediaProperties rtmpOnlyProperties() {
        ZlmMediaProperties properties = new ZlmMediaProperties();
        properties.setHttpListenerEnabled(false);
        properties.setRtspListenerEnabled(false);
        properties.setRtmpListenerEnabled(true);
        properties.setRtcListenerEnabled(false);
        return properties;
    }

    private static final class TestZlmMediaContext extends ZlmMediaContext {
        private final ZLMApi api;

        private TestZlmMediaContext(ZLMApi api) {
            this(new ZlmMediaProperties(), api);
        }

        private TestZlmMediaContext(ZlmMediaProperties properties, ZLMApi api) {
            super(properties, new ZlmCallbackHandlerContext(), api);
            this.api = api;
        }

        @Override
        protected ZLMApi loadZlmApi() {
            return api;
        }
    }

    private static final class FakeZlmApi {
        private final Map<String, Short> ports;
        /** Records the invocation count for each native API method. */
        private final Map<String, AtomicInteger> calls = new HashMap<>();
        private final AtomicInteger stopCalls = new AtomicInteger();
        private final ZLMApi api;

        private FakeZlmApi(Map<String, Short> ports) {
            this.ports = new HashMap<>(ports);
            this.api = (ZLMApi) Proxy.newProxyInstance(
                    ZLMApi.class.getClassLoader(),
                    new Class<?>[]{ZLMApi.class},
                    (proxy, method, args) -> {
                        calls.computeIfAbsent(method.getName(), ignored -> new AtomicInteger()).incrementAndGet();
                        if (ports.containsKey(method.getName())) {
                            return ports.get(method.getName());
                        }
                        if (method.getName().equals("mk_stop_all_server")) {
                            stopCalls.incrementAndGet();
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private ZLMApi api() {
            return api;
        }

        private int stopCalls() {
            return stopCalls.get();
        }

        private int calls(String methodName) {
            AtomicInteger count = calls.get(methodName);
            return count == null ? 0 : count.get();
        }

        private int totalStartCalls() {
            return calls.entrySet().stream()
                    .filter(entry -> entry.getKey().endsWith("_server_start"))
                    .mapToInt(entry -> entry.getValue().get())
                    .sum();
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
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
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
