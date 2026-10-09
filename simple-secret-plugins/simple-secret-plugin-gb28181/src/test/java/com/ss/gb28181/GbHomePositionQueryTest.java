package com.ss.gb28181;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import static com.ss.gb28181.Gb28181ServerTest.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class GbHomePositionQueryTest {
    private static final String CHANNEL = "34020000001320000002";
    Gb28181Server open(GbServerOptions options) { return Gb28181Server.open(options, id -> java.util.Optional.of(password)); }
    final String password = UUID.randomUUID().toString();
    static String sn(Gb28181ServerTest.Wire request) { return Gb28181ServerTest.match(request.body(), "<SN>([0-9]+)</SN>"); }
    @ParameterizedTest @ValueSource(strings = {"UDP", "TCP"})
    void queriesHomePositionConfigurationOnAuthorizedChannel(String transport) throws Exception {
        int port = freePort();
        try (var server = open(options(port).build()); var peer = new Peer(port, transport)) {
            peer.register(password);
            var result = server.queryHomePosition(DEVICE, CHANNEL);
            var request = peer.receive();
            assertTrue(request.start().startsWith("MESSAGE sip:" + CHANNEL + "@127.0.0.1:" + peer.port()));
            assertTrue(request.body().contains("<DeviceID>" + CHANNEL + "</DeviceID>"));
            assertTrue(request.body().contains("<CmdType>HomePositionQuery</CmdType>"));
            peer.respond(request, 200);
            assertFalse(result.isDone(), "SIP receipt alone is not a configuration response");
            String response = response(sn(request), CHANNEL, "<HomePosition><Enabled>1</Enabled>"
                    + "<ResetTime>30</ResetTime><PresetIndex>2</PresetIndex></HomePosition>");
            peer.message(response);
            assertEquals(200, peer.receive().status());
            var home = result.get(2, TimeUnit.SECONDS);
            assertEquals(CHANNEL, home.deviceId());
            assertTrue(home.enabled());
            assertEquals(30, home.resetTime());
            assertEquals(2, home.presetIndex());
            peer.message(response);
            assertEquals(200, peer.receive().status(), "late channel responses only acknowledge receipt");
        }
    }

    @Test void unreportedConfigurationCanCompleteBeforeSipReceipt() throws Exception {
        int port = freePort();
        try (var server = open(options(port).build()); var peer = new Peer(port, "TCP")) {
            peer.register(password);
            var result = server.queryHomePosition(DEVICE);
            var request = peer.receive();
            peer.message(response(sn(request), DEVICE, ""));
            assertEquals(200, peer.receive().status());
            assertNull(result.get(2, TimeUnit.SECONDS).enabled());
        }
    }

    @Test void rejectsWrongTargetCommandAndMalformedFieldsThenAcceptsValidResponse() throws Exception {
        int port = freePort();
        try (var server = open(options(port).build()); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var result = server.queryHomePosition(DEVICE, CHANNEL);
            String sn = sn(peer.receive());
            peer.message(response(sn, DEVICE, ""));
            assertEquals(403, peer.receive().status());
            peer.message(response(sn, CHANNEL, "<HomePosition><Enabled>9</Enabled></HomePosition>"));
            assertEquals(400, peer.receive().status());
            peer.message(response(sn, CHANNEL, "<Result>OK</Result>").replace("HomePositionQuery", "DeviceControl"));
            assertEquals(400, peer.receive().status());
            assertFalse(result.isDone());
            peer.message(response(sn, CHANNEL, "<HomePosition><Enabled>0</Enabled></HomePosition>"));
            assertEquals(200, peer.receive().status());
            assertFalse(result.get(2, TimeUnit.SECONDS).enabled());
        }
    }

    @Test void errorsCancellationAndCloseReleasePendingCapacity() throws Exception {
        int port = freePort();
        try (var server = open(options(port).maxPendingQueries(1).build()); var peer = new Peer(port, "TCP")) {
            peer.register(password);
            var error = server.queryHomePosition(DEVICE);
            String sn = sn(peer.receive());
            peer.message(response(sn, DEVICE, "<Result>ERROR</Result>"));
            assertEquals(200, peer.receive().status());
            assertThrows(ExecutionException.class, () -> error.get(2, TimeUnit.SECONDS));
            var rejected = server.queryHomePosition(DEVICE);
            peer.respond(peer.receive(), 403);
            assertThrows(ExecutionException.class, () -> rejected.get(2, TimeUnit.SECONDS));
            var canceled = server.queryHomePosition(DEVICE);
            peer.respond(peer.receive(), 200);
            assertThrows(IllegalStateException.class, () -> server.queryHomePosition(DEVICE));
            assertTrue(canceled.cancel(false));
            var closed = server.queryHomePosition(DEVICE);
            peer.respond(peer.receive(), 200);
            server.close();
            assertThrows(ExecutionException.class, () -> closed.get(2, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> server.queryHomePosition(DEVICE));
        }
    }

    @Test void receiptOnlyQueryTimesOutAndReleasesCapacity() throws Exception {
        int port = freePort();
        try (var server = open(options(port).maxPendingQueries(1).queryTimeout(Duration.ofMillis(250)).build());
             var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var timed = server.queryHomePosition(DEVICE);
            peer.respond(peer.receive(), 200);
            ExecutionException error = assertThrows(ExecutionException.class, () -> timed.get(2, TimeUnit.SECONDS));
            assertInstanceOf(java.util.concurrent.TimeoutException.class, error.getCause());
            var next = server.queryHomePosition(DEVICE);
            String sn = sn(peer.receive());
            peer.message(response(sn, DEVICE, ""));
            assertEquals(200, peer.receive().status());
            assertNotNull(next.get(2, TimeUnit.SECONDS));
        }
    }

    @Test void validatesOwnerAndTargetWithoutSending() throws Exception {
        try (var server = open(options(freePort()).build())) {
            assertThrows(IllegalArgumentException.class, () -> server.queryHomePosition("bad", CHANNEL));
            assertThrows(IllegalArgumentException.class, () -> server.queryHomePosition(DEVICE, "bad"));
            assertThrows(IllegalArgumentException.class, () -> server.queryHomePosition(null));
            assertThrows(IllegalStateException.class, () -> server.queryHomePosition(DEVICE, CHANNEL));
        }
    }

    private static String response(String sn, String target, String body) {
        return "<Response><CmdType>HomePositionQuery</CmdType><SN>" + sn + "</SN><DeviceID>"
                + target + "</DeviceID>" + body + "</Response>";
    }
}
