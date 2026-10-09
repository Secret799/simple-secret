package com.ss.gb28181;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.ss.gb28181.Gb28181ServerTest.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class GbDeviceControlTest {
    private static final String CHANNEL = "34020000001320000002";
    private final String password = UUID.randomUUID().toString();

    @ParameterizedTest
    @ValueSource(strings = {"UDP", "TCP"})
    void sendsBothOperationsAndCompletesOnlyFromValidatedSipReceipt(String transport) throws Exception {
        int port = freePort();
        try (var server = open(options(port).build()); var peer = new Peer(port, transport);
             var spoof = new Peer(port, transport)) {
            peer.register(password);
            var reset = server.resetAlarm(DEVICE, CHANNEL, new GbAlarmResetCommand(Set.of(5, 2), null));
            var resetRequest = peer.receive();
            assertRequest(resetRequest, peer, "<AlarmCmd>ResetAlarm</AlarmCmd>", "<AlarmMethod>2/5</AlarmMethod>");
            spoof.respond(resetRequest, 200);
            peer.respond(resetRequest, 100);
            peer.message(response(sn(resetRequest), "ERROR"));
            assertEquals(200, peer.receive().status());
            assertFalse(reset.isDone(), "provisional, forged and XML responses do not complete receipt-only control");
            peer.respond(resetRequest, 200);
            reset.get(2, TimeUnit.SECONDS);

            var keyFrame = server.requestKeyFrame(DEVICE, CHANNEL);
            var frameRequest = peer.receive();
            assertRequest(frameRequest, peer, "<IFrameCmd>Send</IFrameCmd>");
            assertFalse(frameRequest.body().contains("AlarmCmd"));
            peer.respond(frameRequest, 200);
            keyFrame.get(2, TimeUnit.SECONDS);
            peer.message(response(sn(frameRequest), "ERROR"));
            assertEquals(200, peer.receive().status(), "late optional XML remains an independent acknowledgement");
        }
    }

    @Test
    void rejectsSipFailureInvalidArgumentsAndOfflineOwner() throws Exception {
        int port = freePort();
        try (var server = open(options(port).build()); var peer = new Peer(port, "UDP")) {
            assertThrows(IllegalArgumentException.class,
                    () -> server.resetAlarm("invalid", CHANNEL, GbAlarmResetCommand.all()));
            assertThrows(IllegalArgumentException.class,
                    () -> server.resetAlarm(DEVICE, "123456789", GbAlarmResetCommand.all()));
            assertThrows(NullPointerException.class, () -> server.resetAlarm(DEVICE, CHANNEL, null));
            assertThrows(IllegalArgumentException.class, () -> server.requestKeyFrame(DEVICE, "invalid"));
            assertThrows(IllegalStateException.class,
                    () -> server.resetAlarm(DEVICE, CHANNEL, GbAlarmResetCommand.all()));
            assertThrows(IllegalStateException.class, () -> server.requestKeyFrame(DEVICE, CHANNEL));

            peer.register(password);
            var reset = server.resetAlarm(DEVICE, CHANNEL, GbAlarmResetCommand.all());
            peer.respond(peer.receive(), 403);
            assertThrows(ExecutionException.class, () -> reset.get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void supportsTenDigitAlarmCenterResetTarget() throws Exception {
        int port = freePort();
        try (var server = open(options(port).build()); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var reset = server.resetAlarm(DEVICE, "3402000000", GbAlarmResetCommand.all());
            var request = peer.receive();
            assertTrue(request.start().contains("sip:3402000000@"));
            assertTrue(request.body().contains("<DeviceID>3402000000</DeviceID>"));
            assertTrue(request.body().contains("<AlarmMethod>0</AlarmMethod>"));
            peer.respond(request, 200);
            reset.get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void sharesCapacityAndReleasesOnCancelHostCompletionTimeoutOfflineRebindAndClose() throws Exception {
        int port = freePort();
        var configured = options(port).maxPendingQueries(1).queryTimeout(Duration.ofMillis(300)).build();
        try (var server = open(configured); var peer = new Peer(port, "UDP"); var replacement = new Peer(port, "UDP")) {
            peer.register(password);
            var reset = server.resetAlarm(DEVICE, CHANNEL, GbAlarmResetCommand.all());
            peer.receive();
            assertThrows(IllegalStateException.class, () -> server.requestKeyFrame(DEVICE, CHANNEL));
            reset.cancel(false);

            var externallyCompleted = server.requestKeyFrame(DEVICE, CHANNEL);
            peer.receive();
            externallyCompleted.complete(null);
            var timedOut = server.resetAlarm(DEVICE, CHANNEL, GbAlarmResetCommand.all());
            peer.receive();
            assertTimeout(timedOut);

            var rebound = server.requestKeyFrame(DEVICE, CHANNEL);
            peer.receive();
            replacement.register(password);
            assertFails(rebound);
            var unregistered = server.resetAlarm(DEVICE, CHANNEL, GbAlarmResetCommand.all());
            replacement.receive();
            replacement.register(password, 0);
            assertFails(unregistered);

            replacement.register(password);
            var closing = server.requestKeyFrame(DEVICE, CHANNEL);
            replacement.receive();
            server.close();
            assertFails(closing);
            assertThrows(IllegalStateException.class, () -> server.requestKeyFrame(DEVICE, CHANNEL));
        }
    }

    private Gb28181Server open(GbServerOptions configured) {
        return Gb28181Server.open(configured, id -> Optional.of(password));
    }

    private static void assertRequest(Wire request, Peer peer, String... fragments) {
        assertTrue(request.start().startsWith("MESSAGE sip:" + CHANNEL + "@127.0.0.1:" + peer.port()));
        assertTrue(request.headers().get("to").contains("sip:" + CHANNEL + "@"));
        assertTrue(request.headers().get("from").contains("sip:" + SERVER + "@"));
        assertTrue(request.body().contains("<Control>"));
        assertTrue(request.body().contains("<CmdType>DeviceControl</CmdType>"));
        assertTrue(request.body().contains("<DeviceID>" + CHANNEL + "</DeviceID>"));
        for (String fragment : fragments) assertTrue(request.body().contains(fragment));
    }

    private static String sn(Wire request) { return match(request.body(), "<SN>([0-9]+)</SN>"); }

    private static String response(String sn, String result) {
        return "<Response><CmdType>DeviceControl</CmdType><SN>" + sn + "</SN><DeviceID>" + CHANNEL
                + "</DeviceID><Result>" + result + "</Result></Response>";
    }

    private static void assertTimeout(CompletableFuture<?> future) {
        assertInstanceOf(TimeoutException.class,
                assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS)).getCause());
    }

    private static void assertFails(CompletableFuture<?> future) {
        assertThrows(ExecutionException.class, () -> future.get(2, TimeUnit.SECONDS));
    }
}
