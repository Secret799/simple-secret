package com.ss.easymedia.h264;

import com.aizuda.zlm4j.core.ZLMApi;
import com.aizuda.zlm4j.structure.MK_FRAME;
import com.aizuda.zlm4j.structure.MK_INI;
import com.aizuda.zlm4j.structure.MK_MEDIA;
import com.ss.zlm4j.config.properties.ZlmMediaProperties;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class H264NakedFlowPushZlmManagerTest {

    @Test
    void shouldRejectNonPositiveProcessQueueSizeAtConstructionTime() {
        ZLMApi zlmApi = mock(ZLMApi.class);

        assertThatThrownBy(() -> new H264NakedFlowPushZlmManager(
                new ZlmMediaProperties(), 30, 0, zlmApi))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("processQueueSize");
    }

    @Test
    void shouldReleaseNativeMediaExactlyOnceWhenStreamStopped() throws Exception {
        ZLMApi zlmApi = mock(ZLMApi.class);
        MK_INI mkIni = mock(MK_INI.class);
        MK_MEDIA mkMedia = mock(MK_MEDIA.class);
        when(zlmApi.mk_ini_create()).thenReturn(mkIni);
        when(zlmApi.mk_media_create2("__defaultVhost__", "live", "camera-1", 0, mkIni))
                .thenReturn(mkMedia);
        H264NakedFlowPushZlmManager manager = new H264NakedFlowPushZlmManager(
                new ZlmMediaProperties(), 30, 10, zlmApi);

        manager.push("live", "camera-1", new byte[0]);
        manager.stopPush("live", "camera-1");
        manager.stopPush("live", "camera-1");

        verify(zlmApi, times(1)).mk_media_release(mkMedia);
        manager.close();
    }

    @Test
    void shouldReleaseActiveStreamsWhenManagerClosed() throws Exception {
        ZLMApi zlmApi = mock(ZLMApi.class);
        MK_INI mkIni = mock(MK_INI.class);
        MK_MEDIA mkMedia = mock(MK_MEDIA.class);
        when(zlmApi.mk_ini_create()).thenReturn(mkIni);
        when(zlmApi.mk_media_create2("__defaultVhost__", "live", "camera-2", 0, mkIni))
                .thenReturn(mkMedia);
        H264NakedFlowPushZlmManager manager = new H264NakedFlowPushZlmManager(
                new ZlmMediaProperties(), 30, 10, zlmApi);

        manager.push("live", "camera-2", new byte[0]);
        manager.close();

        verify(zlmApi, times(1)).mk_media_release(mkMedia);
    }

    @Test
    void shouldUnrefEveryNativeFrameAfterInput() throws Exception {
        ZLMApi zlmApi = mock(ZLMApi.class);
        MK_INI mkIni = mock(MK_INI.class);
        MK_MEDIA mkMedia = mock(MK_MEDIA.class);
        MK_FRAME frame = mock(MK_FRAME.class);
        when(zlmApi.mk_ini_create()).thenReturn(mkIni);
        when(zlmApi.mk_media_create2("__defaultVhost__", "live", "camera-3", 0, mkIni))
                .thenReturn(mkMedia);
        when(zlmApi.mk_frame_create(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(frame);
        H264NakedFlowPushZlmManager manager = new H264NakedFlowPushZlmManager(
                new ZlmMediaProperties(), 30, 10, zlmApi);

        manager.push("live", "camera-3", completeNalUnitFragment());

        verify(zlmApi, timeout(Duration.ofSeconds(1).toMillis()))
                .mk_media_input_frame(mkMedia, frame);
        verify(zlmApi, timeout(Duration.ofSeconds(1).toMillis()))
                .mk_frame_unref(frame);
        manager.close();
    }

    @Test
    void shouldNotReleaseMediaUntilInFlightFrameInputCompletes() throws Exception {
        ZLMApi zlmApi = mock(ZLMApi.class);
        MK_INI mkIni = mock(MK_INI.class);
        MK_MEDIA mkMedia = mock(MK_MEDIA.class);
        MK_FRAME frame = mock(MK_FRAME.class);
        CountDownLatch inputStarted = new CountDownLatch(1);
        CountDownLatch allowInputToFinish = new CountDownLatch(1);
        when(zlmApi.mk_ini_create()).thenReturn(mkIni);
        when(zlmApi.mk_media_create2("__defaultVhost__", "live", "camera-4", 0, mkIni))
                .thenReturn(mkMedia);
        when(zlmApi.mk_frame_create(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(frame);
        when(zlmApi.mk_media_input_frame(mkMedia, frame)).thenAnswer(invocation -> {
            inputStarted.countDown();
            boolean interrupted = false;
            while (allowInputToFinish.getCount() > 0) {
                try {
                    allowInputToFinish.await(20, TimeUnit.MILLISECONDS);
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
            return 1;
        });
        H264NakedFlowPushZlmManager manager = new H264NakedFlowPushZlmManager(
                new ZlmMediaProperties(), 30, 10, zlmApi);
        manager.push("live", "camera-4", completeNalUnitFragment());
        assertThat(inputStarted.await(1, TimeUnit.SECONDS)).isTrue();

        Thread stopThread = new Thread(() -> manager.stopPush("live", "camera-4"));
        stopThread.start();
        Thread.sleep(100);

        verify(zlmApi, never()).mk_media_release(mkMedia);
        allowInputToFinish.countDown();
        stopThread.join(2_000);
        assertThat(stopThread.isAlive()).isFalse();
        InOrder order = inOrder(zlmApi);
        order.verify(zlmApi).mk_media_input_frame(mkMedia, frame);
        order.verify(zlmApi).mk_frame_unref(frame);
        order.verify(zlmApi).mk_media_release(mkMedia);
        manager.close();
    }

    @Test
    void backpressurePushShouldWaitUntilFragmentProcessingCompletes() throws Exception {
        ZLMApi zlmApi = mock(ZLMApi.class);
        MK_INI mkIni = mock(MK_INI.class);
        MK_MEDIA mkMedia = mock(MK_MEDIA.class);
        MK_FRAME frame = mock(MK_FRAME.class);
        CountDownLatch inputStarted = new CountDownLatch(1);
        CountDownLatch allowInputToFinish = new CountDownLatch(1);
        AtomicBoolean pushFinished = new AtomicBoolean(false);
        when(zlmApi.mk_ini_create()).thenReturn(mkIni);
        when(zlmApi.mk_media_create2("__defaultVhost__", "live", "camera-bp", 0, mkIni))
                .thenReturn(mkMedia);
        when(zlmApi.mk_frame_create(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(frame);
        when(zlmApi.mk_media_input_frame(mkMedia, frame)).thenAnswer(invocation -> {
            inputStarted.countDown();
            allowInputToFinish.await(1, TimeUnit.SECONDS);
            return 1;
        });
        H264NakedFlowPushZlmManager manager = new H264NakedFlowPushZlmManager(
                new ZlmMediaProperties(), 30, 10, zlmApi);
        Thread pushThread = new Thread(() -> {
            try {
                manager.pushWithBackpressure(
                        "live", "camera-bp", completeNalUnitFragment());
                pushFinished.set(true);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });

        pushThread.start();
        assertThat(inputStarted.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(pushFinished.get()).isFalse();
        allowInputToFinish.countDown();
        pushThread.join(1_000);

        assertThat(pushFinished.get()).isTrue();
        manager.close();
    }

    @Test
    void shouldNotInputFrameWhenNativeFrameCreationFails() throws Exception {
        ZLMApi zlmApi = mock(ZLMApi.class);
        MK_INI mkIni = mock(MK_INI.class);
        MK_MEDIA mkMedia = mock(MK_MEDIA.class);
        when(zlmApi.mk_ini_create()).thenReturn(mkIni);
        when(zlmApi.mk_media_create2("__defaultVhost__", "live", "camera-5", 0, mkIni))
                .thenReturn(mkMedia);
        H264NakedFlowPushZlmManager manager = new H264NakedFlowPushZlmManager(
                new ZlmMediaProperties(), 30, 10, zlmApi);

        manager.push("live", "camera-5", completeNalUnitFragment());

        verify(zlmApi, timeout(1_000)).mk_frame_create(
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any());
        verify(zlmApi, never()).mk_media_input_frame(org.mockito.ArgumentMatchers.eq(mkMedia), any());
        manager.close();
    }

    private static byte[] completeNalUnitFragment() {
        return new byte[]{0, 0, 0, 1, 0x65, 0, 0, 0, 1, 0x41};
    }
}
