package com.ss.gb28181.internal;

import com.ss.gb28181.GbMobilePosition;
import com.ss.gb28181.GbMobilePositionNotification;
import com.ss.gb28181.GbMobilePositionSubscriptionRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GbMobilePositionXmlTest {
    private static final String OWNER = "34020000002000000001";
    private static final String CHANNEL = "34020000001320000001";
    private static final int MAX_BYTES = 16_384;

    @Test
    void encodesSubscriptionQueryWithWholeSecondInterval() {
        var request = new GbMobilePositionSubscriptionRequest(Duration.ofMinutes(10), Duration.ofSeconds(7));
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                        + "<Query><CmdType>MobilePosition</CmdType><SN>42</SN><DeviceID>" + OWNER
                        + "</DeviceID><Interval>7</Interval></Query>",
                text(GbXml.mobilePositionSubscription(OWNER, 42, request)));
        assertThrows(IllegalArgumentException.class,
                () -> GbXml.mobilePositionSubscription("123", 42, request));
        assertThrows(IllegalArgumentException.class,
                () -> GbXml.mobilePositionSubscription(OWNER, 0, request));
        assertThrows(NullPointerException.class,
                () -> GbXml.mobilePositionSubscription(OWNER, 42, null));
    }

    @Test
    void parsesStandardBatchAndPreservesOptionalValuesTextOrderAndDuplicates() {
        String item = item(CHANNEL, "2026-09-15T10:20:30+08:00",
                "<Longitude>121.500</Longitude><Latitude>31.200</Latitude>"
                        + "<Speed>12.5</Speed><Direction>359.9</Direction>"
                        + "<Altitude>-8.25</Altitude><Height>2e1</Height>");
        var notification = parse(notification(2, "2", item + item));

        var expected = new GbMobilePosition(CHANNEL, "2026-09-15T10:20:30+08:00",
                121.5, 31.2, 12.5, 359.9, -8.25, 20.0);
        assertEquals(new GbMobilePositionNotification(OWNER, 7,
                "2026-09-15T10:20:31+08:00", 2, List.of(expected, expected)), notification);
    }

    @Test
    void parsesMissingOptionalValuesAndEmptyBatch() {
        var position = parse(notification(1, "1", item(CHANNEL, "2026-09-15T02:20:30Z",
                "<Longitude>-180</Longitude><Latitude>90</Latitude>"))).positions().get(0);
        assertNull(position.speed());
        assertNull(position.direction());
        assertNull(position.altitude());
        assertNull(position.height());

        assertEquals(List.of(), parse("<Notify><CmdType>MobilePosition</CmdType><SN>7</SN><DeviceID>"
                + OWNER + "</DeviceID><Time>2026-09-15T10:20:31</Time><SumNum>0</SumNum></Notify>").positions());
        assertEquals(List.of(), parse(notification(0, "0", "")).positions());
    }

    @Test
    void rejectsMissingDuplicateNestedAndFlatLegacyFields() {
        assertInvalid(notification(1, "1", "<Item><CaptureTime>2026-09-15T10:20:30Z</CaptureTime>"
                + "<Longitude>1</Longitude><Latitude>2</Latitude></Item>"));
        assertInvalid(notification(1, "1", item(CHANNEL, "2026-09-15T10:20:30Z",
                "<Longitude>1</Longitude><Longitude>2</Longitude><Latitude>2</Latitude>")));
        assertInvalid(notification(1, "1", "<Item><DeviceID>" + CHANNEL + "</DeviceID><Wrapper>"
                + "<CaptureTime>2026-09-15T10:20:30Z</CaptureTime></Wrapper>"
                + "<Longitude>1</Longitude><Latitude>2</Latitude></Item>"));
        assertInvalid("<Notify><CmdType>MobilePosition</CmdType><SN>7</SN><DeviceID>" + OWNER
                + "</DeviceID><Time>2026-09-15T10:20:31Z</Time><SumNum>1</SumNum>"
                + "<CaptureTime>2026-09-15T10:20:30Z</CaptureTime>"
                + "<Longitude>1</Longitude><Latitude>2</Latitude></Notify>");
        assertInvalid("<Notify><CmdType>MobilePosition</CmdType><SN>7</SN><DeviceID>" + OWNER
                + "</DeviceID><Wrapper><Time>2026-09-15T10:20:31Z</Time></Wrapper>"
                + "<SumNum>0</SumNum></Notify>");
    }

    @Test
    void rejectsFlatPositionFieldsWhenDeclaredBatchIsEmpty() {
        assertInvalid("<Notify><CmdType>MobilePosition</CmdType><SN>7</SN><DeviceID>" + OWNER
                + "</DeviceID><Time>2026-09-15T10:20:31Z</Time><SumNum>0</SumNum>"
                + "<CaptureTime>2026-09-15T10:20:30Z</CaptureTime>"
                + "<Longitude>1</Longitude><Latitude>2</Latitude></Notify>");
    }

    @Test
    void rejectsNestedItemsWhenDeclaredListIsEmpty() {
        assertInvalid(notificationWithTime("2026-09-15T10:20:31Z", 0,
                "<DeviceList Num=\"0\"><Wrapper>" + item(CHANNEL, "2026-09-15T10:20:30Z",
                        "<Longitude>1</Longitude><Latitude>2</Latitude>") + "</Wrapper></DeviceList>"));
    }

    @Test
    void rejectsPositionFieldsOutsideDirectItemsWithinDeviceList() {
        for (String list : List.of(
                "<DeviceList Num=\"0\"><Longitude>1</Longitude><Latitude>2</Latitude></DeviceList>",
                "<DeviceList Num=\"0\"><Wrapper><Longitude>1</Longitude><Latitude>2</Latitude>"
                        + "</Wrapper></DeviceList>",
                "<DeviceList Num=\"1\">" + item(CHANNEL, "2026-09-15T10:20:30Z",
                        "<Longitude>1</Longitude><Latitude>2</Latitude>")
                        + "<Longitude>3</Longitude></DeviceList>",
                "<DeviceList Num=\"1\">" + item(CHANNEL, "2026-09-15T10:20:30Z",
                        "<Longitude>1</Longitude><Latitude>2</Latitude>")
                        + "<Extension><Latitude>4</Latitude></Extension></DeviceList>")) {
            assertInvalid(notificationWithTime("2026-09-15T10:20:31Z", list.contains("Num=\"1\"") ? 1 : 0,
                    list));
        }
    }

    @Test
    void preservesDeviceListExtensionsWithoutPositionFields() {
        String position = item(CHANNEL, "2026-09-15T10:20:30Z",
                "<Longitude>1</Longitude><Latitude>2</Latitude>");
        var notification = parse(notificationWithTime("2026-09-15T10:20:31Z", 1,
                "<DeviceList Num=\"1\"><Extension><VendorField>ok</VendorField></Extension>"
                        + position + "</DeviceList>"));

        assertEquals(1, notification.positions().size());
    }

    @Test
    void rejectsEmptyOrWhitespaceOptionalPositionNumbers() {
        for (String name : List.of("Speed", "Direction", "Altitude", "Height")) {
            for (String value : List.of("", "   ")) {
                assertInvalid(notification(1, "1", item(CHANNEL, "2026-09-15T10:20:30Z",
                        "<Longitude>1</Longitude><Latitude>2</Latitude><" + name + ">" + value
                                + "</" + name + ">")));
            }
        }
    }

    @Test
    void rejectsInvalidDatesIdsAndNumericTokens() {
        for (String time : List.of("2026-02-30T10:20:30Z", "2026-09-15", "2026-09-15T10:20:30Zx")) {
            assertInvalid(notificationWithTime(time, 0, null));
            assertInvalid(notification(1, "1", item(CHANNEL, time,
                    "<Longitude>1</Longitude><Latitude>2</Latitude>")));
        }
        assertInvalid(notification(1, "1", item("123", "2026-09-15T10:20:30Z",
                "<Longitude>1</Longitude><Latitude>2</Latitude>")));
        for (String token : List.of("NaN", "Infinity", "0x1", "1_0", "--1", "1 2", "1e", ".")) {
            assertInvalid(notification(1, "1", item(CHANNEL, "2026-09-15T10:20:30Z",
                    "<Longitude>" + token + "</Longitude><Latitude>2</Latitude>")));
        }
        String oversized = "1".repeat(65);
        assertInvalid(notification(1, "1", item(CHANNEL, "2026-09-15T10:20:30Z",
                "<Longitude>" + oversized + "</Longitude><Latitude>2</Latitude>")));
    }

    @Test
    void rejectsOutOfRangeOrNonFinitePositionValues() {
        for (String fields : List.of(
                "<Longitude>-180.1</Longitude><Latitude>0</Latitude>",
                "<Longitude>180.1</Longitude><Latitude>0</Latitude>",
                "<Longitude>0</Longitude><Latitude>-90.1</Latitude>",
                "<Longitude>0</Longitude><Latitude>90.1</Latitude>",
                "<Longitude>0</Longitude><Latitude>0</Latitude><Speed>-0.1</Speed>",
                "<Longitude>0</Longitude><Latitude>0</Latitude><Direction>-0.1</Direction>",
                "<Longitude>0</Longitude><Latitude>0</Latitude><Direction>360</Direction>",
                "<Longitude>1e309</Longitude><Latitude>0</Latitude>",
                "<Longitude>0</Longitude><Latitude>0</Latitude><Altitude>1e309</Altitude>")) {
            assertInvalid(notification(1, "1", item(CHANNEL, "2026-09-15T10:20:30Z", fields)));
        }
    }

    @Test
    void rejectsMismatchedTotalsCountsAndConfiguredLimit() {
        String item = item(CHANNEL, "2026-09-15T10:20:30Z",
                "<Longitude>1</Longitude><Latitude>2</Latitude>");
        assertInvalid(notification(1, "0", item));
        assertInvalid(notification(0, "1", item));
        var partial = parse(notification(2, "1", item));
        assertEquals(2, partial.total());
        assertEquals(1, partial.positions().size());
        assertInvalid(notificationWithTime("2026-09-15T10:20:31Z", 1, null));
        assertThrows(IllegalArgumentException.class,
                () -> GbXml.parse(notification(2, "2", item + item).getBytes(StandardCharsets.UTF_8),
                        MAX_BYTES, 10, 10, 1));
    }

    @Test
    void legacyMessageConstructorsDefaultMobilePositionNotification() {
        assertNull(new GbXml.Message("Notify", "Keepalive", 1, OWNER, "OK", 0, List.of())
                .mobilePositionNotification());
        assertNull(new GbXml.Message("Response", "DeviceInfo", 1, OWNER,
                null, 0, List.of(), "OK", null, null).mobilePositionNotification());
        assertNull(new GbXml.Message("Response", "RecordInfo", 1, OWNER,
                null, 0, List.of(), null, null, null, List.of()).mobilePositionNotification());
        assertNull(new GbXml.Message("Notify", "Alarm", 1, OWNER,
                null, 0, List.of(), null, null, null, List.of(), null).mobilePositionNotification());
        assertNull(new GbXml.Message("Response", "PresetQuery", 1, OWNER,
                null, 0, List.of(), null, null, null, List.of(), null, List.of()).mobilePositionNotification());
        assertNull(new GbXml.Message("Notify", "Catalog", 1, OWNER,
                null, 0, List.of(), null, null, null, List.of(), null, List.of(), null)
                .mobilePositionNotification());
    }

    private static GbMobilePositionNotification parse(String xml) {
        return GbXml.parse(xml.getBytes(StandardCharsets.UTF_8), MAX_BYTES, 10, 10, 10)
                .mobilePositionNotification();
    }

    private static String notification(int total, String count, String items) {
        return notificationWithTime("2026-09-15T10:20:31+08:00", total,
                "<DeviceList Num=\"" + count + "\">" + items + "</DeviceList>");
    }

    private static String notificationWithTime(String time, int total, String list) {
        return "<Notify><CmdType>MobilePosition</CmdType><SN>7</SN><DeviceID>" + OWNER
                + "</DeviceID><Time>" + time + "</Time><SumNum>" + total + "</SumNum>"
                + (list == null ? "" : list) + "</Notify>";
    }

    private static String item(String deviceId, String captureTime, String fields) {
        return "<Item><DeviceID>" + deviceId + "</DeviceID><CaptureTime>" + captureTime
                + "</CaptureTime>" + fields + "</Item>";
    }

    private static String text(byte[] xml) {
        return new String(xml, StandardCharsets.UTF_8);
    }

    private static void assertInvalid(String xml) {
        assertThrows(IllegalArgumentException.class,
                () -> GbXml.parse(xml.getBytes(StandardCharsets.UTF_8), MAX_BYTES, 10, 10, 10));
    }
}
