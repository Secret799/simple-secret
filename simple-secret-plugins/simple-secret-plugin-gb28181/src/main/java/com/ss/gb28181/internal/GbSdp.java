package com.ss.gb28181.internal;

import com.ss.gb28181.GbDownloadRequest;
import com.ss.gb28181.GbRtpTarget;
import com.ss.gb28181.GbPlaybackRange;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/** Bounded SDP for a single live, playback or download PS/RTP stream, GB/T 28181-2022 Annexes D and G. */
public final class GbSdp {
    private static final int MAX_LINES = 256;
    private static final int MAX_LINE_CHARS = 1024;

    private GbSdp() { }

    public static byte[] offer(String serverId, String channelId, GbRtpTarget target, String ssrc) {
        return offer(serverId, channelId, target, ssrc, null);
    }

    public static byte[] offer(String serverId, String channelId, GbRtpTarget target, String ssrc,
                               GbPlaybackRange range) {
        return offer(serverId, channelId, target, ssrc, range, null);
    }

    public static byte[] downloadOffer(String serverId, String channelId, GbRtpTarget target, String ssrc,
                                       GbDownloadRequest request) {
        Objects.requireNonNull(request, "request");
        return offer(serverId, channelId, target, ssrc, request.range(), request);
    }

    private static byte[] offer(String serverId, String channelId, GbRtpTarget target, String ssrc,
                                GbPlaybackRange range, GbDownloadRequest download) {
        identifier(serverId);
        identifier(channelId);
        arguments(target, ssrc, range);
        String connection = "IN " + family(target.address()) + " " + target.address();
        String sdp = "v=0\r\no=" + serverId + " 0 0 " + connection + "\r\ns=" + (download == null ? mode(range) : "Download") + "\r\n"
                + (range == null ? "" : "u=" + channelId + ":0\r\n") + "c=" + connection
                + "\r\nt=" + timing(range) + "\r\nm=video " + target.port() + " " + protocol(target) + " 96\r\n"
                + "a=recvonly\r\na=rtpmap:96 PS/90000\r\n";
        if (target.transport() == GbRtpTarget.Transport.TCP_PASSIVE)
            sdp += "a=setup:passive\r\na=connection:new\r\n";
        if (download != null) sdp += "a=downloadspeed:" + download.speed() + "\r\n";
        return (sdp + "y=" + ssrc + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    /** Validates negotiation only; neither the SDP sender address nor its port is contacted. */
    public static void validateAnswer(byte[] bytes, GbRtpTarget target, String ssrc, int maxBytes) {
        validateAnswer(bytes, target, ssrc, maxBytes, null);
    }

    public static void validateAnswer(byte[] bytes, GbRtpTarget target, String ssrc, int maxBytes,
                                      GbPlaybackRange range) {
        validateAnswer(bytes, target, ssrc, maxBytes, range, false);
    }

    /** Returns the device's declared byte count only, not received bytes or file completion. */
    public static OptionalLong validateDownloadAnswer(byte[] bytes, GbRtpTarget target, String ssrc, int maxBytes,
                                                      GbDownloadRequest request) {
        Objects.requireNonNull(request, "request");
        return validateAnswer(bytes, target, ssrc, maxBytes, request.range(), true);
    }

    private static OptionalLong validateAnswer(byte[] bytes, GbRtpTarget target, String ssrc, int maxBytes,
                                               GbPlaybackRange range, boolean download) {
        arguments(target, ssrc, range);
        if (maxBytes < 1 || bytes == null || bytes.length == 0 || bytes.length > maxBytes
                || bytes.length > MAX_LINES * (MAX_LINE_CHARS * 4 + 2)) throw invalid();
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) { throw invalid(); }
        // Accept LF-only devices, but reject bare CR, controls and empty embedded lines.
        text = text.replace("\r\n", "\n");
        int lineCount = 0;
        int lineLength = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((Character.isISOControl(c) && c != '\n' && c != '\t') || c == '\u2028' || c == '\u2029')
                throw invalid();
            if (c == '\n') {
                if (++lineCount > MAX_LINES) throw invalid();
                lineLength = 0;
            } else if (++lineLength > MAX_LINE_CHARS) throw invalid();
        }
        String[] lines = text.split("\n", -1);
        int count = lines.length - (lines[lines.length - 1].isEmpty() ? 1 : 0);
        if (count < 7 || count > MAX_LINES) throw invalid();
        Map<String, String> session = new HashMap<>();
        Map<String, String> media = new HashMap<>();
        Map<String, String> downloadAttributes = download ? new HashMap<>() : null;
        boolean inMedia = false;
        for (int i = 0; i < count; i++) {
            String line = lines[i];
            if (line.length() < 2 || line.length() > MAX_LINE_CHARS || line.charAt(1) != '=') throw invalid();
            char field = line.charAt(0);
            String value = line.substring(2);
            if (i < 3 && field != "vos".charAt(i)) throw invalid();
            Map<String, String> scope = inMedia ? media : session;
            switch (field) {
                case 'v', 'o', 's', 't' -> {
                    if (inMedia) throw invalid();
                    put(scope, String.valueOf(field), value);
                }
                case 'm' -> {
                    if (inMedia || !session.containsKey("t")) throw invalid();
                    inMedia = true;
                    put(media, "m", value);
                }
                case 'c' -> {
                    connection(value);
                    put(scope, "c", value);
                }
                case 'a' -> attribute(scope, value, inMedia, downloadAttributes);
                case 'y' -> {
                    if (!inMedia) throw invalid();
                    put(media, "y", value);
                }
                // Other standard descriptive fields do not select transport, direction or payload.
                case 'i', 'u', 'e', 'p', 'b', 'r', 'z', 'f' -> { }
                default -> throw invalid();
            }
        }
        if (!"0".equals(session.get("v")) || !(download ? "Download" : mode(range)).equals(session.get("s"))
                || !timing(range).equals(session.get("t"))) throw invalid();
        origin(session.get("o"));
        if (!media.containsKey("c") && !session.containsKey("c")) throw invalid();
        String[] m = tokens(media.get("m"), 4);
        if (!"video".equals(m[0]) || !protocol(target).equals(m[2]) || !"96".equals(m[3])) throw invalid();
        if (!m[1].matches("[0-9]{1,5}") || Integer.parseInt(m[1]) < 1 || Integer.parseInt(m[1]) > 65535)
            throw invalid();
        if (!"sendonly".equals(inherit(media, session, "direction"))
                || !"PS/90000".equalsIgnoreCase(media.get("rtpmap")) || !ssrc.equals(media.get("y")))
            throw invalid();
        if (target.transport() == GbRtpTarget.Transport.TCP_PASSIVE) {
            if (!"active".equals(inherit(media, session, "setup"))
                    || !"new".equals(inherit(media, session, "connection"))) throw invalid();
        } else if (session.containsKey("setup") || media.containsKey("setup")
                || session.containsKey("connection") || media.containsKey("connection")) throw invalid();
        if (download && downloadAttributes.containsKey("filesize"))
            return OptionalLong.of(unsignedLong(downloadAttributes.get("filesize")));
        return OptionalLong.empty();
    }

    private static void attribute(Map<String, String> scope, String value, boolean inMedia,
                                  Map<String, String> downloadAttributes) {
        if (value.equals("sendonly") || value.equals("recvonly") || value.equals("sendrecv") || value.equals("inactive")) {
            put(scope, "direction", value);
        } else if (value.startsWith("setup:")) {
            put(scope, "setup", value.substring(6));
        } else if (value.startsWith("connection:")) {
            put(scope, "connection", value.substring(11));
        } else if (value.startsWith("rtpmap:")) {
            if (!inMedia) throw invalid();
            String[] map = tokens(value.substring(7), 2);
            if (!"96".equals(map[0])) throw invalid();
            put(scope, "rtpmap", map[1]);
        } else if (downloadAttributes != null) {
            // Download metadata must occur only once across session and media scopes.
            int colon = value.indexOf(':');
            String name = colon < 0 ? value : value.substring(0, colon);
            if (name.equals("filesize") || name.equals("downloadspeed")) {
                if (colon < 0) throw invalid();
                String number = value.substring(colon + 1);
                long parsed = unsignedLong(number);
                if (name.equals("downloadspeed") && (parsed < 1 || parsed > 128)) throw invalid();
                put(downloadAttributes, name, number);
            }
        }
    }

    private static long unsignedLong(String value) {
        if (!value.matches("[0-9]+")) throw invalid();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) { throw invalid(); }
    }

    private static void origin(String value) {
        String[] origin = tokens(value, 6);
        if (!origin[0].matches("[!-~]{1,128}") || !origin[1].matches("[0-9]{1,20}")
                || !origin[2].matches("[0-9]{1,20}")) throw invalid();
        connection(origin[3] + " " + origin[4] + " " + origin[5]);
    }

    private static void connection(String value) {
        String[] c = tokens(value, 3);
        if (!"IN".equals(c[0]) || !family(c[2]).equals(c[1])) throw invalid();
        new GbRtpTarget(c[2], 1, GbRtpTarget.Transport.UDP);
    }

    private static String[] tokens(String value, int count) {
        if (value == null) throw invalid();
        String[] tokens = value.split("[ \\t]+", -1);
        if (tokens.length != count) throw invalid();
        return tokens;
    }

    private static void put(Map<String, String> values, String key, String value) {
        if (values.putIfAbsent(key, value) != null) throw invalid();
    }

    private static String inherit(Map<String, String> media, Map<String, String> session, String key) {
        return media.getOrDefault(key, session.get(key));
    }

    private static String family(String address) { return address.contains(":") ? "IP6" : "IP4"; }

    private static String protocol(GbRtpTarget target) {
        return target.transport() == GbRtpTarget.Transport.UDP ? "RTP/AVP" : "TCP/RTP/AVP";
    }

    private static void identifier(String value) {
        if (value == null || !value.matches("[0-9]{20}"))
            throw new IllegalArgumentException("GB identifier must contain 20 digits");
    }

    private static String mode(GbPlaybackRange range) { return range == null ? "Play" : "Playback"; }

    private static String timing(GbPlaybackRange range) {
        return range == null ? "0 0" : range.startTime().getEpochSecond() + " " + range.endTime().getEpochSecond();
    }

    private static void arguments(GbRtpTarget target, String ssrc, GbPlaybackRange range) {
        if (target == null) throw new IllegalArgumentException("RTP target is required");
        if (ssrc == null || !ssrc.matches((range == null ? "0" : "1") + "[0-9]{9}"))
            throw new IllegalArgumentException("SSRC must contain 10 digits and match the live or playback mode");
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid or unsupported GB SDP answer");
    }
}
