package com.ss.consumer.gb28181;

import com.ss.gb28181.CatalogItem;
import com.ss.gb28181.DeviceCredentials;
import com.ss.gb28181.Gb28181Server;
import com.ss.gb28181.GbGuardCommand;
import com.ss.gb28181.GbRecordControlCommand;
import com.ss.gb28181.GbTeleBootCommand;
import com.ss.gb28181.GbAlarm;
import com.ss.gb28181.GbAlarmResetCommand;
import com.ss.gb28181.GbAlarmEvent;
import com.ss.gb28181.GbAlarmListener;
import com.ss.gb28181.GbAlarmSubscription;
import com.ss.gb28181.GbAlarmSubscriptionRequest;
import com.ss.gb28181.GbCatalogEvent;
import com.ss.gb28181.GbCatalogListener;
import com.ss.gb28181.GbCatalogNotification;
import com.ss.gb28181.GbCatalogSubscription;
import com.ss.gb28181.GbCatalogSubscriptionRequest;
import com.ss.gb28181.GbDownloadRequest;
import com.ss.gb28181.GbMobilePosition;
import com.ss.gb28181.GbMobilePositionEvent;
import com.ss.gb28181.GbMobilePositionListener;
import com.ss.gb28181.GbMobilePositionNotification;
import com.ss.gb28181.GbMobilePositionSubscription;
import com.ss.gb28181.GbMobilePositionSubscriptionRequest;
import com.ss.gb28181.GbServerOptions;
import com.ss.gb28181.GbRtpTarget;
import com.ss.gb28181.GbPlaySession;
import com.ss.gb28181.GbPlaybackControl;
import com.ss.gb28181.GbPlaybackRange;
import com.ss.gb28181.PtzCommand;
import com.ss.gb28181.PresetCommand;
import com.ss.gb28181.PresetItem;
import com.ss.gb28181.GbDragZoomCommand;
import com.ss.gb28181.DeviceInfo;
import com.ss.gb28181.DeviceStatus;
import com.ss.gb28181.HomePosition;
import com.ss.gb28181.PtzPosition;
import com.ss.gb28181.RecordItem;
import com.ss.gb28181.RecordQuery;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.Instant;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class Gb28181PluginConsumerTest {
    @Test void exposesHomePositionQueryApi() throws Exception {
        assertEquals(new HomePosition("34020000001320000001", true, 30, 2),
                new HomePosition("34020000001320000001", true, 30, 2));
        assertNotNull(Gb28181Server.class.getMethod("queryHomePosition", String.class));
    }
    @Test void exposesPtzPositionQueryApi() throws Exception {
        assertEquals(new PtzPosition("34020000001320000001", 1.0, null, 2.0),
                new PtzPosition("34020000001320000001", 1.0, null, 2.0));
        assertNotNull(Gb28181Server.class.getMethod("queryPtzPosition", String.class));
        assertNotNull(Gb28181Server.class.getMethod("queryPtzPosition", String.class, String.class));
    }
    @Test
    void exposesDragZoomApi() throws Exception {
        assertEquals(new GbDragZoomCommand(1, 2, 3, 4, 5, 6), new GbDragZoomCommand(1, 2, 3, 4, 5, 6));
        assertNotNull(Gb28181Server.class.getMethod("dragZoomIn", String.class, String.class, GbDragZoomCommand.class));
        assertNotNull(Gb28181Server.class.getMethod("dragZoomOut", String.class, String.class, GbDragZoomCommand.class));
    }
    @Test
    void exposesDeviceControlOperations() throws Exception {
        assertNotNull(GbTeleBootCommand.valueOf("BOOT"));
        assertNotNull(GbRecordControlCommand.valueOf("RECORD"));
        assertNotNull(GbGuardCommand.valueOf("SET_GUARD"));
        assertNotNull(Gb28181Server.class.getMethod("teleBoot", String.class));
        assertNotNull(Gb28181Server.class.getMethod("controlRecording", String.class, String.class,
                GbRecordControlCommand.class));
        assertNotNull(Gb28181Server.class.getMethod("controlGuard", String.class, String.class,
                GbGuardCommand.class));
    }
    @Test
    void exposesAlarmResetAndKeyFrameControls() throws Exception {
        GbAlarmResetCommand all = GbAlarmResetCommand.all();
        var source = new java.util.HashSet<>(Set.of(2));
        GbAlarmResetCommand filtered = new GbAlarmResetCommand(source, 5);
        source.clear();

        assertEquals(Set.of(), all.methods());
        assertNull(all.type());
        assertEquals(Set.of(2), filtered.methods());
        assertEquals(5, filtered.type());
        assertThrows(UnsupportedOperationException.class, () -> filtered.methods().add(3));
        assertEquals(CompletableFuture.class.getName() + "<" + Void.class.getName() + ">",
                Gb28181Server.class.getMethod("resetAlarm", String.class, String.class,
                        GbAlarmResetCommand.class).getGenericReturnType().getTypeName());
        assertEquals(CompletableFuture.class.getName() + "<" + Void.class.getName() + ">",
                Gb28181Server.class.getMethod("requestKeyFrame", String.class, String.class)
                        .getGenericReturnType().getTypeName());
    }

    @Test
    void exposesMobilePositionModelsListenerSubscriptionAndFiveArgumentOpen() throws Exception {
        GbMobilePositionSubscriptionRequest request =
                new GbMobilePositionSubscriptionRequest(Duration.ofMinutes(5));
        GbMobilePosition position = new GbMobilePosition("34020000001320000001",
                "2026-09-15T10:30:00+08:00", 121.5, 31.2, 12.5, 90.0, 8.0, 3.0);
        GbMobilePositionNotification notification = new GbMobilePositionNotification(
                "34020000002000000001", 7, "2026-09-15T10:30:01+08:00", 1, List.of(position));
        GbMobilePositionEvent event = new GbMobilePositionEvent("34020000002000000001", "position-call",
                Instant.parse("2026-09-15T02:30:02Z"), notification);
        AtomicReference<GbMobilePositionEvent> received = new AtomicReference<>();
        GbMobilePositionListener listener = received::set;

        listener.onMobilePosition(event);

        assertSame(event, received.get());
        assertEquals(Duration.ofSeconds(5), request.interval());
        assertEquals(position, event.notification().positions().get(0));
        assertEquals(43, GbServerOptions.builder().serverId("34020000002000000001")
                .realm("example.test").maxMobilePositionSubscriptions(31)
                .maxPendingMobilePositionNotifications(37).maxMobilePositionItems(43)
                .build().maxMobilePositionItems());
        assertEquals(CompletableFuture.class.getName() + "<" + GbMobilePositionSubscription.class.getName() + ">",
                Gb28181Server.class.getMethod("subscribeMobilePosition", String.class,
                        GbMobilePositionSubscriptionRequest.class).getGenericReturnType().getTypeName());
        assertEquals(CompletableFuture.class.getName() + "<" + Void.class.getName() + ">",
                GbMobilePositionSubscription.class.getMethod("stop").getGenericReturnType().getTypeName());
        assertEquals(String.class, GbMobilePositionSubscription.class.getMethod("deviceId").getReturnType());
        assertEquals(String.class, GbMobilePositionSubscription.class.getMethod("callId").getReturnType());
        assertEquals(GbMobilePositionSubscriptionRequest.class,
                GbMobilePositionSubscription.class.getMethod("request").getReturnType());
        assertEquals(CompletionStage.class.getName() + "<" + Void.class.getName() + ">",
                GbMobilePositionSubscription.class.getMethod("completion").getGenericReturnType().getTypeName());
        assertTrue(AutoCloseable.class.isAssignableFrom(GbMobilePositionSubscription.class));
        assertNotNull(Gb28181Server.class.getMethod("open", GbServerOptions.class, DeviceCredentials.class,
                GbAlarmListener.class, GbCatalogListener.class, GbMobilePositionListener.class));
    }

    @Test
    void exposesCatalogSubscriptionApiAndPreservesOldOptionsConstructor() throws Exception {
        GbCatalogSubscriptionRequest request = GbCatalogSubscriptionRequest.all(Duration.ofMinutes(5));
        CatalogItem item = new CatalogItem("34020000001320000001", "camera",
                "34020000002000000001", "ON");
        GbCatalogNotification notification = new GbCatalogNotification("34020000002000000001", 7, 1,
                List.of(new GbCatalogNotification.Entry(item, GbCatalogNotification.Type.UPDATE)));
        GbCatalogEvent event = new GbCatalogEvent("34020000002000000001", "catalog-call",
                Instant.parse("2026-09-15T02:30:00Z"), notification);
        GbCatalogListener catalogListener = received -> { };

        assertEquals(GbCatalogNotification.Type.UPDATE, event.notification().entries().get(0).type());
        assertEquals("catalog-call", event.callId());
        assertEquals(31, GbServerOptions.builder().serverId("34020000002000000001")
                .realm("example.test").maxCatalogSubscriptions(31)
                .maxPendingCatalogNotifications(37).build().maxCatalogSubscriptions());
        assertEquals(CompletableFuture.class.getName() + "<" + GbCatalogSubscription.class.getName() + ">",
                Gb28181Server.class.getMethod("subscribeCatalog", String.class,
                        GbCatalogSubscriptionRequest.class).getGenericReturnType().getTypeName());
        assertEquals(CompletableFuture.class.getName() + "<" + Void.class.getName() + ">",
                GbCatalogSubscription.class.getMethod("stop").getGenericReturnType().getTypeName());
        assertTrue(AutoCloseable.class.isAssignableFrom(GbCatalogSubscription.class));
        assertNotNull(Gb28181Server.class.getMethod("open", GbServerOptions.class,
                DeviceCredentials.class, GbAlarmListener.class, GbCatalogListener.class));
        assertNotNull(catalogListener);

        GbServerOptions oldOptions = new GbServerOptions("34020000002000000001", "example.test",
                "127.0.0.1", "127.0.0.1", 5060, Duration.ofDays(1), Duration.ofSeconds(60), 3,
                Duration.ofSeconds(10), 1000, 256, 10000, 65536, Duration.ofSeconds(10),
                Duration.ofSeconds(5), 256, 10000, 256, 256);
        assertEquals(256, oldOptions.maxCatalogSubscriptions());
        assertEquals(256, oldOptions.maxPendingCatalogNotifications());
    }

    @Test
    void exposesPresetQueryAndControlWithoutChangingPtzApi() throws Exception {
        assertEquals("A50F018200010038", PresetCommand.goTo(1).hex());
        assertEquals(PresetCommand.Action.SET, PresetCommand.set(255).action());
        assertEquals(PresetCommand.Action.REMOVE, PresetCommand.remove(1).action());
        assertEquals("001", new PresetItem("001", "Gate").presetId());
        assertEquals("", new PresetItem("vendor-id", "").presetName());
        assertEquals(CompletableFuture.class.getName() + "<" + List.class.getName()
                        + "<" + PresetItem.class.getName() + ">>",
                Gb28181Server.class.getMethod("queryPresets", String.class, String.class)
                        .getGenericReturnType().getTypeName());
        assertEquals(CompletableFuture.class.getName() + "<" + Void.class.getName() + ">",
                Gb28181Server.class.getMethod("preset", String.class, String.class, PresetCommand.class)
                        .getGenericReturnType().getTypeName());
        assertEquals("A50F0100000000B5", PtzCommand.stop().hex());
    }

    @Test
    void exposesAlarmSubscriptionApiAndCapacity() throws Exception {
        GbAlarmSubscriptionRequest request = GbAlarmSubscriptionRequest.all(Duration.ofMinutes(5));

        assertEquals(Duration.ofMinutes(5), request.expires());
        assertEquals(Set.of(), request.methods());
        assertEquals(31, GbServerOptions.builder().serverId("34020000002000000001")
                .realm("example.test").maxAlarmSubscriptions(31).build().maxAlarmSubscriptions());
        assertEquals(CompletableFuture.class.getName() + "<" + GbAlarmSubscription.class.getName() + ">",
                Gb28181Server.class.getMethod("subscribeAlarms", String.class, String.class,
                        GbAlarmSubscriptionRequest.class).getGenericReturnType().getTypeName());
        assertEquals(CompletableFuture.class.getName() + "<" + Void.class.getName() + ">",
                GbAlarmSubscription.class.getMethod("stop").getGenericReturnType().getTypeName());
        assertTrue(AutoCloseable.class.isAssignableFrom(GbAlarmSubscription.class));
    }

    @Test
    void exposesAlarmApiAndCapacity() throws Exception {
        GbAlarm alarm = new GbAlarm("34020000001320000001", 7, 1, 2,
                "2026-09-15T10:30:00+08:00", "motion", 121.5, 31.2, 2, 1);
        GbAlarmEvent event = new GbAlarmEvent("34020000002000000002",
                Instant.parse("2026-09-15T02:30:00Z"), alarm);
        GbAlarmListener listener = received -> { };

        assertEquals(alarm, event.alarm());
        assertEquals("34020000002000000002", event.sourceDeviceId());
        assertTrue(Modifier.isPublic(GbAlarmListener.class.getModifiers()));
        assertEquals(17, GbServerOptions.builder().serverId("34020000002000000001")
                .realm("example.test").maxPendingAlarms(17).build().maxPendingAlarms());
        assertNotNull(Gb28181Server.class.getMethod("open", GbServerOptions.class,
                DeviceCredentials.class, GbAlarmListener.class));
        assertNotNull(listener);
    }

    @Test
    void exposesDownloadRequestAndSessionMetadata() throws Exception {
        Instant start = Instant.parse("2026-09-15T00:00:00Z");
        GbPlaybackRange range = new GbPlaybackRange(start, start.plusSeconds(3600));
        assertSame(range, GbDownloadRequest.normal(range).range());
        assertEquals(1, GbDownloadRequest.normal(range).speed());
        assertEquals(128, new GbDownloadRequest(range, 128).speed());
        assertThrows(NullPointerException.class, () -> GbDownloadRequest.normal(null));
        assertThrows(IllegalArgumentException.class, () -> new GbDownloadRequest(range, 0));
        assertThrows(IllegalArgumentException.class, () -> new GbDownloadRequest(range, 129));
        assertEquals(CompletableFuture.class.getName() + "<" + GbPlaySession.class.getName() + ">",
                Gb28181Server.class.getMethod("download", String.class, String.class,
                        GbRtpTarget.class, GbDownloadRequest.class).getGenericReturnType().getTypeName());
        assertEquals(Optional.class.getName() + "<" + GbDownloadRequest.class.getName() + ">",
                GbPlaySession.class.getMethod("downloadRequest").getGenericReturnType().getTypeName());
        assertEquals(OptionalLong.class, GbPlaySession.class.getMethod("downloadFileSize").getReturnType());
    }

    @Test void exposesHistoryPlaybackAndValidatedControlModels() throws Exception {
        Instant start = Instant.parse("2026-09-15T00:00:00Z");
        GbPlaybackRange range = new GbPlaybackRange(start, start.plusSeconds(3600));
        assertEquals(start, range.startTime());
        assertEquals(start.plusSeconds(3600), range.endTime());
        assertEquals(GbPlaybackControl.Type.PAUSE, GbPlaybackControl.pause().type());
        assertEquals(GbPlaybackControl.Type.RESUME, GbPlaybackControl.resume().type());
        assertEquals(Duration.ofSeconds(30), GbPlaybackControl.seek(Duration.ofSeconds(30)).position());
        assertEquals(2.0, GbPlaybackControl.speed(2.0).scale());
        assertThrows(IllegalArgumentException.class, () -> new GbPlaybackRange(start, start));
        assertThrows(IllegalArgumentException.class, () -> GbPlaybackControl.seek(Duration.ofMillis(1)));
        assertThrows(IllegalArgumentException.class, () -> GbPlaybackControl.speed(Double.NaN));
        assertEquals(CompletableFuture.class.getName() + "<" + GbPlaySession.class.getName() + ">",
                Gb28181Server.class.getMethod("playback", String.class, String.class,
                        GbRtpTarget.class, GbPlaybackRange.class).getGenericReturnType().getTypeName());
        assertEquals(Optional.class.getName() + "<" + GbPlaybackRange.class.getName() + ">",
                GbPlaySession.class.getMethod("playbackRange").getGenericReturnType().getTypeName());
        assertEquals(CompletableFuture.class.getName() + "<" + Void.class.getName() + ">",
                GbPlaySession.class.getMethod("control", GbPlaybackControl.class).getGenericReturnType().getTypeName());
    }

    @Test void exposesRecordQueryApiAndCapacity() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        RecordQuery query = RecordQuery.all(start, start.plusHours(1));
        assertEquals(RecordQuery.Type.ALL, query.type());
        assertEquals(start, query.startTime());
        assertEquals(start.plusHours(1), query.endTime());
        assertEquals(RecordQuery.Type.ALARM,
                new RecordQuery(start, start.plusHours(1), RecordQuery.Type.ALARM).type());
        assertTrue(Modifier.isPublic(RecordItem.class.getModifiers()));
        String returnType = Gb28181Server.class.getMethod("queryRecordInfo",
                String.class, String.class, RecordQuery.class).getGenericReturnType().getTypeName();
        assertEquals(CompletableFuture.class.getName() + "<" + List.class.getName()
                + "<" + RecordItem.class.getName() + ">>", returnType);
        assertEquals(37, GbServerOptions.builder().serverId("34020000002000000001")
                .realm("example.test").maxRecordItems(37).build().maxRecordItems());
    }

    @Test void exposesValidatedApiWithoutNativeMediaDependencies() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> GbServerOptions.builder().build());
        DeviceCredentials credentials = device -> Optional.empty();
        assertTrue(credentials.passwordFor("unconfigured").isEmpty());
        assertNotNull(Gb28181Server.class.getMethod("queryCatalog", String.class));
        assertNotNull(Gb28181Server.class.getMethod("play", String.class, String.class, GbRtpTarget.class));
        assertNotNull(GbPlaySession.class.getMethod("completion"));
        assertNotNull(Gb28181Server.class.getMethod("queryDeviceInfo", String.class));
        assertNotNull(Gb28181Server.class.getMethod("queryDeviceStatus", String.class));
        assertNotNull(Gb28181Server.class.getMethod("ptz", String.class, String.class, PtzCommand.class));
        assertNotNull(DeviceInfo.class.getMethod("channelCount"));
        assertNotNull(DeviceStatus.class.getMethod("online"));
        assertEquals("A50F0100000000B5", PtzCommand.stop().hex());
        assertThrows(IllegalArgumentException.class, () -> new GbRtpTarget("0.0.0.0", 30000, GbRtpTarget.Transport.UDP));
        assertNotNull(CatalogItem.class.getMethod("parentId"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.ss.zlm4j.service.IZlmMediaService"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("com.sun.jna.Native"));
    }
}
