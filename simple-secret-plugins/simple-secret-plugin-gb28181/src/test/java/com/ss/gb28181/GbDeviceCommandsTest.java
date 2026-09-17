package com.ss.gb28181;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import static com.ss.gb28181.Gb28181ServerTest.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class GbDeviceCommandsTest {
    static final String CHANNEL = "34020000001320000002";
    final String password = UUID.randomUUID().toString();

    @ParameterizedTest @ValueSource(strings = {"UDP", "TCP"})
    void queriesDeviceInfoAndStatusAndSendsPtzOverRegisteredConnection(String transport) throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, transport)) {
            peer.register(password);
            var info = server.queryDeviceInfo(DEVICE);
            var request = peer.receive();
            assertTrue(request.body().contains("<CmdType>DeviceInfo</CmdType>"));
            peer.respond(request, 200);
            assertFalse(info.isDone(), "SIP receipt must not complete a device query");
            peer.message(response("DeviceInfo", sn(request), "<Result>OK</Result><DeviceName>一号设备</DeviceName>"
                    + "<Manufacturer>Example</Manufacturer><Model>Test</Model><Firmware>1.2</Firmware><Channel>8</Channel>"));
            assertEquals(200, peer.receive().status());
            var value = info.get(2, TimeUnit.SECONDS);
            assertEquals(DEVICE, value.deviceId());
            assertEquals("一号设备", value.deviceName());
            assertEquals("Example", value.manufacturer());
            assertEquals(8, value.channelCount());

            var status = server.queryDeviceStatus(DEVICE);
            var statusRequest = peer.receive();
            assertTrue(statusRequest.body().contains("<CmdType>DeviceStatus</CmdType>"));
            // The asynchronous XML response can arrive before the original MESSAGE's SIP 200.
            peer.message(response("DeviceStatus", sn(statusRequest), statusFields()));
            assertEquals(200, peer.receive().status());
            var deviceStatus = status.get(2, TimeUnit.SECONDS);
            assertEquals("ONLINE", deviceStatus.online());
            assertEquals("OK", deviceStatus.status());
            assertEquals("ON", deviceStatus.encode());
            assertEquals("OFF", deviceStatus.record());
            assertEquals("2026-09-15T12:30:00", deviceStatus.deviceTime());

            var control = server.ptz(DEVICE, CHANNEL, PtzCommand.move(PtzCommand.Pan.RIGHT, PtzCommand.Tilt.NONE, 32, 0));
            var move = peer.receive();
            assertTrue(move.start().startsWith("MESSAGE sip:" + CHANNEL + "@127.0.0.1:" + peer.port()));
            assertTrue(move.body().contains("<CmdType>DeviceControl</CmdType>"));
            assertTrue(move.body().contains("<DeviceID>" + CHANNEL + "</DeviceID>"));
            assertTrue(move.body().contains("<PTZCmd>A50F0101200000D6</PTZCmd>"));
            assertFalse(control.isDone());
            String controlResponse = response("DeviceControl", sn(move), "<Result>OK</Result>")
                    .replace("<DeviceID>" + DEVICE + "</DeviceID>", "<DeviceID>" + CHANNEL + "</DeviceID>");
            peer.message(controlResponse);
            assertEquals(200, peer.receive().status());
            assertFalse(control.isDone(), "optional XML response does not replace SIP receipt");
            peer.respond(move, 200);
            control.get(2, TimeUnit.SECONDS);
            peer.message(controlResponse);
            assertEquals(200, peer.receive().status());
            var stop = server.ptz(DEVICE, CHANNEL, PtzCommand.stop());
            var stopRequest = peer.receive();
            assertTrue(stopRequest.body().contains("<PTZCmd>A50F0100000000B5</PTZCmd>"));
            peer.respond(stopRequest, 200);
            stop.get(2, TimeUnit.SECONDS);
        }
    }

    @Test void mismatchedCommandAndSpoofedSourceCannotCompleteQuery() throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, "UDP"); var spoof = new Peer(port, "UDP")) {
            peer.register(password);
            var info = server.queryDeviceInfo(DEVICE);
            var request = peer.receive();
            peer.message(response("DeviceInfo", "2147483647", infoFields()));
            assertEquals(200, peer.receive().status());
            assertFalse(info.isDone(), "unrelated SN must not complete this query");
            peer.message(response("DeviceInfo", sn(request), infoFields())
                    .replace("<DeviceID>" + DEVICE + "</DeviceID>", "<DeviceID>" + CHANNEL + "</DeviceID>"));
            assertEquals(403, peer.receive().status());
            assertFalse(info.isDone());
            peer.message(response("DeviceStatus", sn(request), statusFields()));
            assertEquals(400, peer.receive().status());
            assertFalse(info.isDone());
            spoof.message(response("DeviceInfo", sn(request), infoFields()));
            assertEquals(403, spoof.receive().status());
            assertFalse(info.isDone());
            peer.message(response("DeviceInfo", sn(request), infoFields()));
            assertEquals(200, peer.receive().status());
            assertEquals("Example", info.get(2, TimeUnit.SECONDS).manufacturer());
            peer.message(response("DeviceInfo", sn(request), infoFields()));
            assertEquals(200, peer.receive().status(), "late duplicate must be harmless");
        }
    }

    @Test void handlesErrorResultsSipRejectionAndMalformedResponseWithoutLosingPendingQuery() throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var info = server.queryDeviceInfo(DEVICE);
            var request = peer.receive();
            peer.message(response("DeviceInfo", sn(request), "<Result>ERROR</Result>"));
            assertEquals(200, peer.receive().status());
            assertThrows(ExecutionException.class, () -> info.get(2, TimeUnit.SECONDS));
            var status = server.queryDeviceStatus(DEVICE);
            var statusRequest = peer.receive();
            peer.message(response("DeviceStatus", sn(statusRequest), statusFields().replace("ONLINE", "invalid")));
            assertEquals(400, peer.receive().status());
            assertFalse(status.isDone());
            peer.message(response("DeviceStatus", sn(statusRequest), statusFields()));
            assertEquals(200, peer.receive().status());
            status.get(2, TimeUnit.SECONDS);
            var control = server.ptz(DEVICE, CHANNEL, PtzCommand.stop());
            peer.respond(peer.receive(), 403);
            assertThrows(ExecutionException.class, () -> control.get(2, TimeUnit.SECONDS));
        }
    }

    @Test void allMessageOperationsShareCapacityAndReleaseOnCancelTimeoutAndClose() throws Exception {
        int port = freePort();
        var options = options(port).maxPendingQueries(1).queryTimeout(Duration.ofMillis(250)).build();
        try (var server = Gb28181Server.open(options, id -> Optional.of(password)); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var catalog = server.queryCatalog(DEVICE);
            peer.receive();
            assertThrows(IllegalStateException.class, () -> server.queryDeviceInfo(DEVICE));
            assertThrows(IllegalStateException.class, () -> server.ptz(DEVICE, CHANNEL, PtzCommand.stop()));
            catalog.cancel(false);
            var info = server.queryDeviceInfo(DEVICE);
            peer.receive();
            assertInstanceOf(TimeoutException.class, assertThrows(ExecutionException.class, () -> info.get(2, TimeUnit.SECONDS)).getCause());
            var status = server.queryDeviceStatus(DEVICE);
            peer.receive();
            status.completeExceptionally(new TimeoutException("Host deadline"));
            var command = server.ptz(DEVICE, CHANNEL, PtzCommand.stop());
            peer.receive();
            server.close();
            assertThrows(ExecutionException.class, () -> command.get(1, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> server.queryDeviceStatus(DEVICE));
        }
    }

    @Test void unregisterFailsPendingQueriesAndRejectsInvalidTargets() throws Exception {
        int port = freePort();
        try (var server = open(port); var peer = new Peer(port, "UDP")) {
            assertThrows(IllegalStateException.class, () -> server.queryDeviceInfo(DEVICE));
            assertThrows(IllegalArgumentException.class, () -> server.queryDeviceInfo("bad\r\n"));
            assertThrows(IllegalArgumentException.class, () -> server.ptz(DEVICE, "invalid", PtzCommand.stop()));
            peer.register(password);
            var info = server.queryDeviceInfo(DEVICE);
            peer.receive();
            var status = server.queryDeviceStatus(DEVICE);
            peer.receive();
            peer.register(password, 0);
            assertThrows(ExecutionException.class, () -> info.get(1, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class, () -> status.get(1, TimeUnit.SECONDS));
        }
    }

    Gb28181Server open(int port) { return Gb28181Server.open(options(port).build(), id -> Optional.of(password)); }
    static String sn(Wire request) { return match(request.body(), "<SN>([0-9]+)</SN>"); }
    static String response(String command, String sn, String fields) {
        return "<Response><CmdType>" + command + "</CmdType><SN>" + sn + "</SN><DeviceID>" + DEVICE + "</DeviceID>" + fields + "</Response>";
    }
    static String infoFields() {
        return "<Result>OK</Result><Manufacturer>Example</Manufacturer><Model>Test</Model><Firmware>1.2</Firmware><Channel>8</Channel>";
    }
    static String statusFields() {
        return "<Result>OK</Result><Online>ONLINE</Online><Status>OK</Status><Encode>ON</Encode><Record>OFF</Record><DeviceTime>2026-09-15T12:30:00</DeviceTime>";
    }
}
