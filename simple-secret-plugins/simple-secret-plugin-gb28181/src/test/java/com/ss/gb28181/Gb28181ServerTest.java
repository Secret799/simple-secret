package com.ss.gb28181;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class Gb28181ServerTest {
    static final String DEVICE = "34020000001320000001";
    static final String SERVER = "34020000002000000001";
    static final String REALM = "3402000000";
    // Ephemeral test-only credentials, never a real device credential.
    final String password = UUID.randomUUID().toString();

    @ParameterizedTest
    @ValueSource(strings = {"UDP", "TCP"})
    void registersQueriesSplitCatalogOnOriginalConnectionAndUnregisters(String transport) throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).build(), id ->
                DEVICE.equals(id) ? Optional.of(password) : Optional.empty());
             var peer = new Peer(port, transport)) {
            peer.register(password);
            assertEquals(DEVICE, server.devices().get(0).deviceId());
            assertEquals("3.0", server.devices().get(0).protocolVersion());
            peer.message("<Notify><CmdType>Keepalive</CmdType><SN>1</SN><DeviceID>" + DEVICE
                    + "</DeviceID><Status>OK</Status></Notify>");
            assertEquals(200, peer.receive().status());

            var query = server.queryCatalog(DEVICE);
            Wire request = peer.receive();
            assertTrue(request.start().startsWith("MESSAGE sip:" + DEVICE + "@"));
            assertTrue(request.body().contains("<CmdType>Catalog</CmdType>"));
            String sn = match(request.body(), "<SN>([0-9]+)</SN>");
            peer.respond(request, 200);
            assertFalse(query.isDone(), "SIP 200 must not complete a Catalog query");
            peer.message(catalog(sn, 2, "34020000001320000002", "一号通道"));
            assertEquals(200, peer.receive().status());
            assertFalse(query.isDone());
            peer.message(catalog(sn, 2, "34020000001320000002", "一号通道"));
            assertEquals(200, peer.receive().status());
            assertFalse(query.isDone(), "duplicate items must not count twice");
            peer.message(catalog(sn, 2, "34020000001320000003", "二号通道"));
            assertEquals(200, peer.receive().status());
            var items = query.get(2, TimeUnit.SECONDS);
            assertEquals(List.of("一号通道", "二号通道"), items.stream().map(CatalogItem::name).toList());
            assertThrows(UnsupportedOperationException.class, () -> items.clear());
            peer.register(password, 0);
            assertTrue(server.devices().isEmpty());
            assertThrows(IllegalStateException.class, () -> server.queryCatalog(DEVICE));
        }
        try (var tcp = new ServerSocket(port); var udp = new DatagramSocket(port)) {
            assertTrue(tcp.isBound());
            assertTrue(udp.isBound());
        }
    }

    @Test
    void rejectsWrongPasswordReplayAndUnregisteredHeartbeat() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password));
             var peer = new Peer(port, "UDP")) {
            peer.message("<Notify><CmdType>Keepalive</CmdType><SN>1</SN><DeviceID>" + DEVICE
                    + "</DeviceID><Status>OK</Status></Notify>");
            assertEquals(403, peer.receive().status());
            String nonce = peer.challenge();
            peer.sendRegister(peer.authorization(nonce, "wrong-" + password), 3600);
            assertEquals(403, peer.receive().status());
            assertTrue(server.devices().isEmpty());
            nonce = peer.challenge();
            String auth = peer.authorization(nonce, password);
            peer.sendRegister(auth, 3600);
            assertEquals(200, peer.receive().status());
            peer.sendRegister(auth, 3600);
            assertEquals(403, peer.receive().status(), "same nonce-count must not authorize a new transaction");
        }
    }

    @Test
    void acceptsLegacyDigestAndRejectsAlteredCredentialScope() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).build(), id ->
                DEVICE.equals(id) ? Optional.of(password) : Optional.empty());
             var peer = new Peer(port, "UDP")) {
            String nonce = peer.challenge();
            String auth = peer.authorization(nonce, password);
            for (String changed : List.of(
                    auth.replace("username=\"" + DEVICE, "username=\"34020000001320000099"),
                    auth.replace("realm=\"" + REALM, "realm=\"wrong"),
                    auth.replace("algorithm=MD5", "algorithm=SHA-256"),
                    auth.replace("qop=auth", "qop=auth-int"),
                    auth.replace("nc=00000001", "nc=00000000"),
                    auth.replace("uri=\"sip:" + SERVER, "uri=\"sip:34020000002000000099"))) {
                peer.sendRegister(changed, 3600);
                assertEquals(403, peer.receive().status());
                assertTrue(server.devices().isEmpty());
            }
            String uri = "sip:" + SERVER + "@" + REALM;
            String response = md5(md5(DEVICE + ":" + REALM + ":" + password) + ":" + nonce + ":" + md5("REGISTER:" + uri));
            String legacy = "Digest username=\"" + DEVICE + "\", realm=\"" + REALM + "\", nonce=\"" + nonce
                    + "\", uri=\"" + uri + "\", response=\"" + response + "\"";
            peer.sendRegister(legacy, 3600);
            assertEquals(200, peer.receive().status());
            peer.sendRegister(legacy, 3600);
            assertEquals(403, peer.receive().status());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"UDP", "TCP"})
    void acceptsIpv6LiteralTarget(String transport) throws Exception {
        if ("TCP".equals(transport)) Assumptions.assumeTrue(ipv6TcpLoopbackAvailable(),
                "Environment redirects IPv6 TCP loopback connections away from the IPv6 listener");
        var loopback = InetAddress.getByName("::1");
        int port;
        try (var reservation = new ServerSocket(0, 1, loopback)) { port = reservation.getLocalPort(); }
        try (var server = Gb28181Server.open(options(port).bindAddress("::1").advertisedAddress("::1").build(),
                id -> Optional.of(password)); var peer = new Peer(port, transport, loopback)) {
            peer.targetDomain = "[0:0:0:0:0:0:0:1]";
            peer.register(password);
            assertEquals(1, server.devices().size());
        }
    }

    @Test
    void refreshWithUnknownOrExpiredNonceGetsNewChallenge() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password));
             var peer = new Peer(port, "UDP")) {
            peer.register(password);
            peer.sendRegister(peer.authorization("expired-nonce", password), 3600);
            Wire challenge = peer.receive();
            assertEquals(401, challenge.status());
            String nonce = match(challenge.headers().get("www-authenticate"), "nonce=\"([^\"]+)\"");
            peer.sendRegister(peer.authorization(nonce, password), 3600);
            assertEquals(200, peer.receive().status());
            assertEquals(1, server.devices().size());
        }
    }

    @Test
    void rejectsSpoofedSourceAndCancelsPendingQueriesOnClose() throws Exception {
        int port = freePort();
        var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password));
        try (server; var peer = new Peer(port, "UDP"); var spoof = new Peer(port, "UDP")) {
            peer.register(password);
            var query = server.queryCatalog(DEVICE);
            var request = peer.receive();
            peer.respond(request, 200);
            String sn = match(request.body(), "<SN>([0-9]+)</SN>");
            spoof.message(catalog(sn, 1, "34020000001320000002", "spoof"));
            assertEquals(403, spoof.receive().status());
            assertFalse(query.isDone());
            server.close();
            assertThrows(ExecutionException.class, () -> query.get(1, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> server.queryCatalog(DEVICE));
            server.close();
        }
    }

    @Test
    void boundsPendingQueriesAndReleasesCapacityAfterTimeoutAndCancellation() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).queryTimeout(Duration.ofMillis(300))
                .maxPendingQueries(1).build(), id -> Optional.of(password));
             var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var first = server.queryCatalog(DEVICE);
            peer.respond(peer.receive(), 200);
            assertThrows(IllegalStateException.class, () -> server.queryCatalog(DEVICE));
            ExecutionException e = assertThrows(ExecutionException.class, () -> first.get(2, TimeUnit.SECONDS));
            assertInstanceOf(TimeoutException.class, e.getCause());
            var second = server.queryCatalog(DEVICE);
            peer.respond(peer.receive(), 200);
            second.cancel(false);
            var third = server.queryCatalog(DEVICE);
            assertFalse(third.isDone());
        }
    }

    @Test
    void heartbeatExpiryRemovesDeviceAndFailsPendingQuery() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).heartbeatInterval(Duration.ofMillis(150))
                .heartbeatMisses(2).build(), id -> Optional.of(password));
             var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var query = server.queryCatalog(DEVICE);
            peer.respond(peer.receive(), 200);
            assertThrows(ExecutionException.class, () -> query.get(2, TimeUnit.SECONDS));
            assertTrue(server.devices().isEmpty());
        }
    }

    @Test
    void sipFailureFailsQueryAndInconsistentCatalogCannotCompleteIt() throws Exception {
        int port = freePort();
        try (var server = Gb28181Server.open(options(port).build(), id -> Optional.of(password));
             var peer = new Peer(port, "UDP")) {
            peer.register(password);
            var rejected = server.queryCatalog(DEVICE);
            peer.respond(peer.receive(), 403);
            assertThrows(ExecutionException.class, () -> rejected.get(2, TimeUnit.SECONDS));
            var query = server.queryCatalog(DEVICE);
            Wire request = peer.receive();
            peer.respond(request, 200);
            String sn = match(request.body(), "<SN>([0-9]+)</SN>");
            peer.message(catalog(sn, 2, "34020000001320000002", "first"));
            assertEquals(200, peer.receive().status());
            peer.message(catalog(sn, 1, "34020000001320000003", "second"));
            assertEquals(400, peer.receive().status());
            assertThrows(ExecutionException.class, () -> query.get(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void startupFailureReleasesUdpSocketAndInvalidOptionsFailBeforeStart() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> options(5060).serverId("bad").build());
        assertThrows(IllegalArgumentException.class, () -> options(5060).maxMessageBytes(0).build());
        assertThrows(IllegalArgumentException.class, () -> options(5060).queryTimeout(Duration.ZERO).build());
        try (var occupied = new ServerSocket()) {
            occupied.bind(new InetSocketAddress("127.0.0.1", freePort()));
            int port = occupied.getLocalPort();
            assertThrows(IllegalStateException.class, () -> {
                try (var unexpected = Gb28181Server.open(options(port).build(), id -> Optional.empty())) {
                    fail("A second listener must not start on the occupied endpoint");
                }
            });
            try (var udp = new DatagramSocket(port)) { assertTrue(udp.isBound()); }
        }
    }

    @Test
    void udpBindFailureAlsoReleasesTcpListener() throws Exception {
        int port = freePort();
        try (var occupied = new DatagramSocket(new InetSocketAddress("127.0.0.1", port))) {
            assertThrows(IllegalStateException.class, () -> {
                try (var unexpected = Gb28181Server.open(options(port).build(), id -> Optional.empty())) {
                    fail("An occupied UDP endpoint must reject startup");
                }
            });
            try (var tcp = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))) {
                assertTrue(tcp.isBound());
            }
        }
    }

    static GbServerOptions.Builder options(int port) {
        return GbServerOptions.builder().serverId(SERVER).realm(REALM).bindAddress("127.0.0.1")
                .advertisedAddress("127.0.0.1").port(port).queryTimeout(Duration.ofSeconds(3));
    }

    static String catalog(String sn, int total, String id, String name) {
        return "<Response><CmdType>Catalog</CmdType><SN>" + sn + "</SN><DeviceID>" + DEVICE
                + "</DeviceID><SumNum>" + total + "</SumNum><DeviceList Num=\"1\"><Item><DeviceID>"
                + id + "</DeviceID><Name>" + name + "</Name><ParentID>" + DEVICE
                + "</ParentID><Status>ON</Status></Item></DeviceList></Response>";
    }

    static boolean ipv6TcpLoopbackAvailable() throws IOException {
        // Probe JDK sockets independently of SIP. Some host proxies redirect ::1 TCP to IPv4.
        try (var listener = new ServerSocket(0, 1, InetAddress.getByName("::1")); var client = new Socket()) {
            listener.setSoTimeout(500);
            client.connect(new InetSocketAddress("::1", listener.getLocalPort()), 500);
            try (var accepted = listener.accept()) {
                return client.getLocalAddress() instanceof Inet6Address && accepted.getInetAddress() instanceof Inet6Address;
            } catch (SocketTimeoutException redirected) { return false; }
        } catch (SocketException unavailable) { return false; }
    }

    static int freePort() throws IOException {
        for (int attempt = 0; attempt < 20; attempt++) {
            try (var tcp = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
                int port = tcp.getLocalPort();
                try (var udp = new DatagramSocket(port)) { return port; }
                catch (BindException occupiedUdp) { /* TCP and UDP have independent port allocations. */ }
            }
        }
        throw new IOException("Cannot reserve a TCP/UDP test endpoint");
    }
    static String match(String text, String regex) {
        var m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
        assertTrue(m.find(), () -> "Missing field matching " + regex);
        return m.group(1);
    }
    static String md5(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    record Wire(String start, Map<String, String> headers, String body) {
        int status() { return Integer.parseInt(start.split(" ")[1]); }
        static Wire parse(byte[] bytes) {
            String all = new String(bytes, StandardCharsets.UTF_8);
            String[] halves = all.split("\r\n\r\n", 2);
            String[] lines = halves[0].split("\r\n");
            Map<String, String> headers = new HashMap<>();
            for (int i = 1; i < lines.length; i++) {
                int at = lines[i].indexOf(':');
                headers.put(lines[i].substring(0, at).toLowerCase(Locale.ROOT), lines[i].substring(at + 1).trim());
            }
            return new Wire(lines[0], headers, halves.length == 2 ? halves[1] : "");
        }
    }

    static final class Peer implements AutoCloseable {
        final int serverPort;
        final String transport;
        final DatagramSocket udp;
        final Socket tcp;
        final String callId = UUID.randomUUID() + "@test";
        final InetAddress address;
        String targetDomain = REALM;
        int sequence = 0;
        Peer(int serverPort, String transport) throws IOException {
            this(serverPort, transport, InetAddress.getByName("127.0.0.1"));
        }
        Peer(int serverPort, String transport, InetAddress address) throws IOException {
            this.serverPort = serverPort; this.transport = transport; this.address = address;
            if (transport.equals("TCP")) {
                tcp = new Socket(address, serverPort); tcp.setSoTimeout(3000); udp = null;
            } else {
                udp = new DatagramSocket(new InetSocketAddress(address, 0)); udp.setSoTimeout(3000); tcp = null;
            }
        }
        int port() { return udp == null ? tcp.getLocalPort() : udp.getLocalPort(); }
        void register(String password) throws Exception { register(password, 3600); }
        void register(String password, int expires) throws Exception {
            sendRegister(authorization(challenge(), password), expires);
            assertEquals(200, receive().status());
        }
        String challenge() throws Exception {
            sendRegister(null, 3600);
            Wire response = receive(); assertEquals(401, response.status());
            assertEquals("3.0", response.headers().get("x-gb-ver"));
            return match(response.headers().get("www-authenticate"), "nonce=\"([^\"]+)\"");
        }
        String authorization(String nonce, String password) throws Exception {
            String uri = "sip:" + SERVER + "@" + targetDomain;
            String ha1 = md5(DEVICE + ":" + REALM + ":" + password);
            String digest = md5(ha1 + ":" + nonce + ":00000001:test-client:auth:" + md5("REGISTER:" + uri));
            return "Digest username=\"" + DEVICE + "\", realm=\"" + REALM + "\", nonce=\"" + nonce
                    + "\", uri=\"" + uri + "\", response=\"" + digest
                    + "\", algorithm=MD5, qop=auth, nc=00000001, cnonce=\"test-client\"";
        }
        void sendRegister(String auth, int expires) throws IOException {
            sendRequest("REGISTER", "Contact: <sip:" + DEVICE + "@192.0.2.1:5099>\r\nExpires: " + expires
                    + "\r\nX-GB-Ver: 3.0\r\n" + (auth == null ? "" : "Authorization: " + auth + "\r\n"), "");
        }
        void message(String body) throws IOException {
            sendRequest("MESSAGE", "Content-Type: Application/MANSCDP+xml\r\n", body);
        }
        void sendRequest(String method, String extra, String body) throws IOException {
            sequence++;
            send(method + " sip:" + SERVER + "@" + targetDomain + " SIP/2.0\r\nVia: SIP/2.0/" + transport
                    + " " + (address instanceof Inet6Address ? "[" + address.getHostAddress() + "]" : address.getHostAddress()) + ":" + port() + ";rport;branch=z9hG4bK" + UUID.randomUUID()
                    + "\r\nFrom: <sip:" + DEVICE + "@" + REALM + ">;tag=device-tag\r\nTo: <sip:" + SERVER
                    + "@" + REALM + ">\r\nCall-ID: " + callId + "\r\nCSeq: " + sequence + " " + method
                    + "\r\nMax-Forwards: 70\r\n" + extra + "Content-Length: "
                    + body.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n" + body);
        }
        void respond(Wire request, int status) throws IOException {
            StringBuilder b = new StringBuilder("SIP/2.0 " + status + (status == 200 ? " OK" : " Forbidden") + "\r\n");
            for (String h : List.of("via", "from", "to", "call-id", "cseq"))
                b.append(h).append(": ").append(request.headers().get(h)).append(h.equals("to") ? ";tag=reply" : "").append("\r\n");
            send(b + "Content-Length: 0\r\n\r\n");
        }
        void send(String content) throws IOException {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            if (udp != null) udp.send(new DatagramPacket(bytes, bytes.length, address, serverPort));
            else { tcp.getOutputStream().write(bytes); tcp.getOutputStream().flush(); }
        }
        Wire receive() throws IOException {
            if (udp != null) {
                var packet = new DatagramPacket(new byte[65535], 65535); udp.receive(packet);
                return Wire.parse(Arrays.copyOf(packet.getData(), packet.getLength()));
            }
            var out = new ByteArrayOutputStream();
            var in = tcp.getInputStream();
            String line;
            int length = 0;
            while (true) {
                var lineBytes = new ByteArrayOutputStream(); int c;
                while ((c = in.read()) != -1) { lineBytes.write(c); if (c == '\n') break; }
                if (c == -1) throw new EOFException();
                byte[] bytes = lineBytes.toByteArray(); out.write(bytes);
                line = new String(bytes, StandardCharsets.US_ASCII);
                if (line.toLowerCase(Locale.ROOT).startsWith("content-length:"))
                    length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                if (line.equals("\r\n")) break;
                if (out.size() > 65535) throw new IOException("Headers too large");
            }
            out.write(in.readNBytes(length)); return Wire.parse(out.toByteArray());
        }
        @Override public void close() throws IOException { if (udp != null) udp.close(); if (tcp != null) tcp.close(); }
    }
}
