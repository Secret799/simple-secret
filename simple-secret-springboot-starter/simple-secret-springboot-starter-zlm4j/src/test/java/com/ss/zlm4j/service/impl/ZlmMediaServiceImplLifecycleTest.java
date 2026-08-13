package com.ss.zlm4j.service.impl;

import com.aizuda.zlm4j.callback.IMKProxyPlayerCallBack;
import com.aizuda.zlm4j.core.ZLMApi;
import com.aizuda.zlm4j.structure.MK_PROXY_PLAYER;
import com.aizuda.zlm4j.structure.MK_PUSHER;
import com.aizuda.zlm4j.structure.MK_RTP_SERVER;
import com.aizuda.zlm4j.structure.MK_INI;
import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.security.MediaResourcePolicy;
import com.ss.zlm4j.security.MediaResourceUsage;
import com.ss.zlm4j.service.domain.bo.GetMediaListBO;
import com.ss.zlm4j.service.domain.bo.StreamProxyPullerBO;
import com.sun.jna.Memory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZlmMediaServiceImplLifecycleTest {

    @Test
    void keepsNativeRegistriesInstanceScoped() throws Exception {
        ZlmMediaServiceImpl first = new ZlmMediaServiceImpl(policy(), recordingApi(new AtomicInteger()));
        ZlmMediaServiceImpl second = new ZlmMediaServiceImpl(policy(), recordingApi(new AtomicInteger()));

        Field field = ZlmMediaServiceImpl.class.getDeclaredField("proxyPlayers");
        assertThat(Modifier.isStatic(field.getModifiers())).isFalse();
        assertThat(registry(first, field)).isNotSameAs(registry(second, field));
    }

    @Test
    void closeReleasesTrackedNativeResourcesExactlyOnce() throws Exception {
        AtomicInteger releases = new AtomicInteger();
        ZlmMediaServiceImpl service = new ZlmMediaServiceImpl(policy(), recordingApi(releases));
        registry(service, "proxyPlayers").put("pull", new MK_PROXY_PLAYER(new Memory(8)));
        registry(service, "pushers").put("push", new MK_PUSHER(new Memory(8)));
        registry(service, "rtpServers").put("rtp", new MK_RTP_SERVER(new Memory(8)));

        service.close();
        service.close();

        assertThat(releases).hasValue(3);
        assertThat(registry(service, "proxyPlayers")).isEmpty();
        assertThat(registry(service, "pushers")).isEmpty();
        assertThat(registry(service, "rtpServers")).isEmpty();
    }

    @Test
    void releasesPullerOptionsWhenConfigurationFails() {
        AtomicInteger iniReleases = new AtomicInteger();
        ZLMApi api = (ZLMApi) Proxy.newProxyInstance(ZLMApi.class.getClassLoader(),
                new Class<?>[]{ZLMApi.class}, (proxy, method, args) -> {
                    if (method.getName().equals("mk_ini_create")) {
                        return new MK_INI(new Memory(8));
                    }
                    if (method.getName().equals("mk_ini_release")) {
                        iniReleases.incrementAndGet();
                    }
                    return defaultValue(method.getReturnType());
                });
        ZlmMediaServiceImpl service = new ZlmMediaServiceImpl(policy(), api) {
            @Override
            public List<MediaSourceDomain> getMediaList(GetMediaListBO param) {
                return List.of();
            }
        };
        StreamProxyPullerBO request = new StreamProxyPullerBO()
                .setUrl("rtmp://example.com/live/input")
                .setApp("live")
                .setStream("output")
                .setSchema("rtmp")
                .setRetryCount(1)
                .setEnableMp4(0)
                .setEnableAudio(1)
                .setEnableFmp4(0)
                .setEnableTs(0)
                .setEnableHls(0)
                .setEnableRtsp(1)
                .setEnableRtmp(1)
                .setMp4MaxSecond(0)
                .setAutoClose(1)
                .setRecordFileRepeat(0);

        assertThatThrownBy(() -> service.addStreamPullerProxy(request))
                .isInstanceOf(RuntimeException.class);
        assertThat(iniReleases).hasValue(1);
    }

    @Test
    void pullerFailureShouldThrowAndReleaseRegisteredNativeProxy() throws Exception {
        AtomicInteger proxyReleases = new AtomicInteger();
        AtomicReference<IMKProxyPlayerCallBack> resultCallback = new AtomicReference<>();
        ZlmMediaServiceImpl service = pullerService(pullerApi(
                proxyReleases, resultCallback, true));

        assertThatThrownBy(() -> service.addStreamPullerProxy(pullerRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("拉流代理失败")
                .hasMessageContaining("connection refused");
        assertThat(proxyReleases).hasValue(1);
        assertThat(registry(service, "proxyPlayers")).isEmpty();
        assertThat(registry(service, "proxyPlayerCallbacks")).isEmpty();
        assertThat(registry(service, "proxyResultCallbacks")).isEmpty();
    }

    @Test
    void pullerTimeoutShouldThrowAndReleaseRegisteredNativeProxy() throws Exception {
        AtomicInteger proxyReleases = new AtomicInteger();
        ZlmMediaServiceImpl service = pullerService(pullerApi(
                proxyReleases, new AtomicReference<>(), false));

        assertThatThrownBy(() -> service.addStreamPullerProxy(pullerRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("超时");
        assertThat(proxyReleases).hasValue(1);
        assertThat(registry(service, "proxyPlayers")).isEmpty();
        assertThat(registry(service, "proxyPlayerCallbacks")).isEmpty();
        assertThat(registry(service, "proxyResultCallbacks")).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static <T> ConcurrentMap<String, T> registry(Object target, String name) throws Exception {
        Field field = ZlmMediaServiceImpl.class.getDeclaredField(name);
        return registry(target, field);
    }

    @SuppressWarnings("unchecked")
    private static <T> ConcurrentMap<String, T> registry(Object target, Field field) throws Exception {
        field.setAccessible(true);
        return (ConcurrentMap<String, T>) field.get(target);
    }

    private static ZLMApi recordingApi(AtomicInteger releases) {
        return (ZLMApi) Proxy.newProxyInstance(ZLMApi.class.getClassLoader(),
                new Class<?>[]{ZLMApi.class}, (proxy, method, args) -> {
                    if (method.getName().endsWith("_release")) {
                        releases.incrementAndGet();
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static ZlmMediaServiceImpl pullerService(ZLMApi api) {
        return new ZlmMediaServiceImpl(
                policy(), api, new com.ss.zlm4j.config.properties.ZlmMediaProperties(),
                Duration.ofMillis(20)) {
            @Override
            public List<MediaSourceDomain> getMediaList(GetMediaListBO param) {
                return List.of();
            }
        };
    }

    private static ZLMApi pullerApi(AtomicInteger releases,
                                    AtomicReference<IMKProxyPlayerCallBack> resultCallback,
                                    boolean reportFailure) {
        MK_INI ini = new MK_INI(new Memory(8));
        MK_PROXY_PLAYER proxyPlayer = new MK_PROXY_PLAYER(new Memory(8));
        return (ZLMApi) Proxy.newProxyInstance(ZLMApi.class.getClassLoader(),
                new Class<?>[]{ZLMApi.class}, (proxy, method, args) -> {
                    if (method.getName().equals("mk_ini_create")) {
                        return ini;
                    }
                    if (method.getName().equals("mk_proxy_player_create4")) {
                        return proxyPlayer;
                    }
                    if (method.getName().equals("mk_proxy_player_release")) {
                        releases.incrementAndGet();
                    }
                    if (method.getName().equals("mk_proxy_player_set_on_play_result")) {
                        resultCallback.set((IMKProxyPlayerCallBack) args[1]);
                    }
                    if (method.getName().equals("mk_proxy_player_play")
                            && reportFailure && resultCallback.get() != null) {
                        resultCallback.get().invoke(proxyPlayer.getPointer(),
                                1, "connection refused", 111);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static StreamProxyPullerBO pullerRequest() {
        return new StreamProxyPullerBO()
                .setUrl("rtmp://example.com/live/input")
                .setApp("live")
                .setStream("output")
                .setSchema("rtmp")
                .setRetryCount(1)
                .setEnableMp4(0)
                .setEnableAudio(1)
                .setEnableFmp4(0)
                .setEnableTs(0)
                .setEnableHls(0)
                .setEnableRtsp(1)
                .setEnableRtmp(1)
                .setMp4MaxSecond(0)
                .setAutoClose(1)
                .setRecordFileRepeat(0);
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

    private static MediaResourcePolicy policy() {
        return new MediaResourcePolicy() {
            @Override
            public URI requireAllowed(String value, MediaResourceUsage usage) {
                return URI.create(value);
            }

            @Override
            public Path requireRecordingPath(String value) {
                return Path.of(value);
            }
        };
    }
}
