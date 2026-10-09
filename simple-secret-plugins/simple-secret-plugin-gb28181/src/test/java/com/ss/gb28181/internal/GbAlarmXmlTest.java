package com.ss.gb28181.internal;

import com.ss.gb28181.GbAlarm;
import com.ss.gb28181.GbAlarmEvent;
import com.ss.gb28181.GbAlarmListener;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GbAlarmXmlTest {

    private static final int MAX_MESSAGE_BYTES = 8_192;
    private static final int MAX_CATALOG_ITEMS = 10;
    private static final String DEVICE = "34020000002000000001";

    @Test
    void parsesCompleteAlarmAndPreservesReportedValues() {
        GbXml.Message message = parse("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Notify>
                  <CmdType>Alarm</CmdType>
                  <SN>42</SN>
                  <DeviceID>34020000001320000001</DeviceID>
                  <AlarmPriority>4</AlarmPriority>
                  <AlarmMethod>7</AlarmMethod>
                  <AlarmTime>2026-09-15T08:09:10+08:00</AlarmTime>
                  <AlarmDescription>东门越界 &amp; 人员聚集</AlarmDescription>
                  <Longitude>-180.0</Longitude>
                  <Latitude>90.0</Latitude>
                  <Info>
                    <AlarmType>12</AlarmType>
                    <AlarmTypeParam><EventType>2</EventType></AlarmTypeParam>
                  </Info>
                </Notify>
                """);

        assertEquals("Notify", message.kind());
        assertEquals("Alarm", message.command());
        assertEquals(new GbAlarm("34020000001320000001", 42, 4, 7,
                "2026-09-15T08:09:10+08:00", "东门越界 & 人员聚集", -180.0, 90.0, 12, 2),
                message.alarm());
    }

    @Test
    void parsesMinimalAlarmAndTenDigitAlarmCenter() {
        GbAlarm alarm = parse("""
                <Notify><CmdType>Alarm</CmdType><SN>1</SN><DeviceID>3402000000</DeviceID>
                <AlarmPriority>1</AlarmPriority><AlarmMethod>1</AlarmMethod>
                <AlarmTime>2026-09-15T08:09:10</AlarmTime></Notify>
                """).alarm();

        assertEquals("3402000000", alarm.deviceId());
        assertNull(alarm.description());
        assertNull(alarm.longitude());
        assertNull(alarm.latitude());
        assertNull(alarm.type());
        assertNull(alarm.eventType());
    }

    @Test
    void parsesEmptyAlarmTypeParamAndOneOptionalCoordinate() {
        GbAlarm alarm = parse(withOptional("<Longitude>12.5</Longitude><Info><AlarmType>3</AlarmType>"
                + "<AlarmTypeParam/></Info>")).alarm();

        assertEquals(12.5, alarm.longitude());
        assertNull(alarm.latitude());
        assertEquals(3, alarm.type());
        assertNull(alarm.eventType());
    }

    @Test
    void honorsGb2312AlarmEncoding() {
        String xml = "<?xml version=\"1.0\" encoding=\"GB2312\"?>"
                + "<Notify><CmdType>Alarm</CmdType><SN>7</SN><DeviceID>" + DEVICE + "</DeviceID>"
                + "<AlarmPriority>2</AlarmPriority><AlarmMethod>3</AlarmMethod>"
                + "<AlarmTime>2026-09-15T08:09:10</AlarmTime><AlarmDescription>入侵报警</AlarmDescription>"
                + "</Notify>";

        GbXml.Message message = GbXml.parse(xml.getBytes(Charset.forName("GB2312")),
                MAX_MESSAGE_BYTES, MAX_CATALOG_ITEMS);

        assertEquals("入侵报警", message.alarm().description());
    }

    @Test
    void exposesAlarmEventAndFunctionalListenerContract() {
        GbAlarm alarm = parse(minimalAlarm(DEVICE)).alarm();
        Instant receivedAt = Instant.parse("2026-09-15T00:09:10Z");
        GbAlarmEvent event = new GbAlarmEvent("34020000002000000002", receivedAt, alarm);
        AtomicReference<GbAlarmEvent> received = new AtomicReference<>();
        GbAlarmListener listener = received::set;

        listener.onAlarm(event);

        assertEquals("34020000002000000002", event.sourceDeviceId());
        assertEquals(receivedAt, event.receivedAt());
        assertEquals(alarm, event.alarm());
        assertEquals(event, received.get());
    }

    @Test
    void createsUtf8AlarmResponseWithOkResult() {
        byte[] response = GbXml.alarmResponse("3402000000", 42);
        String xml = new String(response, StandardCharsets.UTF_8);

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<Response>\r\n<CmdType>Alarm</CmdType>\r\n<SN>42</SN>\r\n"
                + "<DeviceID>3402000000</DeviceID>\r\n<Result>OK</Result>\r\n</Response>", xml);
        assertArrayEquals(response, xml.getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> GbXml.alarmResponse("123", 1));
        assertThrows(IllegalArgumentException.class, () -> GbXml.alarmResponse(DEVICE, 0));
    }

    @Test
    void rejectsMissingDuplicateNestedAndUnexpectedAlarmStructure() {
        assertInvalid(minimalAlarm(DEVICE).replace("<AlarmPriority>1</AlarmPriority>", ""));
        assertInvalid(minimalAlarm(DEVICE).replace("<AlarmMethod>1</AlarmMethod>",
                "<AlarmMethod>1</AlarmMethod><AlarmMethod>2</AlarmMethod>"));
        assertInvalid(minimalAlarm(DEVICE).replace("<AlarmTime>2026-09-15T08:09:10</AlarmTime>",
                "<AlarmTime><Value>2026-09-15T08:09:10</Value></AlarmTime>"));
        assertInvalid(minimalAlarm(DEVICE).replace("</Notify>", "<Info/><Info/></Notify>"));
    }

    @Test
    void rejectsInvalidRequiredAlarmValues() {
        for (String priority : new String[]{"0", "5", "-1", "1.0", "\u0661"}) {
            assertInvalid(minimalAlarm(DEVICE).replace("<AlarmPriority>1</AlarmPriority>",
                    "<AlarmPriority>" + priority + "</AlarmPriority>"));
        }
        for (String method : new String[]{"0", "8", "-1", "1,2"}) {
            assertInvalid(minimalAlarm(DEVICE).replace("<AlarmMethod>1</AlarmMethod>",
                    "<AlarmMethod>" + method + "</AlarmMethod>"));
        }
        assertInvalid(minimalAlarm(DEVICE).replace("<SN>1</SN>", "<SN>0</SN>"));
        assertInvalid(minimalAlarm(DEVICE).replace(DEVICE, "340200000X"));
        assertInvalid(minimalAlarm(DEVICE).replace("2026-09-15T08:09:10", "2026-02-30T08:09:10"));
        assertInvalid(minimalAlarm(DEVICE).replace("2026-09-15T08:09:10", "x".repeat(65)));
    }

    @Test
    void rejectsInvalidOptionalAlarmValuesAndBounds() {
        assertInvalid(withOptional("<AlarmDescription>" + "x".repeat(1025) + "</AlarmDescription>"));
        for (String coordinates : new String[]{
                "<Longitude>180.0001</Longitude>", "<Longitude>NaN</Longitude>",
                "<Longitude>INF</Longitude>", "<Latitude>-90.0001</Latitude>",
                "<Latitude>-INF</Latitude>"}) {
            assertInvalid(withOptional(coordinates));
        }
        for (String info : new String[]{
                "<Info><AlarmType>0</AlarmType></Info>",
                "<Info><AlarmType>-1</AlarmType></Info>",
                "<Info><AlarmType>1.0</AlarmType></Info>",
                "<Info><AlarmTypeParam><EventType>1</EventType></AlarmTypeParam></Info>",
                "<Info><AlarmType>1</AlarmType><AlarmTypeParam><EventType>0</EventType></AlarmTypeParam></Info>",
                "<Info><AlarmType>1</AlarmType><AlarmTypeParam><EventType>3</EventType></AlarmTypeParam></Info>"}) {
            assertInvalid(withOptional(info));
        }
    }

    @Test
    void rejectsDuplicateOptionalNodesAndNestedInfoFields() {
        assertInvalid(withOptional("<Longitude>1</Longitude><Longitude>2</Longitude>"));
        assertInvalid(withOptional("<Info><AlarmType>1</AlarmType><AlarmType>2</AlarmType></Info>"));
        assertInvalid(withOptional("<Info><AlarmType>1</AlarmType><AlarmTypeParam>"
                + "<EventType>1</EventType><EventType>2</EventType></AlarmTypeParam></Info>"));
        assertInvalid(withOptional("<Info><AlarmType><Value>1</Value></AlarmType></Info>"));
    }

    @Test
    void rejectsAlarmXxeOversizedBodyAndUnsupportedEncoding() {
        String secret = "UNIQUE-ALARM-SENSITIVE-MARKER";
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE Notify [<!ENTITY xxe SYSTEM \"file:///"
                + secret + "\">]><Notify><CmdType>Alarm</CmdType><SN>1</SN><DeviceID>" + DEVICE
                + "</DeviceID><AlarmPriority>1</AlarmPriority><AlarmMethod>1</AlarmMethod>"
                + "<AlarmTime>&xxe;</AlarmTime></Notify>";
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> GbXml.parse(xxe.getBytes(StandardCharsets.UTF_8), MAX_MESSAGE_BYTES, MAX_CATALOG_ITEMS));
        assertTrue(exception.getMessage() == null || !exception.getMessage().contains(secret));

        byte[] alarm = minimalAlarm(DEVICE).getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> GbXml.parse(alarm, alarm.length - 1, MAX_CATALOG_ITEMS));
        assertInvalid("<?xml version=\"1.0\" encoding=\"NO-SUCH-ENCODING\"?>" + minimalAlarm(DEVICE));
    }

    private static String minimalAlarm(String deviceId) {
        return "<Notify><CmdType>Alarm</CmdType><SN>1</SN><DeviceID>" + deviceId + "</DeviceID>"
                + "<AlarmPriority>1</AlarmPriority><AlarmMethod>1</AlarmMethod>"
                + "<AlarmTime>2026-09-15T08:09:10</AlarmTime></Notify>";
    }

    private static String withOptional(String optional) {
        return minimalAlarm(DEVICE).replace("</Notify>", optional + "</Notify>");
    }

    private static GbXml.Message parse(String xml) {
        return GbXml.parse(xml.getBytes(StandardCharsets.UTF_8), MAX_MESSAGE_BYTES, MAX_CATALOG_ITEMS);
    }

    private static void assertInvalid(String xml) {
        assertThrows(IllegalArgumentException.class, () -> parse(xml));
    }
}
