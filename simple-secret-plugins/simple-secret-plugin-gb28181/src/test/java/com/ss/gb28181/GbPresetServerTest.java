package com.ss.gb28181;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import static com.ss.gb28181.Gb28181ServerTest.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class GbPresetServerTest {
    static final String CHANNEL = "34020000001320000002";
    final String password = UUID.randomUUID().toString();

    @ParameterizedTest @ValueSource(strings = {"UDP", "TCP"})
    void aggregatesPagesAndDuplicatesThroughRegisteredOwner(String transport) throws Exception {
        int port = freePort();
        try (var server = open(options(port).maxCatalogItems(1).maxRecordItems(1).build());
             var peer = new Peer(port, transport)) {
            peer.register(password);
            var result = server.queryPresets(DEVICE, CHANNEL);
            var request = peer.receive();
            assertTrue(request.start().startsWith("MESSAGE sip:" + CHANNEL + "@127.0.0.1:" + peer.port()));
            assertTrue(request.headers().get("to").contains("sip:" + CHANNEL + "@"));
            assertTrue(request.headers().get("from").contains("sip:" + SERVER + "@"));
            assertTrue(request.body().contains("<Query>"));
            assertTrue(request.body().contains("<CmdType>PresetQuery</CmdType>"));
            assertTrue(request.body().contains("<DeviceID>" + CHANNEL + "</DeviceID>"));
            peer.respond(request, 200);
            assertFalse(result.isDone(), "SIP receipt does not complete the preset list");
            String sn = sn(request), gate = item("vendor-B", "大门");
            peer.message(page(sn, 3, gate));
            assertEquals(200, peer.receive().status());
            peer.message(page(sn, 3, gate));
            assertEquals(200, peer.receive().status());
            assertFalse(result.isDone(), "duplicate IDs do not count toward SumNum");
            peer.message(page(sn, 3, item("01", ""), item("1", "Gate &amp; Yard")));
            assertEquals(200, peer.receive().status());
            var presets = result.get(2, TimeUnit.SECONDS);
            assertEquals(List.of(new PresetItem("vendor-B", "大门"), new PresetItem("01", ""),
                    new PresetItem("1", "Gate & Yard")), presets);
            assertThrows(UnsupportedOperationException.class, () -> presets.add(presets.get(0)));
            peer.message(page(sn, 3, gate));
            assertEquals(200, peer.receive().status(), "late channel responses only acknowledge receipt");
        }
    }

    @ParameterizedTest @ValueSource(strings = {"UDP", "TCP"})
    void emptyXmlResponseCompletesBeforeSipReceipt(String transport) throws Exception {
        int port = freePort();
        try (var server = open(options(port).build()); var peer = new Peer(port, transport)) {
            peer.register(password);
            var result = server.queryPresets(DEVICE, CHANNEL);
            var request = peer.receive();
            peer.message(page(sn(request), 0));
            assertEquals(200, peer.receive().status());
            assertEquals(List.of(), result.get(2, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"UDP", "TCP"})
    void sendsAllPresetActionsAndOnlySipReceiptCompletesControl(String transport) throws Exception {
        int port = freePort();
        try (var server = open(options(port).build()); var peer = new Peer(port, transport);
             var spoof = new Peer(port, transport)) {
            peer.register(password);
            var commands = List.of(PresetCommand.set(1), PresetCommand.goTo(1), PresetCommand.remove(255));
            var hex = List.of("A50F018100010037", "A50F018200010038", "A50F018300FF0037");
            for (int i = 0; i < commands.size(); i++) {
                var result = server.preset(DEVICE, CHANNEL, commands.get(i));
                var request = peer.receive();
                assertTrue(request.start().startsWith("MESSAGE sip:" + CHANNEL + "@127.0.0.1:" + peer.port()));
                assertTrue(request.headers().get("from").contains("sip:" + SERVER + "@"));
                assertTrue(request.body().contains("<CmdType>DeviceControl</CmdType>"));
                assertTrue(request.body().contains("<DeviceID>" + CHANNEL + "</DeviceID>"));
                assertTrue(request.body().contains("<PTZCmd>" + hex.get(i) + "</PTZCmd>"));
                spoof.respond(request, 200);
                String optionalResponse = GbDeviceCommandsTest.response("DeviceControl", sn(request), "<Result>OK</Result>")
                        .replace("<DeviceID>" + DEVICE, "<DeviceID>" + CHANNEL);
                peer.message(optionalResponse);
                assertEquals(200, peer.receive().status());
                assertFalse(result.isDone(), "XML and an unregistered source cannot replace a valid SIP receipt");
                peer.respond(request, 200);
                result.get(2, TimeUnit.SECONDS);
                peer.message(optionalResponse);
                assertEquals(200, peer.receive().status());
            }
        }
    }

    @Test void conflictingDuplicatesChangedTotalAndUniqueOverflowFailAndReleaseCapacity() throws Exception {
        int port = freePort();
        try (var server = open(options(port).maxPendingQueries(1).build()); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            for (String failure : List.of("conflict", "total", "count")) {
                var result = server.queryPresets(DEVICE, CHANNEL);
                var request = peer.receive();
                peer.respond(request, 200);
                peer.message(page(sn(request), 2, item("1", "Gate")));
                assertEquals(200, peer.receive().status());
                String next = switch (failure) {
                    case "conflict" -> page(sn(request), 2, item("1", "Changed"));
                    case "total" -> page(sn(request), 3, item("2", "Door"));
                    default -> page(sn(request), 2, item("2", "Door"), item("3", "Yard"));
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
            var result = server.queryPresets(DEVICE, CHANNEL);
            var request = peer.receive();
            peer.respond(request, 200);
            String body = page(sn(request), 0);
            spoof.message(body);
            assertEquals(403, spoof.receive().status());
            peer.message(body.replace(CHANNEL, DEVICE));
            assertEquals(403, peer.receive().status());
            GbRecordQueryTest.sendAs(peer, "34020000002000000003", body);
            assertEquals(403, peer.receive().status());
            peer.message(GbDeviceCommandsTest.response("DeviceInfo", sn(request), GbDeviceCommandsTest.infoFields())
                    .replace("<DeviceID>" + DEVICE, "<DeviceID>" + CHANNEL));
            assertEquals(400, peer.receive().status());
            peer.message(page("2147483647", 0));
            assertEquals(200, peer.receive().status());
            assertFalse(result.isDone());
            var unrelated = server.queryDeviceInfo(DEVICE);
            var infoRequest = peer.receive();
            peer.respond(infoRequest, 200);
            peer.message(page(sn(infoRequest), 0));
            assertEquals(403, peer.receive().status(), "an occupied SN keeps its target validation");
            assertFalse(unrelated.isDone());
            peer.message(page(sn(infoRequest), 0).replace(CHANNEL, DEVICE));
            assertEquals(400, peer.receive().status(), "an occupied SN keeps its command validation");
            assertFalse(unrelated.isDone());
            unrelated.cancel(false);
            peer.message(body);
            assertEquals(200, peer.receive().status());
            assertEquals(List.of(), result.get(2, TimeUnit.SECONDS));
        }
    }

    @Test void malformedAndOversizedPagesLeaveQueryWaitingForValidResponse() throws Exception {
        int port = freePort();
        try (var server = open(options(port).build()); var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var result = server.queryPresets(DEVICE, CHANNEL);
            var request = peer.receive();
            peer.respond(request, 200);
            for (String body : List.of(page(sn(request), 256),
                    page(sn(request), 1, item("1", "Gate")).replace("Num=\"1\"", "Num=\"2\""),
                    page(sn(request), 1, "<Item><PresetID>1</PresetID></Item>"))) {
                peer.message(body);
                assertEquals(400, peer.receive().status());
                assertFalse(result.isDone());
            }
            peer.message(page(sn(request), 0));
            assertEquals(200, peer.receive().status());
            assertEquals(List.of(), result.get(2, TimeUnit.SECONDS));
        }
    }

    @Test void duplicatePagesConsumeCumulativeByteBudget() throws Exception {
        int port = freePort();
        try (var server = open(options(port).maxMessageBytes(4096).build()); var peer = new Peer(port, "TCP")) {
            peer.register(password);
            var result = server.queryPresets(DEVICE, CHANNEL);
            var request = peer.receive();
            peer.respond(request, 200);
            String repeated = page(sn(request), 3, item("1", "a".repeat(256)), item("2", "b".repeat(256)));
            int pages = 4096 * 16 / repeated.getBytes(StandardCharsets.UTF_8).length;
            for (int i = 0; i < pages; i++) {
                peer.message(repeated);
                assertEquals(200, peer.receive().status());
            }
            assertFalse(result.isDone());
            peer.message(repeated);
            assertEquals(400, peer.receive().status());
            assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS));
        }
    }

    @Test void queryAndControlShareCapacityAndReleaseCancelHostCompletionTimeoutAndClose() throws Exception {
        int port = freePort();
        try (var server = open(options(port).maxPendingQueries(1).queryTimeout(Duration.ofMillis(400)).build());
             var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var catalog = server.queryCatalog(DEVICE);
            peer.respond(peer.receive(), 200);
            assertThrows(IllegalStateException.class, () -> server.queryPresets(DEVICE, CHANNEL));
            assertThrows(IllegalStateException.class, () -> server.preset(DEVICE, CHANNEL, PresetCommand.set(1)));
            catalog.cancel(false);
            var canceled = server.queryPresets(DEVICE, CHANNEL);
            var canceledRequest = peer.receive();
            peer.respond(canceledRequest, 200);
            assertThrows(IllegalStateException.class, () -> server.queryDeviceInfo(DEVICE));
            assertThrows(IllegalStateException.class, () -> server.preset(DEVICE, CHANNEL, PresetCommand.goTo(1)));
            canceled.cancel(false);
            peer.message(page(sn(canceledRequest), 0));
            assertEquals(200, peer.receive().status());
            var hostCompleted = server.queryPresets(DEVICE, CHANNEL);
            peer.respond(peer.receive(), 200);
            hostCompleted.complete(List.of());
            var queryTimeout = server.queryPresets(DEVICE, CHANNEL);
            var request = peer.receive();
            peer.respond(request, 200);
            peer.message(page(sn(request), 2, item("1", "Gate")));
            assertEquals(200, peer.receive().status());
            assertTimeout(queryTimeout);
            var controlCanceled = server.preset(DEVICE, CHANNEL, PresetCommand.goTo(1));
            peer.receive();
            controlCanceled.cancel(false);
            var controlCompleted = server.preset(DEVICE, CHANNEL, PresetCommand.set(1));
            var set = peer.receive();
            assertTrue(set.body().contains("<PTZCmd>A50F018100010037</PTZCmd>"), "cancellation sends no compensating control");
            controlCompleted.complete(null);
            var controlTimeout = server.preset(DEVICE, CHANNEL, PresetCommand.remove(255));
            peer.receive();
            assertTimeout(controlTimeout);
            var closing = server.queryPresets(DEVICE, CHANNEL);
            peer.respond(peer.receive(), 200);
            server.close();
            assertThrows(ExecutionException.class, () -> closing.get(1, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> server.queryPresets(DEVICE, CHANNEL));
            assertThrows(IllegalStateException.class, () -> server.preset(DEVICE, CHANNEL, PresetCommand.set(1)));
        }
    }

    @Test void sipRejectionUnregisterRebindAndCloseFailBothOperations() throws Exception {
        int port = freePort();
        try (var server = open(options(port).build()); var peer = new Peer(port, "UDP"); var replacement = new Peer(port, "UDP")) {
            peer.register(password);
            var rejectedQuery = server.queryPresets(DEVICE, CHANNEL);
            peer.respond(peer.receive(), 403);
            assertThrows(ExecutionException.class, () -> rejectedQuery.get(2, TimeUnit.SECONDS));
            var rejectedControl = server.preset(DEVICE, CHANNEL, PresetCommand.set(1));
            peer.respond(peer.receive(), 403);
            assertThrows(ExecutionException.class, () -> rejectedControl.get(2, TimeUnit.SECONDS));
            for (String action : List.of("rebind", "unregister", "close")) {
                Peer current = action.equals("rebind") ? peer : replacement;
                if (action.equals("close")) replacement.register(password);
                var query = server.queryPresets(DEVICE, CHANNEL);
                current.respond(current.receive(), 200);
                var control = server.preset(DEVICE, CHANNEL, PresetCommand.goTo(1));
                current.receive();
                switch (action) {
                    case "rebind" -> replacement.register(password);
                    case "unregister" -> replacement.register(password, 0);
                    default -> server.close();
                }
                assertThrows(ExecutionException.class, () -> query.get(2, TimeUnit.SECONDS));
                assertThrows(ExecutionException.class, () -> control.get(2, TimeUnit.SECONDS));
            }
        }
    }

    @Test void heartbeatExpiryFailsBothPendingOperations() throws Exception {
        int port = freePort();
        try (var server = open(options(port).heartbeatInterval(Duration.ofMillis(150)).heartbeatMisses(2).build());
             var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var query = server.queryPresets(DEVICE, CHANNEL);
            peer.respond(peer.receive(), 200);
            var control = server.preset(DEVICE, CHANNEL, PresetCommand.goTo(1));
            peer.receive();
            assertThrows(ExecutionException.class, () -> query.get(2, TimeUnit.SECONDS));
            assertThrows(ExecutionException.class, () -> control.get(2, TimeUnit.SECONDS));
            assertTrue(server.devices().isEmpty());
        }
    }

    @Test void invalidArgumentsAndOfflineOwnersAreRejectedSynchronously() throws Exception {
        int port = freePort();
        try (var server = open(options(port).build())) {
            assertThrows(IllegalArgumentException.class, () -> server.queryPresets("invalid", CHANNEL));
            assertThrows(IllegalArgumentException.class, () -> server.queryPresets(DEVICE, "invalid"));
            assertThrows(IllegalArgumentException.class, () -> server.queryPresets(null, CHANNEL));
            assertThrows(IllegalArgumentException.class, () -> server.preset("invalid", CHANNEL, PresetCommand.set(1)));
            assertThrows(IllegalArgumentException.class, () -> server.preset(DEVICE, null, PresetCommand.set(1)));
            assertThrows(NullPointerException.class, () -> server.preset(DEVICE, CHANNEL, null));
            assertThrows(IllegalStateException.class, () -> server.queryPresets(DEVICE, CHANNEL));
            assertThrows(IllegalStateException.class, () -> server.preset(DEVICE, CHANNEL, PresetCommand.set(1)));
        }
    }

    Gb28181Server open(GbServerOptions options) { return Gb28181Server.open(options, id -> Optional.of(password)); }
    static void assertTimeout(CompletableFuture<?> result) {
        assertInstanceOf(TimeoutException.class,
                assertThrows(ExecutionException.class, () -> result.get(2, TimeUnit.SECONDS)).getCause());
    }
    static String sn(Wire request) { return match(request.body(), "<SN>([0-9]+)</SN>"); }
    static String page(String sn, int total, String... items) {
        return "<Response><CmdType>PresetQuery</CmdType><SN>" + sn + "</SN><DeviceID>" + CHANNEL
                + "</DeviceID><SumNum>" + total + "</SumNum><PresetList Num=\"" + items.length + "\">"
                + String.join("", items) + "</PresetList></Response>";
    }
    static String item(String id, String name) {
        return "<Item><PresetID>" + id + "</PresetID><PresetName>" + name + "</PresetName></Item>";
    }
}
