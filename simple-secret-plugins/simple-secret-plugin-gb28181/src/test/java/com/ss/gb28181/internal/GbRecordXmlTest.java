package com.ss.gb28181.internal;

import com.ss.gb28181.RecordQuery;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GbRecordXmlTest {
    private static final String CHANNEL = "34020000001320000001";

    @Test
    void parsesStandardEmptyResponseWithoutResult() {
        var message = GbXml.parse(response(0, ""), 8192, 10);
        assertEquals("RecordInfo", message.command());
        assertEquals(0, message.total());
        assertEquals(List.of(), message.records());
        assertNull(message.result());
    }

    @Test
    void encodesQueryAsDeviceTimeWithSecondsAndLowercaseType() {
        for (var type : RecordQuery.Type.values()) {
            var query = new RecordQuery(LocalDateTime.of(1, 1, 1, 0, 0),
                    LocalDateTime.of(9999, 12, 31, 23, 59, 59), type);
            String xml = new String(GbXml.recordQuery(CHANNEL, 42, query), StandardCharsets.UTF_8);
            assertTrue(xml.contains("<CmdType>RecordInfo</CmdType><SN>42</SN><DeviceID>" + CHANNEL
                    + "</DeviceID><StartTime>0001-01-01T00:00:00</StartTime>"
                    + "<EndTime>9999-12-31T23:59:59</EndTime><Type>" + type.name().toLowerCase(java.util.Locale.ROOT)
                    + "</Type>"), xml);
        }
        var query = RecordQuery.all(LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 2, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> GbXml.recordQuery("bad", 1, query));
        assertThrows(IllegalArgumentException.class, () -> GbXml.recordQuery(CHANNEL, 0, query));
        assertThrows(NullPointerException.class, () -> GbXml.recordQuery(CHANNEL, 1, null));
    }

    @Test
    void parsesRequiredAndOptionalFieldsWithoutDereferencingPaths() {
        String item = item("<FilePath>../../archive &amp; clip.ps</FilePath><Address>大门</Address>"
                + "<StartTime>2026-09-15T01:00:00.250+08:00</StartTime>"
                + "<EndTime>2026-09-14T17:00:01Z</EndTime><Type>alarm</Type>"
                + "<RecorderID>trigger-1</RecorderID><FileSize>9223372036854775807</FileSize>");
        var message = parse(response(2, item));
        var record = message.records().get(0);
        assertEquals(CHANNEL, record.deviceId());
        assertEquals("摄像头", record.name());
        assertEquals("../../archive & clip.ps", record.filePath());
        assertEquals("大门", record.address());
        assertEquals("2026-09-15T01:00:00.250+08:00", record.startTime());
        assertEquals("2026-09-14T17:00:01Z", record.endTime());
        assertEquals(0, record.secrecy());
        assertEquals("alarm", record.type());
        assertEquals("trigger-1", record.recorderId());
        assertEquals(Long.MAX_VALUE, record.fileSize());
        assertThrows(UnsupportedOperationException.class, () -> message.records().clear());
        var minimal = parse(response(1, item(""))).records().get(0);
        assertNull(minimal.filePath());
        assertNull(minimal.startTime());
        assertNull(minimal.endTime());
        assertNull(minimal.type());
        assertNull(minimal.fileSize());
        String gb2312 = "<?xml version=\"1.0\" encoding=\"GB2312\"?>" + new String(response(1, item("")), StandardCharsets.UTF_8);
        assertEquals("摄像头", parse(gb2312.getBytes(Charset.forName("GB2312"))).records().get(0).name());
    }

    @Test
    void rejectsInvalidRecordFieldsAndLimits() {
        for (String field : List.of("<Type>all</Type>", "<Type>unknown</Type>", "<FileSize>-1</FileSize>",
                "<FileSize>9223372036854775808</FileSize>", "<FileSize>1.5</FileSize>",
                "<StartTime>2026-02-30T00:00:00</StartTime>", "<EndTime>2026-09-15</EndTime>",
                "<StartTime>" + "2".repeat(65) + "</StartTime>",
                "<FilePath>" + "x".repeat(1025) + "</FilePath>",
                "<Address>" + "x".repeat(257) + "</Address>",
                "<RecorderID>" + "x".repeat(129) + "</RecorderID>",
                "<Type><Nested>time</Nested></Type>", "<Type>time</Type><Type>time</Type>",
                "<StartTime>2026-09-15T10:00:00Z</StartTime><EndTime>2026-09-15T10:00:01+08:00</EndTime>")) {
            assertThrows(IllegalArgumentException.class, () -> parse(response(1, item(field))), field);
        }
        String valid = new String(response(1, item("")), StandardCharsets.UTF_8);
        for (String invalid : List.of(valid.replace("<Secrecy>0</Secrecy>", ""),
                valid.replace("<Secrecy>0", "<Secrecy>2"), valid.replace("<Name>摄像头</Name>", ""),
                valid.replace("<Name>大门</Name>", ""), valid.replace("<Name>摄像头", "<Name>" + "x".repeat(257)),
                valid.replace("<Item><DeviceID>" + CHANNEL, "<Item><DeviceID>34020000001320000002"),
                valid.replace("Num=\"1\"", "Num=\"0\""), valid.replace("Num=\"1\"", ""),
                valid.replace("<SumNum>1", "<SumNum>0"), valid.replace("<SumNum>1", "<SumNum>11"))) {
            assertThrows(IllegalArgumentException.class, () -> parse(invalid.getBytes(StandardCharsets.UTF_8)), invalid);
        }
        assertThrows(IllegalArgumentException.class, () -> GbXml.parse(response(1, item("")), 8192, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> GbXml.parse(response(0, ""), 1, 10, 10));
    }

    @Test
    void acceptsEqualTimesAndIndependentlyOptionalTimes() {
        for (String fields : List.of("<StartTime>2026-09-15T10:00:00</StartTime>",
                "<EndTime>2026-09-15T10:00:00Z</EndTime>",
                "<StartTime>2026-09-15T10:00:00</StartTime><EndTime>2026-09-15T10:00:00</EndTime>")) {
            assertEquals(1, parse(response(1, item(fields))).records().size());
        }
        assertEquals(1, GbXml.parse(response(1, item("")), 8192, 0, 10).records().size());
    }

    @Test
    void retainsMixedTimezoneTimesWithoutInventingADeviceTimezone() {
        var record = parse(response(1, item("<StartTime>2026-09-15T10:00:00</StartTime>"
                + "<EndTime>2026-09-15T10:00:01Z</EndTime>"))).records().get(0);
        assertEquals("2026-09-15T10:00:00", record.startTime());
        assertEquals("2026-09-15T10:00:01Z", record.endTime());
    }

    @Test
    void preservesLegacyConstructorsAndDefaultRecordCapacity() {
        assertEquals(List.of(), new GbXml.Message("Response", "Catalog", 1, CHANNEL, null, 0,
                List.of()).records());
        assertEquals(List.of(), new GbXml.Message("Response", "DeviceInfo", 1, CHANNEL, null, 0,
                List.of(), "OK", null, null).records());
        assertEquals(10_000, GbXml.parse(response(10_000, item("")), 8192, 0).total());
        assertThrows(IllegalArgumentException.class, () -> GbXml.parse(response(10_001, item("")), 8192, 0));
    }

    @Test
    void enforcesXmlSecurityForRecordResponses() {
        String valid = new String(response(1, item("")), StandardCharsets.UTF_8);
        String entity = "<!DOCTYPE Response [<!ENTITY external SYSTEM 'file:///not-a-record-source'>]>"
                + valid.replace("摄像头", "&external;");
        assertThrows(IllegalArgumentException.class, () -> parse(entity.getBytes(StandardCharsets.UTF_8)));
        String deep = valid.replace("<Secrecy>0", "<Unknown>".repeat(33) + "</Unknown>".repeat(33) + "<Secrecy>0");
        assertThrows(IllegalArgumentException.class, () -> parse(deep.getBytes(StandardCharsets.UTF_8)));
        String excessive = valid.replace("<Secrecy>0", "<X/>".repeat(2048) + "<Secrecy>0");
        assertThrows(IllegalArgumentException.class, () -> GbXml.parse(excessive.getBytes(StandardCharsets.UTF_8), 16384, 10, 10));
    }

    private static GbXml.Message parse(byte[] body) {
        return GbXml.parse(body, 8192, 10, 10);
    }

    private static String item(String fields) {
        return "<Item><DeviceID>" + CHANNEL + "</DeviceID><Name>摄像头</Name><Secrecy>0</Secrecy>"
                + fields + "</Item>";
    }

    private static byte[] response(int total, String items) {
        return ("<Response><CmdType>RecordInfo</CmdType><SN>7</SN><DeviceID>" + CHANNEL
                + "</DeviceID><Name>大门</Name><SumNum>" + total + "</SumNum><RecordList Num=\""
                + (items.isEmpty() ? 0 : 1) + "\">" + items + "</RecordList></Response>")
                .getBytes(StandardCharsets.UTF_8);
    }
}
