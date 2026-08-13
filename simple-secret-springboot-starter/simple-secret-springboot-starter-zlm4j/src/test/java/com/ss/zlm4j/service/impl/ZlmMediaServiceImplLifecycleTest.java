package com.ss.zlm4j.service.impl;

import com.aizuda.zlm4j.callback.IMKProxyPlayerCallBack;
import com.aizuda.zlm4j.callback.IMKPushEventCallBack;
import com.aizuda.zlm4j.core.ZLMApi;
import com.aizuda.zlm4j.structure.MK_PROXY_PLAYER;
import com.aizuda.zlm4j.structure.MK_PUSHER;
import com.aizuda.zlm4j.structure.MK_RTP_SERVER;
import com.aizuda.zlm4j.structure.MK_INI;
import com.aizuda.zlm4j.structure.MK_MEDIA_SOURCE;
import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.security.MediaResourcePolicy;
import com.ss.zlm4j.security.MediaResourceUsage;
import com.ss.zlm4j.service.domain.bo.GetMediaListBO;
import com.ss.zlm4j.service.domain.bo.OpenRtpServerBO;
import com.ss.zlm4j.service.domain.bo.StreamProxyPullerBO;
import com.ss.zlm4j.service.domain.bo.StreamProxyPusherBO;
import com.sun.jna.Memory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
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
    void closedServiceShouldRejectNewNativeProxies() {
        AtomicInteger nativeCalls = new AtomicInteger();
        ZlmMediaServiceImpl service = new ZlmMediaServiceImpl(policy(), recordingApi(nativeCalls));
        service.close();

        assertThatThrownBy(() -> service.addStreamPullerProxy(pullerRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("已关闭");
        assertThatThrownBy(() -> service.addStreamPusherProxy(pusherRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("已关闭");
        assertThat(nativeCalls).hasValue(0);
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
    void iniReleaseFailureShouldReleaseCreatedPullerProxy() {
        AtomicInteger proxyReleases = new AtomicInteger();
        MK_INI ini = new MK_INI(new Memory(8));
        MK_PROXY_PLAYER proxyPlayer = new MK_PROXY_PLAYER(new Memory(8));
        ZLMApi api = (ZLMApi) Proxy.newProxyInstance(ZLMApi.class.getClassLoader(),
                new Class<?>[]{ZLMApi.class}, (proxy, method, args) -> {
                    if (method.getName().equals("mk_ini_create")) {
                        return ini;
                    }
                    if (method.getName().equals("mk_proxy_player_create4")) {
                        return proxyPlayer;
                    }
                    if (method.getName().equals("mk_ini_release")) {
                        throw new IllegalStateException("ini release failed");
                    }
                    if (method.getName().equals("mk_proxy_player_release")) {
                        proxyReleases.incrementAndGet();
                    }
                    return defaultValue(method.getReturnType());
                });
        ZlmMediaServiceImpl service = pullerService(api);

        assertThatThrownBy(() -> service.addStreamPullerProxy(pullerRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("ini release failed");
        assertThat(proxyReleases).hasValue(1);
    }

    @Test
    void pullerFailureShouldThrowAndReleaseRegisteredNativeProxy() throws Exception {
        AtomicInteger proxyReleases = new AtomicInteger();
        AtomicReference<IMKProxyPlayerCallBack> resultCallback = new AtomicReference<>();
        ZlmMediaServiceImpl service = pullerService(pullerApi(
                proxyReleases, resultCallback, true));

        assertThatThrownBy(() -> service.addStreamPullerProxy(pullerRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("拉流代理连接失败")
                .hasMessageNotContaining("connection refused");
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

    @Test
    void pullerSuccessShouldReturnKeyAndKeepProxyTracked() throws Exception {
        AtomicInteger proxyReleases = new AtomicInteger();
        ZlmMediaServiceImpl service = pullerService(successfulPullerApi(proxyReleases));

        String key = service.addStreamPullerProxy(pullerRequest());

        assertThat(key).isEqualTo(service.getStreamKey("pull", "live", "output"));
        assertThat(registry(service, "proxyPlayers")).containsKey(key);
        assertThat(proxyReleases).hasValue(0);
        assertThat(service.delStreamPullerProxy(key)).isTrue();
        assertThat(proxyReleases).hasValue(1);
    }

    @Test
    void closeShouldWaitForPullerStartupTransaction() throws Exception {
        CountDownLatch playStarted = new CountDownLatch(1);
        CountDownLatch continuePlay = new CountDownLatch(1);
        CountDownLatch closeFinished = new CountDownLatch(1);
        AtomicInteger proxyReleases = new AtomicInteger();
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ZlmMediaServiceImpl service = pullerService(blockingSuccessfulPullerApi(
                proxyReleases, playStarted, continuePlay));
        Thread startThread = new Thread(() -> runPuller(service, result, failure), "puller-start-test");
        Thread closeThread = new Thread(() -> closeService(service, closeFinished), "puller-close-test");

        startThread.start();
        assertThat(playStarted.await(1, TimeUnit.SECONDS)).isTrue();
        closeThread.start();
        assertThat(closeFinished.await(50, TimeUnit.MILLISECONDS)).isFalse();
        continuePlay.countDown();
        startThread.join(1000);
        closeThread.join(1000);

        assertThat(failure.get()).isNull();
        assertThat(result.get()).isEqualTo(service.getStreamKey("pull", "live", "output"));
        assertThat(closeFinished.getCount()).isZero();
        assertThat(proxyReleases).hasValue(1);
        assertThat(registry(service, "proxyPlayers")).isEmpty();
    }

    @Test
    void interruptedPullerWaitShouldRestoreInterruptAndReleaseProxy() throws Exception {
        CountDownLatch playStarted = new CountDownLatch(1);
        AtomicInteger proxyReleases = new AtomicInteger();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Boolean> interrupted = new AtomicReference<>(false);
        ZlmMediaServiceImpl service = pullerService(waitingPullerApi(proxyReleases, playStarted));
        Thread thread = new Thread(() -> runInterruptedPuller(service, failure, interrupted),
                "puller-interrupt-test");

        thread.start();
        assertThat(playStarted.await(1, TimeUnit.SECONDS)).isTrue();
        thread.interrupt();
        thread.join(1000);

        assertThat(failure.get()).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("线程被中断");
        assertThat(interrupted.get()).isTrue();
        assertThat(proxyReleases).hasValue(1);
        assertThat(registry(service, "proxyPlayers")).isEmpty();
    }

    @Test
    void pusherFailureShouldThrowAndReleaseRegisteredNativePusher() throws Exception {
        AtomicInteger pusherReleases = new AtomicInteger();
        AtomicReference<IMKPushEventCallBack> resultCallback = new AtomicReference<>();
        ZlmMediaServiceImpl service = pusherService(pusherApi(
                pusherReleases, resultCallback, true));

        assertThatThrownBy(() -> service.addStreamPusherProxy(pusherRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("推流代理连接失败")
                .hasMessageNotContaining("publish refused");
        assertThat(pusherReleases).hasValue(1);
        assertThat(registry(service, "pushers")).isEmpty();
        assertThat(registry(service, "pusherCallbacks")).isEmpty();
        assertThat(registry(service, "pusherResultCallbacks")).isEmpty();
    }

    @Test
    void pusherTimeoutShouldThrowAndReleaseRegisteredNativePusher() throws Exception {
        AtomicInteger pusherReleases = new AtomicInteger();
        ZlmMediaServiceImpl service = pusherService(pusherApi(
                pusherReleases, new AtomicReference<>(), false));

        assertThatThrownBy(() -> service.addStreamPusherProxy(pusherRequest()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("超时");
        assertThat(pusherReleases).hasValue(1);
        assertThat(registry(service, "pushers")).isEmpty();
        assertThat(registry(service, "pusherCallbacks")).isEmpty();
        assertThat(registry(service, "pusherResultCallbacks")).isEmpty();
    }

    @Test
    void pusherSuccessShouldReturnKeyAndKeepProxyTracked() throws Exception {
        AtomicInteger pusherReleases = new AtomicInteger();
        ZlmMediaServiceImpl service = pusherService(successfulPusherApi(pusherReleases));

        String key = service.addStreamPusherProxy(pusherRequest());

        assertThat(key).isEqualTo(service.getStreamKey("push", "live", "input"));
        assertThat(registry(service, "pushers")).containsKey(key);
        assertThat(pusherReleases).hasValue(0);
        assertThat(service.delStreamPusherProxy(key)).isTrue();
        assertThat(pusherReleases).hasValue(1);
    }

    @Test
    void stalePullerReleaseShouldKeepReplacementCallbacks() throws Exception {
        ZlmMediaServiceImpl service = new ZlmMediaServiceImpl(policy(), recordingApi(new AtomicInteger()));
        String key = "pull-live-camera";
        MK_PROXY_PLAYER staleProxy = new MK_PROXY_PLAYER(new Memory(8));
        MK_PROXY_PLAYER replacementProxy = new MK_PROXY_PLAYER(new Memory(8));
        IMKProxyPlayerCallBack replacementCallback = (userData, errCode, message, systemError) -> { };
        registry(service, "proxyPlayers").put(key, replacementProxy);
        registry(service, "proxyPlayerCallbacks").put(key, replacementCallback);
        registry(service, "proxyResultCallbacks").put(key, replacementCallback);

        assertThat(service.releasePuller(key, staleProxy)).isFalse();
        assertThat(registry(service, "proxyPlayerCallbacks")).containsEntry(key, replacementCallback);
        assertThat(registry(service, "proxyResultCallbacks")).containsEntry(key, replacementCallback);
    }

    @Test
    void stalePusherReleaseShouldKeepReplacementCallbacks() throws Exception {
        ZlmMediaServiceImpl service = new ZlmMediaServiceImpl(policy(), recordingApi(new AtomicInteger()));
        String key = "push-live-camera";
        MK_PUSHER stalePusher = new MK_PUSHER(new Memory(8));
        MK_PUSHER replacementPusher = new MK_PUSHER(new Memory(8));
        IMKPushEventCallBack replacementCallback = (userData, errCode, message) -> { };
        registry(service, "pushers").put(key, replacementPusher);
        registry(service, "pusherCallbacks").put(key, replacementCallback);
        registry(service, "pusherResultCallbacks").put(key, replacementCallback);

        assertThat(service.releasePusher(key, stalePusher)).isFalse();
        assertThat(registry(service, "pusherCallbacks")).containsEntry(key, replacementCallback);
        assertThat(registry(service, "pusherResultCallbacks")).containsEntry(key, replacementCallback);
    }

    @Test
    void failedReleaseShouldRemainTrackedAndAllowRetry() throws Exception {
        AtomicInteger releaseAttempts = new AtomicInteger();
        ZLMApi api = failingFirstReleaseApi(releaseAttempts);
        ZlmMediaServiceImpl service = new ZlmMediaServiceImpl(policy(), api);
        String key = "pull-live-camera";
        MK_PROXY_PLAYER proxyPlayer = new MK_PROXY_PLAYER(new Memory(8));
        IMKProxyPlayerCallBack callback = (userData, errCode, message, systemError) -> { };
        registry(service, "proxyPlayers").put(key, proxyPlayer);
        registry(service, "proxyPlayerCallbacks").put(key, callback);
        registry(service, "proxyResultCallbacks").put(key, callback);

        assertThatThrownBy(() -> service.releasePuller(key, proxyPlayer))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("release failed");
        assertThat(registry(service, "proxyPlayers")).containsEntry(key, proxyPlayer);
        assertThat(registry(service, "proxyPlayerCallbacks")).containsEntry(key, callback);
        assertThat(service.releasePuller(key, proxyPlayer)).isTrue();
        assertThat(registry(service, "proxyPlayers")).isEmpty();
        assertThat(releaseAttempts).hasValue(2);
    }

    @Test
    void closeShouldContinueAfterFailureAndRetryRemainingResource() throws Exception {
        AtomicInteger pullerAttempts = new AtomicInteger();
        AtomicInteger pusherReleases = new AtomicInteger();
        AtomicInteger rtpReleases = new AtomicInteger();
        ZlmMediaServiceImpl service = new ZlmMediaServiceImpl(
                policy(), aggregateReleaseApi(pullerAttempts, pusherReleases, rtpReleases));
        registry(service, "proxyPlayers").put("pull", new MK_PROXY_PLAYER(new Memory(8)));
        registry(service, "pushers").put("push", new MK_PUSHER(new Memory(8)));
        registry(service, "rtpServers").put("rtp", new MK_RTP_SERVER(new Memory(8)));

        assertThatThrownBy(service::close)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("puller release failed");
        assertThat(registry(service, "proxyPlayers")).hasSize(1);
        assertThat(registry(service, "pushers")).isEmpty();
        assertThat(registry(service, "rtpServers")).isEmpty();
        assertThat(pusherReleases).hasValue(1);
        assertThat(rtpReleases).hasValue(1);

        service.close();
        assertThat(registry(service, "proxyPlayers")).isEmpty();
        assertThat(pullerAttempts).hasValue(2);
    }

    @Test
    void unregisteredPullerReleaseFailureShouldPreserveOriginalFailureAndRetryOnClose() {
        AtomicInteger releaseAttempts = new AtomicInteger();
        ZlmMediaServiceImpl service = pullerService(unregisteredPullerReleaseApi(releaseAttempts));
        StreamProxyPullerBO request = pullerRequest().setTimeoutSec(1);

        assertThatThrownBy(() -> service.addStreamPullerProxy(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("transport configuration failed")
                .satisfies(exception -> assertThat(exception.getSuppressed())
                        .extracting(Throwable::getMessage)
                        .containsExactly("puller release failed"));

        service.close();
        assertThat(releaseAttempts).hasValue(2);
    }

    @Test
    void unregisteredPusherReleaseFailureShouldPreserveOriginalFailureAndRetryOnClose() {
        AtomicInteger releaseAttempts = new AtomicInteger();
        ZlmMediaServiceImpl service = pusherService(unregisteredPusherReleaseApi(releaseAttempts));
        StreamProxyPusherBO request = pusherRequest();
        request.setTimeoutSec(1);

        assertThatThrownBy(() -> service.addStreamPusherProxy(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("transport configuration failed")
                .satisfies(exception -> assertThat(exception.getSuppressed())
                        .extracting(Throwable::getMessage)
                        .containsExactly("pusher release failed"));

        service.close();
        assertThat(releaseAttempts).hasValue(2);
    }

    @Test
    void unregisteredRtpReleaseFailureShouldPreserveOriginalFailureAndRetryOnClose() {
        AtomicInteger releaseAttempts = new AtomicInteger();
        ZlmMediaServiceImpl service = new ZlmMediaServiceImpl(
                policy(), unregisteredRtpReleaseApi(releaseAttempts));
        OpenRtpServerBO request = new OpenRtpServerBO().setPort(0).setTcpMode(0).setStream("camera");

        assertThatThrownBy(() -> service.openRtpServer(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("read port failed")
                .satisfies(exception -> assertThat(exception.getSuppressed())
                        .extracting(Throwable::getMessage)
                        .containsExactly("rtp release failed"));

        service.close();
        assertThat(releaseAttempts).hasValue(2);
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

    private static ZLMApi failingFirstReleaseApi(AtomicInteger releaseAttempts) {
        return (ZLMApi) Proxy.newProxyInstance(ZLMApi.class.getClassLoader(),
                new Class<?>[]{ZLMApi.class}, (proxy, method, args) -> {
                    if (method.getName().equals("mk_proxy_player_release")
                            && releaseAttempts.incrementAndGet() == 1) {
                        throw new IllegalStateException("release failed");
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static ZLMApi aggregateReleaseApi(AtomicInteger pullerAttempts,
                                              AtomicInteger pusherReleases,
                                              AtomicInteger rtpReleases) {
        return (ZLMApi) Proxy.newProxyInstance(ZLMApi.class.getClassLoader(),
                new Class<?>[]{ZLMApi.class}, (proxy, method, args) -> {
                    if (method.getName().equals("mk_proxy_player_release")
                            && pullerAttempts.incrementAndGet() == 1) {
                        throw new IllegalStateException("puller release failed");
                    }
                    if (method.getName().equals("mk_pusher_release")) {
                        pusherReleases.incrementAndGet();
                    }
                    if (method.getName().equals("mk_rtp_server_release")) {
                        rtpReleases.incrementAndGet();
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static ZLMApi unregisteredPullerReleaseApi(AtomicInteger releaseAttempts) {
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
                    if (method.getName().equals("mk_proxy_player_set_option")) {
                        throw new IllegalArgumentException("transport configuration failed");
                    }
                    if (method.getName().equals("mk_proxy_player_release")
                            && releaseAttempts.incrementAndGet() == 1) {
                        throw new IllegalStateException("puller release failed");
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static ZLMApi unregisteredPusherReleaseApi(AtomicInteger releaseAttempts) {
        MK_MEDIA_SOURCE source = new MK_MEDIA_SOURCE(new Memory(8));
        MK_PUSHER pusher = new MK_PUSHER(new Memory(8));
        return (ZLMApi) Proxy.newProxyInstance(ZLMApi.class.getClassLoader(),
                new Class<?>[]{ZLMApi.class}, (proxy, method, args) -> {
                    if (method.getName().equals("mk_media_source_find2")) {
                        return source;
                    }
                    if (method.getName().equals("mk_pusher_create_src")) {
                        return pusher;
                    }
                    if (method.getName().equals("mk_pusher_set_option")) {
                        throw new IllegalArgumentException("transport configuration failed");
                    }
                    if (method.getName().equals("mk_pusher_release")
                            && releaseAttempts.incrementAndGet() == 1) {
                        throw new IllegalStateException("pusher release failed");
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static ZLMApi unregisteredRtpReleaseApi(AtomicInteger releaseAttempts) {
        MK_RTP_SERVER rtpServer = new MK_RTP_SERVER(new Memory(8));
        return (ZLMApi) Proxy.newProxyInstance(ZLMApi.class.getClassLoader(),
                new Class<?>[]{ZLMApi.class}, (proxy, method, args) -> {
                    if (method.getName().equals("mk_rtp_server_create")) {
                        return rtpServer;
                    }
                    if (method.getName().equals("mk_rtp_server_port")) {
                        throw new IllegalArgumentException("read port failed");
                    }
                    if (method.getName().equals("mk_rtp_server_release")
                            && releaseAttempts.incrementAndGet() == 1) {
                        throw new IllegalStateException("rtp release failed");
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

    private static ZLMApi successfulPullerApi(AtomicInteger releases) {
        AtomicReference<IMKProxyPlayerCallBack> callback = new AtomicReference<>();
        return pullerCallbackApi(releases, callback, () -> callback.get().invoke(null, 0, null, 0));
    }

    private static ZLMApi blockingSuccessfulPullerApi(AtomicInteger releases,
                                                      CountDownLatch playStarted,
                                                      CountDownLatch continuePlay) {
        AtomicReference<IMKProxyPlayerCallBack> callback = new AtomicReference<>();
        return pullerCallbackApi(releases, callback, () -> {
            playStarted.countDown();
            awaitLatch(continuePlay);
            callback.get().invoke(null, 0, null, 0);
        });
    }

    private static ZLMApi waitingPullerApi(AtomicInteger releases, CountDownLatch playStarted) {
        return pullerCallbackApi(releases, new AtomicReference<>(), playStarted::countDown);
    }

    private static ZLMApi pullerCallbackApi(AtomicInteger releases,
                                            AtomicReference<IMKProxyPlayerCallBack> callback,
                                            Runnable onPlay) {
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
                    if (method.getName().equals("mk_proxy_player_set_on_play_result")) {
                        callback.set((IMKProxyPlayerCallBack) args[1]);
                    }
                    if (method.getName().equals("mk_proxy_player_play")) {
                        onPlay.run();
                    }
                    if (method.getName().equals("mk_proxy_player_release")) {
                        releases.incrementAndGet();
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

    private static ZlmMediaServiceImpl pusherService(ZLMApi api) {
        return new ZlmMediaServiceImpl(
                policy(), api, new com.ss.zlm4j.config.properties.ZlmMediaProperties(),
                Duration.ofMillis(20));
    }

    private static ZLMApi pusherApi(AtomicInteger releases,
                                    AtomicReference<IMKPushEventCallBack> resultCallback,
                                    boolean reportFailure) {
        MK_MEDIA_SOURCE source = new MK_MEDIA_SOURCE(new Memory(8));
        MK_PUSHER pusher = new MK_PUSHER(new Memory(8));
        return (ZLMApi) Proxy.newProxyInstance(ZLMApi.class.getClassLoader(),
                new Class<?>[]{ZLMApi.class}, (proxy, method, args) -> {
                    if (method.getName().equals("mk_media_source_find2")) {
                        return source;
                    }
                    if (method.getName().equals("mk_pusher_create_src")) {
                        return pusher;
                    }
                    if (method.getName().equals("mk_pusher_release")) {
                        releases.incrementAndGet();
                    }
                    if (method.getName().equals("mk_pusher_set_on_result")) {
                        resultCallback.set((IMKPushEventCallBack) args[1]);
                    }
                    if (method.getName().equals("mk_pusher_publish")
                            && reportFailure && resultCallback.get() != null) {
                        resultCallback.get().invoke(pusher.getPointer(), 1, "publish refused");
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static ZLMApi successfulPusherApi(AtomicInteger releases) {
        AtomicReference<IMKPushEventCallBack> callback = new AtomicReference<>();
        MK_MEDIA_SOURCE source = new MK_MEDIA_SOURCE(new Memory(8));
        MK_PUSHER pusher = new MK_PUSHER(new Memory(8));
        return (ZLMApi) Proxy.newProxyInstance(ZLMApi.class.getClassLoader(),
                new Class<?>[]{ZLMApi.class}, (proxy, method, args) -> {
                    if (method.getName().equals("mk_media_source_find2")) {
                        return source;
                    }
                    if (method.getName().equals("mk_pusher_create_src")) {
                        return pusher;
                    }
                    if (method.getName().equals("mk_pusher_set_on_result")) {
                        callback.set((IMKPushEventCallBack) args[1]);
                    }
                    if (method.getName().equals("mk_pusher_publish")) {
                        callback.get().invoke(null, 0, null);
                    }
                    if (method.getName().equals("mk_pusher_release")) {
                        releases.incrementAndGet();
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static void runPuller(ZlmMediaServiceImpl service,
                                  AtomicReference<String> result,
                                  AtomicReference<Throwable> failure) {
        try {
            result.set(service.addStreamPullerProxy(pullerRequest()));
        } catch (Throwable exception) {
            failure.set(exception);
        }
    }

    private static void closeService(ZlmMediaServiceImpl service, CountDownLatch closeFinished) {
        try {
            service.close();
        } finally {
            closeFinished.countDown();
        }
    }

    private static void runInterruptedPuller(ZlmMediaServiceImpl service,
                                             AtomicReference<Throwable> failure,
                                             AtomicReference<Boolean> interrupted) {
        try {
            service.addStreamPullerProxy(pullerRequest());
        } catch (Throwable exception) {
            failure.set(exception);
            interrupted.set(Thread.currentThread().isInterrupted());
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test interrupted", exception);
        }
    }

    private static StreamProxyPusherBO pusherRequest() {
        StreamProxyPusherBO request = new StreamProxyPusherBO();
        request.setUrl("rtmp://example.com/live/output");
        request.setApp("live");
        request.setStream("input");
        request.setSchema("rtmp");
        return request;
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
