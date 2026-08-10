package com.ss.kmz;

import com.ss.kmz.domain.Coordinate;
import com.ss.kmz.domain.KmzMission;
import com.ss.kmz.domain.Waypoint;
import com.ss.kmz.exception.KmzException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmzParserWriterTest {

    @Test
    void shouldRoundTripDocKmlWithoutClosingCallerStreams() throws Exception {
        KmzMission mission = mission("doc");
        TrackingOutputStream output = new TrackingOutputStream();

        KmzWriter.writeToStream(mission, output);
        TrackingInputStream input = new TrackingInputStream(output.toByteArray());
        KmzMission parsed = KmzParser.parse(input);

        assertThat(output.closed).isFalse();
        assertThat(input.closed).isFalse();
        assertThat(parsed).isEqualTo(mission);
        assertThat(firstEntryName(output.toByteArray())).isEqualTo("doc.kml");
    }

    @Test
    void shouldPreferDjiWaylinesThenTemplateThenDoc() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("doc.kml", KmlWriter.writeToString(mission("doc")));
        entries.put("wpmz/template.kml", KmlWriter.writeToString(mission("template")));
        entries.put("wpmz/waylines.wpml", KmlWriter.writeToString(mission("waylines")));

        KmzMission parsed = KmzParser.parse(zip(entries));

        assertThat(parsed.getMissionName()).isEqualTo("waylines");
    }

    @Test
    void shouldRejectAmbiguousFallbackCandidates() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("a.kml", KmlWriter.writeToString(mission("a")));
        entries.put("b.wpml", KmlWriter.writeToString(mission("b")));

        assertThatThrownBy(() -> KmzParser.parse(zip(entries)))
                .isInstanceOf(KmzException.class)
                .hasMessageContaining("ambiguous");
    }

    @Test
    void shouldRejectUnsafeEntryPaths() {
        assertThatThrownBy(() -> KmzParser.parse(zip(Map.of(
                "../doc.kml", KmlWriter.writeToString(mission("unsafe"))))))
                .isInstanceOf(KmzException.class)
                .hasMessageContaining("unsafe ZIP entry");
        assertThatThrownBy(() -> KmzParser.parse(zip(Map.of(
                "wpmz\\waylines.wpml", KmlWriter.writeToString(mission("unsafe"))))))
                .isInstanceOf(KmzException.class)
                .hasMessageContaining("unsafe ZIP entry");
    }

    @Test
    void shouldEnforceCompressedEntryAndEntryCountLimits() {
        byte[] kmz = zip(Map.of("doc.kml", KmlWriter.writeToString(mission("limited"))));

        assertThatThrownBy(() -> KmzParser.parse(new ByteArrayInputStream(kmz),
                new KmzReadLimits(4, 1024, 8)))
                .isInstanceOf(KmzException.class)
                .hasMessageContaining("4");
        assertThatThrownBy(() -> KmzParser.parse(new ByteArrayInputStream(kmz),
                new KmzReadLimits(1024 * 1024, 8, 8)))
                .isInstanceOf(KmzException.class)
                .hasMessageContaining("8");

        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("one.txt", "1");
        entries.put("doc.kml", KmlWriter.writeToString(mission("limited")));
        assertThatThrownBy(() -> KmzParser.parse(new ByteArrayInputStream(zip(entries)),
                new KmzReadLimits(1024 * 1024, 1024 * 1024, 1)))
                .isInstanceOf(KmzException.class)
                .hasMessageContaining("entry count");
    }

    @Test
    void shouldEnforceTotalUncompressedLimitAcrossEntries() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("one.txt", "12345678");
        entries.put("doc.kml", KmlWriter.writeToString(mission("total")));

        assertThatThrownBy(() -> KmzParser.parse(new ByteArrayInputStream(zip(entries)),
                new KmzReadLimits(1024 * 1024, 1024 * 1024, 4, 16)))
                .isInstanceOf(KmzException.class)
                .hasMessageContaining("total uncompressed");
    }

    private static KmzMission mission(String name) {
        return new KmzMission()
                .setMissionName(name)
                .setWaypoints(List.of(new Waypoint()
                        .setIndex(0)
                        .setCoordinate(new Coordinate(116.4, 39.9, 80))
                        .setExecuteHeight(100)
                        .setWaypointSpeed(8)));
    }

    private static byte[] zip(Map<String, String> entries) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(output)) {
                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    zip.putNextEntry(new ZipEntry(entry.getKey()));
                    zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static String firstEntryName(byte[] bytes) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            return zip.getNextEntry().getName();
        }
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;
        private TrackingInputStream(byte[] bytes) { super(bytes); }
        @Override public void close() { closed = true; }
    }

    private static final class TrackingOutputStream extends ByteArrayOutputStream {
        private boolean closed;
        @Override public void close() { closed = true; }
    }
}
