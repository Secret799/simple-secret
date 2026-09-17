package com.ss.gb28181;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import static com.ss.gb28181.Gb28181ServerTest.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class GbRecordQueryTest {
    static final String CHANNEL = "34020000001320000002";
    static final RecordQuery RANGE = RecordQuery.all(LocalDateTime.of(2026, 9, 15, 12, 0),
            LocalDateTime.of(2026, 9, 15, 13, 0));
    final String password = UUID.randomUUID().toString();

    @ParameterizedTest @ValueSource(strings = {"UDP", "TCP"})
    void aggregatesOutOfOrderPagesAndExactDuplicatesOnRegisteredConnection(String transport) throws Exception {
        int port = freePort();
        try (var server = open(options(port).maxCatalogItems(1).maxRecordItems(3).build());
             var peer = new Peer(port, transport)) {
            peer.register(password);
            var result = server.queryRecordInfo(DEVICE, CHANNEL, RANGE);
            var request = peer.receive();
            assertTrue(request.start().startsWith("MESSAGE sip:" + CHANNEL + "@127.0.0.1:" + peer.port()));
            assertTrue(request.headers().get("to").contains("sip:" + CHANNEL + "@"));
            assertTrue(request.body().contains("<DeviceID>" + CHANNEL + "</DeviceID>"));
            assertTrue(request.body().contains("<CmdType>RecordInfo</CmdType>"));
            assertTrue(request.body().contains("<StartTime>2026-09-15T12:00:00</StartTime>"));
            assertTrue(request.body().contains("<EndTime>2026-09-15T13:00:00</EndTime>"));
            assertTrue(request.body().contains("<Type>all</Type>"));
            peer.respond(request, 200);
            assertFalse(result.isDone(), "SIP receipt must not complete the record query");
            String sn = sn(request);
            String late = item("late", "2026-09-15T12:40:00");
            String early = item("early", "2026-09-15T11:50:00");
            peer.message(page(sn, 3, late));
            assertEquals(200, peer.receive().status());
            peer.message(page(sn, 3, late));
            assertEquals(200, peer.receive().status());
            assertFalse(result.isDone(), "duplicate records do not count toward SumNum");
            peer.message(page(sn, 3, early, item("middle", "2026-09-15T12:20:00")));
            assertEquals(200, peer.receive().status());
            var records = result.get(2, TimeUnit.SECONDS);
            assertEquals(List.of("late", "early", "middle"), records.stream().map(RecordItem::filePath).toList());
            assertTrue(records.stream().allMatch(record -> CHANNEL.equals(record.deviceId())));
            assertEquals("2026-09-15T11:50:00", records.get(1).startTime(), "whole files are not clipped to query range");
            assertEquals(123L, records.get(0).fileSize());
            assertThrows(UnsupportedOperationException.class, () -> records.add(records.get(0)));
            peer.message(page(sn, 3, late));
            assertEquals(200, peer.receive().status(), "late channel responses are harmless");
            assertEquals(3, records.size());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"UDP", "TCP"})
    void acceptsEmptyResponseBeforeSipReceiptWithoutResultField(String transport) throws Exception {
        int port = freePort();
        try (var server = open(options(port).build()); var peer = new Peer(port, transport)) {
            peer.register(password);
            var result = server.queryRecordInfo(DEVICE, CHANNEL, RANGE);
            var request = peer.receive();
            peer.message(page(sn(request), 0));
            assertEquals(200, peer.receive().status());
            assertEquals(List.of(), result.get(2, TimeUnit.SECONDS));
        }
    }

    @Test void distinguishesRecordIdentityFieldsAndAllowsMissingOptionalMetadata() throws Exception {
        int port = freePort();
        try (var server = open(options(port).build()); var peer = new Peer(port, "TCP")) {
            peer.register(password);
            var result = server.queryRecordInfo(DEVICE, CHANNEL, RANGE);
            var request = peer.receive();
            String first = item("same", "2026-09-15T12:00:00");
            String minimal = "<Item><DeviceID>" + CHANNEL + "</DeviceID><Name>Camera</Name><Secrecy>0</Secrecy></Item>";
            peer.message(page(sn(request), 6, first,
                    first.replace("12:00:00", "12:10:00"), first.replace("13:10:00", "13:20:00"),
                    first.replace("<Type>time</Type>", "<Type>alarm</Type>"),
                    first.replace("<RecorderID>" + DEVICE, "<RecorderID>" + CHANNEL), minimal));
            assertEquals(200, peer.receive().status());
            var records = result.get(2, TimeUnit.SECONDS);
            assertEquals(6, records.size());
            assertNull(records.get(5).filePath());
            assertNull(records.get(5).startTime());
            assertNull(records.get(5).type());
            assertNull(records.get(5).fileSize());
        }
    }

    @Test void conflictingRecordChangedTotalAndTooManyUniqueRecordsFailAndReleaseCapacity() throws Exception {
        int port = freePort();
        try (var server = open(options(port).maxPendingQueries(1).build()); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            for (String failure : List.of("conflict", "total", "count")) {
                var result = server.queryRecordInfo(DEVICE, CHANNEL, RANGE);
                var request = peer.receive();
                peer.respond(request, 200);
                String sn = sn(request), first = item("one", "2026-09-15T12:00:00");
                peer.message(page(sn, 2, first));
                assertEquals(200, peer.receive().status());
                String next = switch (failure) {
                    case "conflict" -> page(sn, 2, first.replace("<Name>Camera</Name>", "<Name>Different</Name>"));
                    case "total" -> page(sn, 3, item("two", "2026-09-15T12:10:00"));
                    default -> page(sn, 2, item("two", "2026-09-15T12:10:00"), item("three", "2026-09-15T12:20:00"));
                };
                peer.message(next);
                assertEquals(400, peer.receive().status(), failure);
                assertInstanceOf(IllegalArgumentException.class,
                        assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS)).getCause());
            }
        }
    }

    @Test void rejectsWrongSourceOwnerTargetCommandAndSnWithoutCompletingQuery() throws Exception {
        int port = freePort();
        try (var server = open(options(port).build()); var peer = new Peer(port, "UDP"); var spoof = new Peer(port, "UDP")) {
            peer.register(password);
            var result = server.queryRecordInfo(DEVICE, CHANNEL, RANGE);
            var request = peer.receive();
            peer.respond(request, 200);
            String body = page(sn(request), 0);
            spoof.message(body);
            assertEquals(403, spoof.receive().status());
            peer.message(body.replace(CHANNEL, DEVICE));
            assertEquals(403, peer.receive().status());
            peer.message(GbDeviceCommandsTest.response("DeviceInfo", sn(request), GbDeviceCommandsTest.infoFields())
                    .replace("<DeviceID>" + DEVICE + "</DeviceID>", "<DeviceID>" + CHANNEL + "</DeviceID>"));
            assertEquals(400, peer.receive().status());
            peer.message(page("2147483647", 0));
            assertEquals(200, peer.receive().status());
            sendAs(peer, "34020000002000000003", body);
            assertEquals(403, peer.receive().status());
            assertFalse(result.isDone());
            peer.message(body);
            assertEquals(200, peer.receive().status());
            assertEquals(List.of(), result.get(2, TimeUnit.SECONDS));
        }
    }

    @Test void rejectsWrongItemChannelAndMalformedPageWithoutCompletingQuery() throws Exception {
        int port = freePort();
        try (var server = open(options(port).build()); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var result = server.queryRecordInfo(DEVICE, CHANNEL, RANGE);
            var request = peer.receive();
            peer.respond(request, 200);
            peer.message(page(sn(request), 1, item("one", "2026-09-15T12:00:00").replace(CHANNEL, DEVICE)));
            assertEquals(400, peer.receive().status());
            assertFalse(result.isDone());
            peer.message(page(sn(request), 1, item("one", "2026-09-15T12:00:00")).replace("Num=\"1\"", "Num=\"2\""));
            assertEquals(400, peer.receive().status());
            assertFalse(result.isDone());
            peer.message(page(sn(request), 0));
            assertEquals(200, peer.receive().status());
            assertEquals(List.of(), result.get(2, TimeUnit.SECONDS));
        }
    }

    @Test void enforcesIndependentRecordTotalAndCumulativeByteLimit() throws Exception {
        int port = freePort();
        try (var server = open(options(port).maxRecordItems(2).maxCatalogItems(10).maxMessageBytes(4096).build());
             var peer = new Peer(port, "TCP")) {
            peer.register(password);
            var tooMany = server.queryRecordInfo(DEVICE, CHANNEL, RANGE);
            var request = peer.receive();
            peer.respond(request, 200);
            peer.message(page(sn(request), 3, item("one", "2026-09-15T12:00:00")));
            assertEquals(400, peer.receive().status());
            assertFalse(tooMany.isDone(), "oversized XML is rejected before aggregation");
            peer.message(page(sn(request), 0));
            assertEquals(200, peer.receive().status());
            assertEquals(List.of(), tooMany.get(2, TimeUnit.SECONDS));
            var bytes = server.queryRecordInfo(DEVICE, CHANNEL, RANGE);
            request = peer.receive();
            peer.respond(request, 200);
            String repeated = page(sn(request), 2, item("one", "2026-09-15T12:00:00"));
            int pages = (4096 * 16) / repeated.getBytes(StandardCharsets.UTF_8).length;
            for (int i = 0; i < pages; i++) {
                peer.message(repeated);
                assertEquals(200, peer.receive().status());
            }
            peer.message(repeated);
            assertEquals(400, peer.receive().status());
            assertThrows(ExecutionException.class, () -> bytes.get(2, TimeUnit.SECONDS));
        }
    }

    @Test void sharesMessageCapacityAndReleasesCancellationHostCompletionTimeoutAndClose() throws Exception {
        int port = freePort();
        try (var server = open(options(port).maxPendingQueries(1).queryTimeout(Duration.ofMillis(400)).build());
             var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var catalog = server.queryCatalog(DEVICE);
            peer.respond(peer.receive(), 200);
            assertThrows(IllegalStateException.class, () -> server.queryRecordInfo(DEVICE, CHANNEL, RANGE));
            catalog.cancel(false);
            var canceled = server.queryRecordInfo(DEVICE, CHANNEL, RANGE);
            peer.respond(peer.receive(), 200);
            assertThrows(IllegalStateException.class, () -> server.queryDeviceInfo(DEVICE));
            assertThrows(IllegalStateException.class, () -> server.ptz(DEVICE, CHANNEL, PtzCommand.stop()));
            canceled.cancel(false);
            var completed = server.queryRecordInfo(DEVICE, CHANNEL, RANGE);
            peer.respond(peer.receive(), 200);
            completed.complete(List.of());
            var timedOut = server.queryRecordInfo(DEVICE, CHANNEL, RANGE);
            peer.respond(peer.receive(), 200);
            assertInstanceOf(TimeoutException.class,
                    assertThrows(ExecutionException.class, () -> timedOut.get(2, TimeUnit.SECONDS)).getCause());
            var closing = server.queryRecordInfo(DEVICE, CHANNEL, RANGE);
            peer.respond(peer.receive(), 200);
            server.close();
            assertThrows(ExecutionException.class, () -> closing.get(1, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> server.queryRecordInfo(DEVICE, CHANNEL, RANGE));
        }
    }

    @Test void failsOnSipRejectionUnregisterAndBindingReplacement() throws Exception {
        int port = freePort();
        try (var server = open(options(port).build()); var peer = new Peer(port, "UDP"); var replacement = new Peer(port, "UDP")) {
            peer.register(password);
            var rejected = server.queryRecordInfo(DEVICE, CHANNEL, RANGE);
            peer.respond(peer.receive(), 403);
            assertThrows(ExecutionException.class, () -> rejected.get(2, TimeUnit.SECONDS));
            var moved = server.queryRecordInfo(DEVICE, CHANNEL, RANGE);
            peer.respond(peer.receive(), 200);
            replacement.register(password);
            assertThrows(ExecutionException.class, () -> moved.get(2, TimeUnit.SECONDS));
            var unregistering = server.queryRecordInfo(DEVICE, CHANNEL, RANGE);
            replacement.respond(replacement.receive(), 200);
            replacement.register(password, 0);
            assertThrows(ExecutionException.class, () -> unregistering.get(2, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> server.queryRecordInfo(DEVICE, CHANNEL, RANGE));
        }
    }

    @Test void heartbeatExpiryFailsPendingRecordQuery() throws Exception {
        int port = freePort();
        try (var server = open(options(port).heartbeatInterval(Duration.ofMillis(150)).heartbeatMisses(2).build());
             var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var result = server.queryRecordInfo(DEVICE, CHANNEL, RANGE);
            peer.respond(peer.receive(), 200);
            assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
            assertTrue(server.devices().isEmpty());
        }
    }

    @Test void rejectsInvalidArgumentsAndOfflineDevices() throws Exception {
        int port = freePort();
        try (var server = open(options(port).build())) {
            assertThrows(IllegalArgumentException.class, () -> server.queryRecordInfo("invalid", CHANNEL, RANGE));
            assertThrows(IllegalArgumentException.class, () -> server.queryRecordInfo(DEVICE, "invalid", RANGE));
            assertThrows(NullPointerException.class, () -> server.queryRecordInfo(DEVICE, CHANNEL, null));
            assertThrows(IllegalStateException.class, () -> server.queryRecordInfo(DEVICE, CHANNEL, RANGE));
        }
    }

    Gb28181Server open(GbServerOptions options) { return Gb28181Server.open(options, id -> Optional.of(password)); }
    static String sn(Wire request) { return match(request.body(), "<SN>([0-9]+)</SN>"); }
    static String page(String sn, int total, String... items) {
        return "<Response><CmdType>RecordInfo</CmdType><SN>" + sn + "</SN><DeviceID>" + CHANNEL
                + "</DeviceID><Name>Camera</Name><SumNum>" + total + "</SumNum><RecordList Num=\"" + items.length + "\">"
                + String.join("", items) + "</RecordList></Response>";
    }
    static String item(String path, String start) {
        return "<Item><DeviceID>" + CHANNEL + "</DeviceID><Name>Camera</Name><FilePath>" + path
                + "</FilePath><Address>Room</Address><StartTime>" + start + "</StartTime><EndTime>2026-09-15T13:10:00</EndTime>"
                + "<Secrecy>0</Secrecy><Type>time</Type><RecorderID>" + DEVICE + "</RecorderID><FileSize>123</FileSize></Item>";
    }
    static void sendAs(Peer peer, String owner, String body) throws Exception {
        peer.send("MESSAGE sip:" + SERVER + "@" + REALM + " SIP/2.0\r\nVia: SIP/2.0/UDP 127.0.0.1:"
                + peer.port() + ";rport;branch=z9hG4bK" + UUID.randomUUID() + "\r\nFrom: <sip:" + owner + "@" + REALM
                + ">;tag=another-device\r\nTo: <sip:" + SERVER + "@" + REALM + ">\r\nCall-ID: " + UUID.randomUUID()
                + "\r\nCSeq: 1 MESSAGE\r\nMax-Forwards: 70\r\nContent-Type: Application/MANSCDP+xml\r\nContent-Length: "
                + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + body);
    }
}
