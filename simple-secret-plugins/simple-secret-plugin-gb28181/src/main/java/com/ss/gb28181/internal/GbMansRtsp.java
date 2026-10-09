package com.ss.gb28181.internal;

import com.ss.gb28181.GbPlaybackControl;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Bounded historical playback control codec, GB/T 28181-2022 Annex B. */
public final class GbMansRtsp {
    private static final int MAX_LINES = 64;
    private static final int MAX_LINE_CHARS = 1024;
    private static final int MAX_HEADER_BYTES = MAX_LINES * (MAX_LINE_CHARS * 4 + 2);

    private GbMansRtsp() { }

    public static byte[] request(GbPlaybackControl control, long cseq) {
        Objects.requireNonNull(control, "control");
        if (cseq <= 0) throw new IllegalArgumentException("RTSP CSeq must be positive");
        String method = control.type() == GbPlaybackControl.Type.PAUSE ? "PAUSE" : "PLAY";
        String header = switch (control.type()) {
            case PAUSE -> "PauseTime: now";
            case RESUME -> "Range: npt=now-";
            case SEEK -> "Range: npt=" + control.position().getSeconds() + "-";
            case SPEED -> "Scale: " + control.scale();
        };
        return (method + " RTSP/1.0\r\nCSeq: " + cseq + "\r\n" + header + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    /** Empty SIP success bodies are handled by the caller; a supplied RTSP body must be valid and successful. */
    public static void validateResponse(byte[] bytes, long cseq, int maxBytes) {
        if (cseq <= 0 || maxBytes <= 0 || bytes == null || bytes.length == 0 || bytes.length > maxBytes)
            throw invalid();
        int headerEnd = -1;
        for (int i = 0; i <= bytes.length - 4 && i <= MAX_HEADER_BYTES; i++) {
            if (bytes[i] == '\r' && bytes[i + 1] == '\n' && bytes[i + 2] == '\r' && bytes[i + 3] == '\n') {
                headerEnd = i;
                break;
            }
        }
        if (headerEnd < 0) throw invalid();
        String headers;
        try {
            headers = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, 0, headerEnd)).toString();
        } catch (CharacterCodingException exception) { throw invalid(); }
        String[] lines = headers.split("\r\n", -1);
        if (lines.length < 2 || lines.length > MAX_LINES) throw invalid();
        for (String line : lines) {
            if (line.length() > MAX_LINE_CHARS) throw invalid();
            for (int i = 0; i < line.length(); i++) {
                char character = line.charAt(i);
                if ((Character.isISOControl(character) && character != '\t')
                        || character == '\u2028' || character == '\u2029') throw invalid();
            }
        }
        if (!lines[0].matches("RTSP/1\\.0 2[0-9]{2} [^\\r\\n]*")) throw invalid();
        Set<String> names = new HashSet<>();
        Long actualCseq = null;
        long contentLength = 0;
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon <= 0) throw invalid();
            String name = lines[i].substring(0, colon);
            if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) throw invalid();
            name = name.toLowerCase(Locale.ROOT);
            if (!names.add(name)) throw invalid();
            String value = lines[i].substring(colon + 1).trim();
            if ("cseq".equals(name)) actualCseq = decimal(value);
            else if ("content-length".equals(name)) contentLength = decimal(value);
        }
        if (actualCseq == null || actualCseq != cseq || contentLength != bytes.length - headerEnd - 4)
            throw invalid();
    }

    private static long decimal(String value) {
        if (!value.matches("[0-9]{1,19}")) throw invalid();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) { throw invalid(); }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid or unsuccessful GB MANSRTSP response");
    }
}
