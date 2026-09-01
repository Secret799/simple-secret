package com.ss.dji.camera.web;

import com.ss.dji.camera.config.DjiSeiProperties;
import com.ss.dji.camera.event.DjiSeiPacketParsedEvent;
import com.ss.dji.camera.parser.SeiMessage;
import com.ss.dji.camera.parser.SeiParseIssue;
import com.ss.dji.camera.parser.SeiParseResult;
import com.ss.dji.camera.parser.VideoCodec;
import com.ss.easymedia.callback.TrackDelegateCallback.TackDelegateInfo;
import com.ss.zlm4j.domain.MediaSourceDomain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

class DjiSeiSseServiceTest {

    private DjiSeiSseService service;

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.close();
        }
    }

    @Test
    void shouldPushOnlySubscribedDeviceAndExposeCompletePayload() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        service = service(() -> emitter);
        service.subscribeDevice("dock-01");

        DjiSeiPacketParsedEvent event = event("live", "dock-01", userDataPayload());
        service.onFrame(source("live", "dock-01"), frame(), VideoCodec.H264,
                result(userDataPayload(), List.of()));
        service.onFrame(source("other", "dock-01"), frame(), VideoCodec.H264,
                result(new byte[]{1}, List.of()));
        service.onFrame(source("live", "dock-02"), frame(), VideoCodec.H264,
                result(new byte[]{2}, List.of()));

        verify(emitter, timeout(2_000).atLeast(2)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(service.subscriberCount("dock-01")).isEqualTo(1);
        assertThat(service.subscriberCount("dock-02")).isZero();

        DjiSeiSseService.SeiEvent payload = DjiSeiSseService.SeiEvent.from(event);
        assertThat(payload.deviceId()).isEqualTo("dock-01");
        assertThat(payload.uuid()).isEqualTo(UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"));
        assertThat(payload.data()).isEqualTo("{\"height\":120,\"status\":\"飞行\"}");
        assertThat(payload.payloadBase64()).isEqualTo(Base64.getEncoder().encodeToString(userDataPayload()));
        assertThat(payload.payloadBytes()).isEqualTo(userDataPayload().length);
    }

    @Test
    void shouldReturnNullTextForBinaryPayload() {
        service = service(() -> mock(SseEmitter.class));

        DjiSeiSseService.SeiEvent payload = DjiSeiSseService.SeiEvent.from(
                event("live", "dock-01", new byte[]{(byte) 0xC3, 0x28}));

        assertThat(payload.data()).isNull();
        assertThat(payload.payloadBase64()).isEqualTo("wyg=");
    }

    @Test
    void shouldLimitSubscribersPerDevice() throws Exception {
        List<SseEmitter> emitters = new ArrayList<>();
        service = service(() -> {
            SseEmitter emitter = mock(SseEmitter.class);
            emitters.add(emitter);
            return emitter;
        });
        for (int index = 0; index < 16; index++) {
            service.subscribeDevice("dock-01");
        }

        assertThatThrownBy(() -> service.subscribeDevice("dock-01"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("subscriber limit");
        assertThat(service.subscriberCount("dock-01")).isEqualTo(16);
        verify(emitters.get(0), atLeast(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void shouldMaintainLegacyDiagnosticsSnapshotWithSeiAndIssues() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        service = service(() -> emitter);
        service.subscribeDiagnostics("live", "dock-01");
        service.onStreamRegistered(source("live", "dock-01"));
        service.onFrame(source("live", "dock-01"), frame(), VideoCodec.H264,
                result(userDataPayload(),
                        List.of(new SeiParseIssue("TRUNCATED_PAYLOAD", "payload is truncated"))));

        DjiSeiSseService.InitialSnapshot snapshot = awaitMessages("live", "dock-01", 1);

        assertThat(snapshot.stats().active()).isTrue();
        assertThat(snapshot.stats().videoFrames()).isEqualTo(1);
        assertThat(snapshot.stats().seiMessages()).isEqualTo(1);
        assertThat(snapshot.stats().parseIssues()).isEqualTo(1);
        assertThat(snapshot.recentEvents()).extracting(DjiSeiSseService.DiagnosticEvent::kind)
                .containsExactly("sei", "issue");
        DjiSeiSseService.DiagnosticEvent sei = snapshot.recentEvents().get(0);
        assertThat(sei.text()).isEqualTo("{\"height\":120,\"status\":\"飞行\"}");
        assertThat(sei.payloadBase64())
                .isEqualTo(Base64.getEncoder().encodeToString(userDataPayload()));
        verify(emitter, timeout(2_000).atLeast(4)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void shouldUseProductionConstructorInSpringContext() {
        DjiSeiProperties properties = new DjiSeiProperties();
        properties.setEnabled(true);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("sse-test", Map.of("simple-secret.dji-sei.enabled", "true")));
            context.registerBean(DjiSeiProperties.class, () -> properties);
            context.registerBean(Clock.class, () -> Clock.systemUTC());
            context.register(DjiSeiSseService.class);
            context.refresh();

            assertThat(context.getBean(DjiSeiSseService.class)).isNotNull();
        }
    }

    private static DjiSeiSseService service(DjiSeiSseService.EmitterFactory emitterFactory) {
        DjiSeiProperties properties = new DjiSeiProperties();
        properties.setAllowedApp("live");
        return new DjiSeiSseService(properties,
                Clock.fixed(Instant.parse("2026-08-28T02:00:00Z"), ZoneOffset.UTC), emitterFactory);
    }

    private static DjiSeiPacketParsedEvent event(String app, String stream, byte[] payload) {
        MediaSourceDomain source = source(app, stream);
        return new DjiSeiPacketParsedEvent(source, frame(), VideoCodec.H264,
                new SeiMessage(payload.length >= 16 ? 5 : 4, payload),
                Instant.parse("2026-08-28T02:00:01Z"));
    }

    private static MediaSourceDomain source(String app, String stream) {
        MediaSourceDomain source = new MediaSourceDomain();
        source.setSchema("rtmp");
        source.setApp(app);
        source.setStream(stream);
        source.setVhost("__defaultVhost__");
        return source;
    }

    private static TackDelegateInfo frame() {
        return new TackDelegateInfo().setPts(1200L).setDts(1160L);
    }

    private static SeiParseResult result(byte[] payload, List<SeiParseIssue> issues) {
        return new SeiParseResult(
                List.of(new SeiMessage(payload.length >= 16 ? 5 : 4, payload)), 1, issues);
    }

    private DjiSeiSseService.InitialSnapshot awaitMessages(
            String app, String stream, long expectedMessages) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        DjiSeiSseService.InitialSnapshot snapshot;
        do {
            snapshot = service.snapshot(app, stream);
            if (snapshot.stats().seiMessages() >= expectedMessages) {
                return snapshot;
            }
            Thread.sleep(10L);
        } while (System.nanoTime() < deadline);
        return snapshot;
    }

    private static byte[] userDataPayload() {
        UUID uuid = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
        byte[] json = "{\"height\":120,\"status\":\"飞行\"}".getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[16 + json.length];
        putLong(payload, 0, uuid.getMostSignificantBits());
        putLong(payload, 8, uuid.getLeastSignificantBits());
        System.arraycopy(json, 0, payload, 16, json.length);
        return payload;
    }

    private static void putLong(byte[] target, int offset, long value) {
        for (int index = 7; index >= 0; index--) {
            target[offset + index] = (byte) value;
            value >>>= 8;
        }
    }
}
