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

    private static final class TestZlmMediaContext extends ZlmMediaContext {
        private final ZLMApi api;

        private TestZlmMediaContext(ZLMApi api) {
            super(new ZlmMediaProperties(), new ZlmCallbackHandlerContext(), api);
            this.api = api;
        }

        @Override
        protected ZLMApi loadZlmApi() {
            return api;
        }
    }

    private static final class FakeZlmApi {
        private final Map<String, Short> ports;
        private final AtomicInteger stopCalls = new AtomicInteger();
        private final ZLMApi api;

        private FakeZlmApi(Map<String, Short> ports) {
            this.ports = new HashMap<>(ports);
            this.api = (ZLMApi) Proxy.newProxyInstance(
                    ZLMApi.class.getClassLoader(),
                    new Class<?>[]{ZLMApi.class},
                    (proxy, method, args) -> {
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
