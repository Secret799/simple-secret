package com.ss.ics.dahua;

import com.ss.ics.domain.DeviceDomain;
import com.ss.ics.domain.PlayDomain;
import com.ss.ics.dahua.internal.model.DahuaNativeStreamFrame;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DahuaCameraSdkServicePreviewTest {

    @Test
    void startsPreviewAndClosesItBeforeLogout() {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();
        DahuaCameraSdkService service = service(nativeApi);
        List<DahuaStreamFrame> frames = new ArrayList<>();

        DahuaRealPlaySession session = service.realPlay(
                device().setChannel("1"), play(1), frames::add);
        byte[] source = new byte[]{0, 0, 0, 1, 0x67};
        nativeApi.streamCallback.onFrame(new DahuaNativeStreamFrame(source, 10L, 9L, 1, 2));
        source[4] = 0x68;

        assertThat(session.handle()).isEqualTo(99L);
        assertThat(frames).singleElement().satisfies(frame -> {
            assertThat(frame.data()).containsExactly(0, 0, 0, 1, 0x67);
            assertThat(frame.pts()).isEqualTo(10L);
            assertThat(frame.dts()).isEqualTo(9L);
        });
        session.close();
        session.close();
        service.close();
        assertThat(nativeApi.events).containsExactly(
                "login", "preview:start:42:0:1", "preview:stop:99", "logout:42", "cleanup");
    }

    @Test
    void logsOutWhenPreviewStartFails() {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();
        nativeApi.previewHandle = 0L;
        DahuaCameraSdkService service = service(nativeApi);

        assertThatThrownBy(() -> service.realPlay(device(), play(0), ignored -> { }))
                .isInstanceOf(DahuaSdkException.class)
                .hasMessage("Dahua real-time preview failed (code=0)");
        assertThat(nativeApi.events).containsExactly(
                "login", "preview:start:42:0:0", "logout:42");
        service.close();
    }

    @Test
    void serviceCloseStopsOpenPreviewBeforeRuntimeCleanup() {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();
        DahuaCameraSdkService service = service(nativeApi);
        service.realPlay(device(), play(0), ignored -> { });

        service.close();

        assertThat(nativeApi.events).containsExactly(
                "login", "preview:start:42:0:0", "preview:stop:99", "logout:42", "cleanup");
    }

    @Test
    void duplicatePreviewHandleInvalidatesNativeResourceAndBothLogins() {
        FakeDahuaNativeApi nativeApi = new FakeDahuaNativeApi();
        DahuaCameraSdkService service = service(nativeApi);
        DahuaRealPlaySession first = service.realPlay(device(), play(0), ignored -> { });

        try {
            assertThatThrownBy(() -> service.realPlay(device(), play(0), ignored -> { }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Dahua SDK returned a duplicate preview handle");
            assertThat(nativeApi.events).containsExactly(
                    "login", "preview:start:42:0:0",
                    "login", "preview:start:42:0:0",
                    "preview:stop:99", "logout:42", "logout:42");
        } finally {
            first.close();
            service.close();
        }
        assertThat(nativeApi.events).endsWith("cleanup");
    }

    private static PlayDomain play(int streamType) {
        return new PlayDomain().setTakeStreamParam(
                new PlayDomain.TakeStreamParam().setStreamType(streamType));
    }

    private static DeviceDomain device() {
        return new DeviceDomain().setIp("192.0.2.10").setPort("37777")
                .setUsername("operator").setPassword("secret");
    }

    private static DahuaCameraSdkService service(FakeDahuaNativeApi nativeApi) {
        return DahuaCameraSdkService.createForTesting(DahuaSdkRuntime.openForTesting(
                DahuaSdkOptions.defaults(Path.of("sdk")), nativeApi));
    }
}
