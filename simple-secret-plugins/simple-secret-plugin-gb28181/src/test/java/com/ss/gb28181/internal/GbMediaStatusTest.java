package com.ss.gb28181.internal;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class GbMediaStatusTest {
    private static final String BODY = "<Notify><CmdType>MediaStatus</CmdType><SN>42</SN>"
            + "<DeviceID>34020000001320000001</DeviceID><NotifyType>121</NotifyType></Notify>";

    @Test
    void parsesMandatoryMediaEndFieldsWithoutInventingStatusOrResult() {
        var message = parse(BODY);
        assertEquals("Notify", message.kind());
        assertEquals("MediaStatus", message.command());
        assertEquals(42, message.sn());
        assertEquals("34020000001320000001", message.deviceId());
        assertEquals("121", message.status());
        assertNull(message.result());
        assertTrue(message.items().isEmpty());
        assertTrue(message.records().isEmpty());
    }

    @Test
    void rejectsMissingDuplicateNestedOrUnsupportedNotificationFields() {
        for (String field : new String[]{"<CmdType>MediaStatus</CmdType>", "<SN>42</SN>",
                "<DeviceID>34020000001320000001</DeviceID>", "<NotifyType>121</NotifyType>"}) {
            assertThrows(IllegalArgumentException.class, () -> parse(BODY.replace(field, "")));
            assertThrows(IllegalArgumentException.class, () -> parse(BODY.replace(field, field + field)));
            assertThrows(IllegalArgumentException.class, () -> parse(BODY.replace(field, "<Other>" + field + "</Other>")));
        }
        for (String value : new String[]{"", "0", "120", "122", "0121", "<Value>121</Value>", "x".repeat(1024)}) {
            assertThrows(IllegalArgumentException.class, () -> parse(BODY.replace(
                    "<NotifyType>121</NotifyType>", "<NotifyType>" + value + "</NotifyType>")));
        }
        assertThrows(IllegalArgumentException.class, () -> parse(BODY.replace("Notify>", "Response>")));
    }

    @Test
    void retainsXmlSecurityAndSizeLimitsForMediaStatus() {
        assertThrows(IllegalArgumentException.class, () -> parse("<!DOCTYPE Notify [<!ENTITY x SYSTEM 'file:///missing'>]>"
                + BODY.replace("121", "&x;")));
        byte[] bytes = BODY.getBytes(StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> GbXml.parse(bytes, bytes.length, 10));
        assertThrows(IllegalArgumentException.class, () -> GbXml.parse(bytes, bytes.length - 1, 10));
        assertThrows(IllegalArgumentException.class, () -> parse(BODY.replace("<SN>42</SN>", "<SN>0</SN>")));
    }

    private static GbXml.Message parse(String body) {
        return GbXml.parse(body.getBytes(StandardCharsets.UTF_8), 8192, 10);
    }
}
