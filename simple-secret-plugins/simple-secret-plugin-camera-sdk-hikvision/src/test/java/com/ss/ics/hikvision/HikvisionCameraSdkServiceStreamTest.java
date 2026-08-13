package com.ss.ics.hikvision;

import com.ss.ics.domain.DeviceDomain;
import com.ss.ics.domain.LoginDomain;
import com.ss.ics.domain.PlayDomain;
import com.ss.ics.hikvision.internal.HikvisionNativeApi;
import com.ss.ics.hikvision.internal.HikvisionNativeStreamCallback;
import com.ss.ics.hikvision.internal.HikvisionNativeStreamStartException;
import com.ss.ics.hikvision.internal.model.HikvisionNativeLoginResult;
import com.ss.ics.hikvision.internal.model.HikvisionPlaybackRequest;
import com.ss.ics.hikvision.internal.model.HikvisionPreviewRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 海康实时预览和历史回放会话测试。
 *
 * @author junpzx
 * @since 2026-08-12
 */
class HikvisionCameraSdkServiceStreamTest {
    private final List<ServiceFixture> fixtures = new ArrayList<>();

    @AfterEach
    void closeServices() {
        for (int index = fixtures.size() - 1; index >= 0; index--) {
            ServiceFixture fixture = fixtures.get(index);
            fixture.nativeApi().stopPreviewResult = true;
            fixture.service().close();
        }
    }

    @Test
    void startsRealPlayAndClosesStreamBeforeLogout() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        HikvisionCameraSdkService service = service(nativeApi);
        List<HikvisionStreamData> received = new ArrayList<>();

        HikvisionStreamSession session = service.realPlay(
                device().setChannel("2"), play(1, "1"), received::add);
        byte[] source = new byte[]{1, 2, 3};
        nativeApi.callback.onData(101L, 2, source);
        source[0] = 9;

        assertThat(nativeApi.previewRequest)
                .isEqualTo(new HikvisionPreviewRequest(34, 1, 1));
        assertThat(session.handle()).isEqualTo(101L);
        assertThat(session.type()).isEqualTo(HikvisionStreamSession.Type.REAL_PLAY);
        assertThat(received).singleElement().satisfies(data -> {
            assertThat(data.handle()).isEqualTo(101L);
            assertThat(data.dataType()).isEqualTo(2);
            assertThat(data.data()).containsExactly(1, 2, 3);
        });

        session.close();
        session.close();
        service.close();

        assertThat(nativeApi.events).containsExactly(
                "login", "preview:start:42", "preview:stop:101", "logout:42", "cleanup");
    }

    @Test
    void startsPlaybackWithValidatedTimeRange() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        HikvisionCameraSdkService service = service(nativeApi);
        LocalDateTime begin = LocalDateTime.of(2026, 8, 12, 10, 0);
        LocalDateTime end = begin.plusMinutes(30);
        PlayDomain request = play(0, "0").setPlaybackParam(new PlayDomain.PlaybackParam()
                .setCode("playback-01").setBeginTime(begin).setEndTime(end));

        HikvisionStreamSession session = service.playback(device(), request, ignored -> { });

        assertThat(nativeApi.playbackRequest)
                .isEqualTo(new HikvisionPlaybackRequest(33, 0, begin, end));
        assertThat(session.type()).isEqualTo(HikvisionStreamSession.Type.PLAYBACK);
        session.close();
        service.close();
        assertThat(nativeApi.events).containsExactly(
                "login", "playback:start:42", "playback:stop:202", "logout:42", "cleanup");
    }

    @Test
    void playbackIgnoresPreviewOnlyProtocolParameter() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        HikvisionCameraSdkService service = service(nativeApi);
        PlayDomain request = play(0, "not-used").setPlaybackParam(
                new PlayDomain.PlaybackParam()
                        .setBeginTime(LocalDateTime.of(2026, 8, 12, 10, 0))
                        .setEndTime(LocalDateTime.of(2026, 8, 12, 10, 30)));

        try (HikvisionStreamSession ignored = service.playback(
                device(), request, data -> { })) {
            assertThat(nativeApi.playbackRequest.streamType()).isZero();
        }
    }

    @Test
    void logsOutWhenRealPlayStartFails() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.previewHandle = -1L;
        HikvisionCameraSdkService service = service(nativeApi);

        assertThatThrownBy(() -> service.realPlay(device(), play(0, "0"), ignored -> { }))
                .isInstanceOf(HikvisionSdkException.class)
                .hasMessage("Hikvision real-time preview failed (code=0)");
        assertThat(nativeApi.events).containsExactly("login", "preview:start:42", "logout:42");
        service.close();
    }

    @Test
    void rejectsInvalidPlaybackBeforeLogin() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        HikvisionCameraSdkService service = service(nativeApi);
        PlayDomain request = play(0, "0").setPlaybackParam(new PlayDomain.PlaybackParam()
                .setBeginTime(LocalDateTime.of(2026, 8, 12, 11, 0))
                .setEndTime(LocalDateTime.of(2026, 8, 12, 10, 0)));

        assertThatThrownBy(() -> service.playback(device(), request, ignored -> { }))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("playback endTime must be after beginTime");
        assertThat(nativeApi.events).isEmpty();
        service.close();
    }

    @Test
    void serviceCloseStopsAllStreamsBeforeSdkCleanup() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        HikvisionCameraSdkService service = service(nativeApi);
        service.realPlay(device(), play(0, "0"), ignored -> { });
        PlayDomain playback = play(0, "0").setPlaybackParam(new PlayDomain.PlaybackParam()
                .setBeginTime(LocalDateTime.of(2026, 8, 12, 10, 0))
                .setEndTime(LocalDateTime.of(2026, 8, 12, 10, 30)));
        service.playback(device(), playback, ignored -> { });

        service.close();

        assertThat(nativeApi.events).containsExactly(
                "login", "preview:start:42", "login", "playback:start:42",
                "preview:stop:101", "logout:42", "playback:stop:202", "logout:42", "cleanup");
    }

    @Test
    void consumerFailureDoesNotEscapeNativeCallback() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        HikvisionCameraSdkService service = service(nativeApi);
        HikvisionStreamSession session = service.realPlay(
                device(), play(0, "0"), data -> {
                    throw new IllegalStateException("consumer failure");
                });

        nativeApi.callback.onData(101L, 1, new byte[]{1});

        session.close();
        service.close();
        assertThat(nativeApi.events).endsWith("cleanup");
    }

    @Test
    void failedStreamStopCanBeRetriedWithoutLosingLogin() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.stopPreviewResult = false;
        HikvisionCameraSdkService service = service(nativeApi);
        HikvisionStreamSession session = service.realPlay(device(), play(0, "0"), ignored -> { });

        assertThatThrownBy(session::close)
                .isInstanceOf(HikvisionSdkException.class)
                .hasMessage("Hikvision real-time preview stop failed (code=0)");
        assertThat(nativeApi.events).doesNotContain("logout:42");
        nativeApi.stopPreviewResult = true;
        session.close();
        service.close();

        assertThat(nativeApi.events).containsSubsequence(
                "preview:stop:101", "preview:stop:101", "logout:42", "cleanup");
    }

    @Test
    void serviceCloseCanRetryFailedStreamStopBeforeLogoutAndCleanup() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.stopPreviewResult = false;
        HikvisionCameraSdkService service = service(nativeApi);
        service.realPlay(device(), play(0, "0"), ignored -> { });

        assertThatThrownBy(service::close)
                .isInstanceOf(HikvisionSdkException.class)
                .hasMessage("Hikvision real-time preview stop failed (code=0)");
        assertThat(nativeApi.events).containsExactly(
                "login", "preview:start:42", "preview:stop:101");

        nativeApi.stopPreviewResult = true;
        service.close();

        assertThat(nativeApi.events).containsExactly(
                "login", "preview:start:42", "preview:stop:101",
                "preview:stop:101", "logout:42", "cleanup");
    }

    @Test
    void duplicateStreamHandlePreservesExistingSessionAndRejectsNewLogin() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.incrementLoginHandle = true;
        HikvisionCameraSdkService service = service(nativeApi);
        HikvisionStreamSession first = service.realPlay(
                device(), play(0, "0"), ignored -> { });

        assertThatThrownBy(() -> service.realPlay(device(), play(0, "0"), ignored -> { }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Hikvision SDK returned a duplicate stream handle");
        first.close();
        service.close();

        assertThat(nativeApi.events).containsExactly(
                "login:42", "preview:start:42", "login:43", "preview:start:43",
                "preview:stop:101", "logout:42", "logout:43", "cleanup");
    }

    @Test
    void logsOutWhenNativeLinkageFailsDuringPreviewStartup() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.previewLinkageFailure = true;
        HikvisionCameraSdkService service = service(nativeApi);

        assertThatThrownBy(() -> service.realPlay(device(), play(0, "0"), ignored -> { }))
                .isInstanceOf(UnsatisfiedLinkError.class);
        assertThat(nativeApi.events).containsExactly(
                "login", "preview:start:42", "logout:42");
        service.close();
    }

    @Test
    void retainsPartiallyStartedPlaybackForServiceCloseRetry() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.playbackStartCleanupFailure = true;
        HikvisionCameraSdkService service = service(nativeApi);
        PlayDomain request = play(0, "0").setPlaybackParam(new PlayDomain.PlaybackParam()
                .setBeginTime(LocalDateTime.of(2026, 8, 12, 10, 0))
                .setEndTime(LocalDateTime.of(2026, 8, 12, 10, 30)));

        assertThatThrownBy(() -> service.playback(device(), request, ignored -> { }))
                .isInstanceOf(HikvisionSdkException.class)
                .hasMessage("Hikvision playback startup cleanup failed (code=0)");
        assertThat(nativeApi.events).containsExactly("login", "playback:start:42");

        service.close();

        assertThat(nativeApi.events).containsExactly(
                "login", "playback:start:42", "playback:stop:202", "logout:42", "cleanup");
    }

    @Test
    void sameNumericHandleFromDifferentStreamTypesRemainsIndependent() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.incrementLoginHandle = true;
        nativeApi.playbackHandle = nativeApi.previewHandle;
        HikvisionCameraSdkService service = service(nativeApi);
        HikvisionStreamSession preview = service.realPlay(
                device(), play(0, "0"), ignored -> { });
        PlayDomain playbackRequest = play(0, "0").setPlaybackParam(
                new PlayDomain.PlaybackParam()
                        .setBeginTime(LocalDateTime.of(2026, 8, 12, 10, 0))
                        .setEndTime(LocalDateTime.of(2026, 8, 12, 10, 30)));

        HikvisionStreamSession playback = service.playback(
                device(), playbackRequest, ignored -> { });
        preview.close();
        playback.close();
        service.close();

        assertThat(nativeApi.events).containsExactly(
                "login:42", "preview:start:42", "login:43", "playback:start:43",
                "preview:stop:101", "logout:42", "playback:stop:101", "logout:43",
                "cleanup");
    }

    private static PlayDomain play(int streamType, String protocolType) {
        return new PlayDomain().setTakeStreamParam(new PlayDomain.TakeStreamParam()
                .setStreamType(streamType).setByProtoType(protocolType));
    }

    private static DeviceDomain device() {
        return new DeviceDomain().setIp("192.0.2.10").setPort("8000")
                .setUsername("operator").setPassword("secret");
    }

    private HikvisionCameraSdkService service(FakeNativeApi nativeApi) {
        HikvisionCameraSdkService service = HikvisionCameraSdkService.createForTesting(
                HikvisionSdkRuntime.openForTesting(
                        HikvisionSdkOptions.defaults(Path.of("sdk")), nativeApi));
        fixtures.add(new ServiceFixture(nativeApi, service));
        return service;
    }

    private record ServiceFixture(
            FakeNativeApi nativeApi, HikvisionCameraSdkService service) {
    }

    private static final class FakeNativeApi implements HikvisionNativeApi {
        private final List<String> events = new ArrayList<>();
        private long previewHandle = 101L;
        private long playbackHandle = 202L;
        private HikvisionPreviewRequest previewRequest;
        private HikvisionPlaybackRequest playbackRequest;
        private HikvisionNativeStreamCallback callback;
        private boolean stopPreviewResult = true;
        private boolean playbackStartCleanupFailure;
        private boolean previewLinkageFailure;
        private boolean incrementLoginHandle;
        private int loginCalls;

        @Override
        public boolean initialize() {
            return true;
        }

        @Override
        public boolean cleanup() {
            events.add("cleanup");
            return true;
        }

        @Override
        public int lastError() {
            return 0;
        }

        @Override
        public HikvisionNativeLoginResult login(LoginDomain login) {
            int handle = incrementLoginHandle ? 42 + loginCalls : 42;
            loginCalls++;
            events.add(incrementLoginHandle ? "login:" + handle : "login");
            return new HikvisionNativeLoginResult(handle, 33, 71, 8, "serial-01");
        }

        @Override
        public boolean logout(int userId) {
            events.add("logout:" + userId);
            return true;
        }

        @Override
        public long startRealPlay(
                int userId, HikvisionPreviewRequest request,
                HikvisionNativeStreamCallback streamCallback) {
            events.add("preview:start:" + userId);
            if (previewLinkageFailure) {
                throw new UnsatisfiedLinkError("missing preview symbol");
            }
            previewRequest = request;
            callback = streamCallback;
            return previewHandle;
        }

        @Override
        public boolean stopRealPlay(long streamHandle) {
            events.add("preview:stop:" + streamHandle);
            return stopPreviewResult;
        }

        @Override
        public long startPlayback(
                int userId, HikvisionPlaybackRequest request,
                HikvisionNativeStreamCallback streamCallback) {
            events.add("playback:start:" + userId);
            playbackRequest = request;
            callback = streamCallback;
            if (playbackStartCleanupFailure) {
                throw new HikvisionNativeStreamStartException(playbackHandle);
            }
            return playbackHandle;
        }

        @Override
        public boolean stopPlayback(long streamHandle) {
            events.add("playback:stop:" + streamHandle);
            return true;
        }
    }
}
