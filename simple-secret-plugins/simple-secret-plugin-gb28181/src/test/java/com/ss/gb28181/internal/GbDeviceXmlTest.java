package com.ss.gb28181.internal;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GbDeviceXmlTest {
    private static final String DEVICE_ID = "34020000002000000001";

    @Test
    void acceptsDeviceInformationResponseWithOnlyRequiredFields() {
        var message = parse(response("DeviceInfo", "<Result>OK</Result>"));
        assertEquals("OK", message.result());
        assertEquals(DEVICE_ID, message.deviceInfo().deviceId());
        assertNull(message.deviceInfo().deviceName());
        assertNull(message.deviceInfo().manufacturer());
        assertNull(message.deviceInfo().model());
        assertNull(message.deviceInfo().firmware());
        assertNull(message.deviceInfo().channelCount());
    }

    @Test
    void parsesAllDeviceInformationFieldsUsingDeclaredGb2312() {
        String xml = "<?xml version=\"1.0\" encoding=\"GB2312\"?>"
                + response("DeviceInfo", "<Result>OK</Result><DeviceName>入口设备</DeviceName>"
                + "<Manufacturer>示例厂商</Manufacturer><Model>IPC &amp; NVR</Model>"
                + "<Firmware>v1.2</Firmware><Channel>16</Channel>");
        var info = GbXml.parse(xml.getBytes(Charset.forName("GB2312")), 8_192, 10).deviceInfo();
        assertEquals("入口设备", info.deviceName());
        assertEquals("示例厂商", info.manufacturer());
        assertEquals("IPC & NVR", info.model());
        assertEquals("v1.2", info.firmware());
        assertEquals(16, info.channelCount());
        assertEquals(0, parse(response("DeviceInfo", "<Result>OK</Result><Channel>0</Channel>"))
                .deviceInfo().channelCount());
    }

    @Test
    void parsesStatusWithoutInventingMissingOptionalValues() {
        var status = parse(response("DeviceStatus",
                "<Result>OK</Result><Online>OFFLINE</Online><Status>ERROR</Status>")).deviceStatus();
        assertEquals(DEVICE_ID, status.deviceId());
        assertEquals("OFFLINE", status.online());
        assertEquals("ERROR", status.status());
        assertNull(status.encode());
        assertNull(status.record());
        assertNull(status.deviceTime());
    }

    @Test
    void preservesStatusDateTimeWithOrWithoutTimezone() {
        for (String time : List.of("2026-09-15T11:12:13", "2026-09-15T11:12:13.123+08:00",
                "2026-09-15T03:12:13Z")) {
            var status = parse(response("DeviceStatus", "<Result>OK</Result><Online>ONLINE</Online>"
                    + "<Status>OK</Status><Encode>ON</Encode><Record>OFF</Record>"
                    + "<DeviceTime>" + time + "</DeviceTime>")).deviceStatus();
            assertEquals("ONLINE", status.online());
            assertEquals("OK", status.status());
            assertEquals("ON", status.encode());
            assertEquals("OFF", status.record());
            assertEquals(time, status.deviceTime());
        }
    }

    @Test
    void acceptsErrorResultsWithoutSuccessPayloadAndControlAcknowledgements() {
        for (String command : List.of("DeviceInfo", "DeviceStatus", "DeviceControl")) {
            var message = parse(response(command, "<Result>ERROR</Result>"));
            assertEquals(command, message.command());
            assertEquals("ERROR", message.result());
            assertNull(message.deviceInfo());
            assertNull(message.deviceStatus());
        }
        assertEquals("OK", parse(response("DeviceControl", "<Result>OK</Result>")).result());
    }

    @Test
    void rejectsMissingOrInvalidResultAndMandatoryStatusFields() {
        for (String command : List.of("DeviceInfo", "DeviceStatus", "DeviceControl")) {
            for (String result : List.of("", "<Result/>", "<Result>SUCCESS</Result>",
                    "<Result>OK</Result><Result>ERROR</Result>")) {
                assertInvalid(response(command, result));
            }
        }
        assertInvalid(response("DeviceStatus", "<Result>OK</Result>"));
        assertInvalid(response("DeviceStatus", "<Result>OK</Result><Online>ONLINE</Online>"));
        assertInvalid(response("DeviceStatus", "<Result>OK</Result><Status>OK</Status>"));
    }

    @Test
    void rejectsInvalidStatusEnumsAndDateTimes() {
        String required = "<Result>OK</Result><Online>ONLINE</Online><Status>OK</Status>";
        for (String field : List.of("Encode", "Record")) {
            for (String invalid : List.of("1", "0", "true", "OK", "on", "x".repeat(17))) {
                assertInvalid(response("DeviceStatus", required + "<" + field + ">" + invalid
                        + "</" + field + ">"));
            }
        }
        assertInvalid(response("DeviceStatus", "<Result>OK</Result><Online>ON</Online><Status>OK</Status>"));
        assertInvalid(response("DeviceStatus", "<Result>OK</Result><Online>ONLINE</Online><Status>1</Status>"));
        for (String invalid : List.of("2026-09-15", "2026-02-30T11:00:00", "yesterday", "x".repeat(65))) {
            assertInvalid(response("DeviceStatus", required + "<DeviceTime>" + invalid + "</DeviceTime>"));
        }
    }

    @Test
    void rejectsDuplicateNestedAndOversizedDeviceFieldsEvenOnError() {
        for (String result : List.of("OK", "ERROR")) {
            for (String field : List.of("DeviceName", "Manufacturer", "Model", "Firmware", "Channel")) {
                String prefix = "<Result>" + result + "</Result>";
                assertInvalid(response("DeviceInfo", prefix + "<" + field + ">1</" + field
                        + "><" + field + ">2</" + field + ">"));
                assertInvalid(response("DeviceInfo", prefix + "<" + field + "><X>1</X></" + field + ">"));
                assertInvalid(response("DeviceInfo", prefix + "<" + field + ">"
                        + "x".repeat(257) + "</" + field + ">"));
            }
            for (String field : List.of("Online", "Status", "Encode", "Record", "DeviceTime")) {
                assertInvalid(response("DeviceStatus", "<Result>" + result + "</Result><" + field
                        + ">1</" + field + "><" + field + ">1</" + field + ">"));
            }
        }
        for (String number : List.of("-1", "2147483648", "1.5", "\u0661", "+1")) {
            assertInvalid(response("DeviceInfo", "<Result>OK</Result><Channel>" + number + "</Channel>"));
        }
    }

    @Test
    void keepsXmlExternalEntitiesDisabledForNewResponses() {
        String xml = "<!DOCTYPE Response [<!ENTITY secret SYSTEM 'file:///definitely-not-present/secret'>]>"
                + response("DeviceInfo", "<Result>OK</Result><DeviceName>&secret;</DeviceName>");
        assertInvalid(xml);
    }

    @Test
    void generatesWhitelistedQueriesAndRejectsInjection() {
        for (String command : List.of("Catalog", "DeviceInfo", "DeviceStatus")) {
            String xml = new String(GbXml.query(command, DEVICE_ID, 42), StandardCharsets.UTF_8);
            assertTrue(xml.contains("<Query>"));
            assertTrue(xml.contains("<CmdType>" + command + "</CmdType>"));
            assertTrue(xml.contains("<DeviceID>" + DEVICE_ID + "</DeviceID>"));
            assertTrue(xml.contains("<SN>42</SN>"));
        }
        assertArrayEquals(GbXml.catalogQuery(DEVICE_ID, 42), GbXml.query("Catalog", DEVICE_ID, 42));
        for (String command : new String[]{null, "DeviceControl", "<DeviceInfo>", "deviceinfo"}) {
            assertThrows(IllegalArgumentException.class, () -> GbXml.query(command, DEVICE_ID, 42));
        }
        assertThrows(IllegalArgumentException.class, () -> GbXml.query("DeviceInfo", "</DeviceID>", 1));
        assertThrows(IllegalArgumentException.class, () -> GbXml.query("DeviceInfo", DEVICE_ID, 0));
    }

    @Test
    void buildsPtzControlWithStrictHexPayload() {
        String xml = new String(GbXml.ptzControl(DEVICE_ID, 42, "a50f0100000000b5"), StandardCharsets.UTF_8);
        assertTrue(xml.contains("<Control>"));
        assertTrue(xml.contains("<CmdType>DeviceControl</CmdType>"));
        assertTrue(xml.contains("<DeviceID>" + DEVICE_ID + "</DeviceID>"));
        assertTrue(xml.contains("<SN>42</SN>"));
        assertTrue(xml.contains("<PTZCmd>A50F0100000000B5</PTZCmd>"));
        for (String hex : new String[]{null, "", "A50F0100000000B", "A50F0100000000B55",
                "G50F0100000000B5", "<PTZCmd>bad</PTZCmd>"}) {
            assertThrows(IllegalArgumentException.class, () -> GbXml.ptzControl(DEVICE_ID, 42, hex));
        }
        assertThrows(IllegalArgumentException.class, () -> GbXml.ptzControl("123", 42, "A50F0100000000B5"));
        assertThrows(IllegalArgumentException.class, () -> GbXml.ptzControl(DEVICE_ID, -1, "A50F0100000000B5"));
    }

    @Test
    void preservesLegacyMessageConstructor() {
        var message = new GbXml.Message("Notify", "Keepalive", 1, DEVICE_ID, "OK", 0, List.of());
        assertEquals("OK", message.status());
        assertNull(message.result());
        assertNull(message.deviceInfo());
        assertNull(message.deviceStatus());
    }

    private static void assertInvalid(String xml) {
        assertThrows(IllegalArgumentException.class, () -> parse(xml));
    }

    private static String response(String command, String fields) {
        return "<Response><CmdType>" + command + "</CmdType><SN>42</SN><DeviceID>"
                + DEVICE_ID + "</DeviceID>" + fields + "</Response>";
    }

    private static GbXml.Message parse(String xml) {
        return GbXml.parse(xml.getBytes(StandardCharsets.UTF_8), 8_192, 10);
    }
}
