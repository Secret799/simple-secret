package com.ss.gb28181.internal;

import com.ss.gb28181.CatalogItem;
import com.ss.gb28181.GbCatalogNotification;
import com.ss.gb28181.GbCatalogSubscriptionRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GbCatalogSubscriptionXmlTest {
    private static final String OWNER = "34020000002000000001";
    private static final int MAX_MESSAGE_BYTES = 16_384;

    @Test
    void encodesCatalogSubscribeWithIndependentOptionalTimes() {
        var both = new GbCatalogSubscriptionRequest(Duration.ofMinutes(10),
                LocalDateTime.of(2026, 9, 15, 10, 0, 1),
                LocalDateTime.of(2026, 9, 15, 11, 2, 3));
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                        + "<Query><CmdType>Catalog</CmdType><SN>42</SN><DeviceID>" + OWNER + "</DeviceID>"
                        + "<StartTime>2026-09-15T10:00:01</StartTime>"
                        + "<EndTime>2026-09-15T11:02:03</EndTime></Query>",
                text(GbXml.catalogSubscribe(OWNER, 42, both)));
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                        + "<Query><CmdType>Catalog</CmdType><SN>1</SN><DeviceID>" + OWNER + "</DeviceID>"
                        + "<StartTime>2026-09-15T10:00:01</StartTime></Query>",
                text(GbXml.catalogSubscribe(OWNER, 1,
                        new GbCatalogSubscriptionRequest(Duration.ofSeconds(1), both.startTime(), null))));
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                        + "<Query><CmdType>Catalog</CmdType><SN>2</SN><DeviceID>" + OWNER + "</DeviceID></Query>",
                text(GbXml.catalogSubscribe(OWNER, 2, GbCatalogSubscriptionRequest.all(Duration.ofSeconds(1)))));
    }

    @Test
    void rejectsInvalidSubscribeArguments() {
        var request = GbCatalogSubscriptionRequest.all(Duration.ofMinutes(1));
        assertThrows(IllegalArgumentException.class, () -> GbXml.catalogSubscribe("123", 1, request));
        assertThrows(IllegalArgumentException.class, () -> GbXml.catalogSubscribe(OWNER, 0, request));
        assertThrows(NullPointerException.class, () -> GbXml.catalogSubscribe(OWNER, 1, null));
    }

    @Test
    void parsesDirectoryInformationWithoutEventAndPreservesProjection() {
        var notification = parse("<Notify><CmdType>Catalog</CmdType><SN>7</SN><DeviceID>" + OWNER
                + "</DeviceID><SumNum>3</SumNum><DeviceList Num=\"2\">"
                + "<Item><DeviceID>11</DeviceID><Name>Gate</Name><ParentID>3402000000</ParentID><Status>ON</Status>"
                + "<Manufacturer>ignored</Manufacturer></Item>"
                + "<Item><DeviceID>12</DeviceID></Item></DeviceList></Notify>");

        assertEquals(new GbCatalogNotification(OWNER, 7, 3, List.of(
                new GbCatalogNotification.Entry(new CatalogItem("11", "Gate", "3402000000", "ON"), null),
                new GbCatalogNotification.Entry(new CatalogItem("12", null, null, null), null))), notification);
    }

    @Test
    void parsesEveryCatalogEventAndStatusOnlyOptionalFields() {
        StringBuilder items = new StringBuilder();
        for (String type : List.of("ON", "OFF", "VLOST", "DEFECT", "ADD", "DEL", "UPDATE")) {
            items.append("<Item><DeviceID>3402000000132000000")
                    .append(items.length() % 10).append("</DeviceID>");
            if (type.equals("ADD") || type.equals("UPDATE")) {
                items.append("<Name>Camera</Name><Status>ON</Status>");
            } else if (type.equals("ON")) {
                items.append("<Name>Optional</Name><ParentID>3402000000</ParentID><Status>OFF</Status>");
            }
            items.append("<Event>").append(type).append("</Event><CivilCode>ignored</CivilCode></Item>");
        }
        var notification = parse(notification(7, "7", items.toString()));

        assertEquals(List.of(GbCatalogNotification.Type.ON, GbCatalogNotification.Type.OFF,
                        GbCatalogNotification.Type.VLOST, GbCatalogNotification.Type.DEFECT,
                        GbCatalogNotification.Type.ADD, GbCatalogNotification.Type.DEL,
                        GbCatalogNotification.Type.UPDATE),
                notification.entries().stream().map(GbCatalogNotification.Entry::type).toList());
        assertEquals("Optional", notification.entries().get(0).item().name());
        assertThrows(UnsupportedOperationException.class, () -> notification.entries().clear());
    }

    @Test
    void acceptsEmptyNotificationAndAbsentDeviceListOnlyWhenTotalIsZero() {
        assertEquals(List.of(), parse("<Notify><CmdType>Catalog</CmdType><SN>1</SN><DeviceID>" + OWNER
                + "</DeviceID><SumNum>0</SumNum></Notify>").entries());
        assertEquals(List.of(), parse(notification(0, "0", "")).entries());
        assertInvalid("<Notify><CmdType>Catalog</CmdType><SN>1</SN><DeviceID>" + OWNER
                + "</DeviceID><SumNum>1</SumNum></Notify>");
    }

    @Test
    void rejectsInvalidTotalsCountsAndLimits() {
        assertInvalid(notification(1, "0", item("11", null, null)));
        assertInvalid(notification(0, "1", item("11", null, null)));
        assertInvalid(notification(1, "2", item("11", null, null)));
        assertInvalid(notification(2, "1", item("11", "ADD", "<Name>A</Name><Status>ON</Status>")));
        String xml = notification(2, "2", item("11", null, null) + item("12", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> GbXml.parse(xml.getBytes(StandardCharsets.UTF_8), MAX_MESSAGE_BYTES, 1));
    }

    @Test
    void rejectsMixedMissingUnknownBlankDuplicateAndNestedEvents() {
        assertInvalid(notification(2, "2", item("11", "ON", null) + item("12", null, null)));
        assertInvalid(notification(1, "1", item("11", "UNKNOWN", null)));
        assertInvalid(notification(1, "1", item("11", " ", null)));
        assertInvalid(notification(1, "1", "<Item><DeviceID>11</DeviceID><Event>ON</Event><Event>OFF</Event></Item>"));
        assertInvalid(notification(1, "1", "<Item><DeviceID>11</DeviceID><Wrapper><Event>ON</Event></Wrapper></Item>"));
    }

    @Test
    void rejectsMissingRequiredAddUpdateFieldsAndInvalidCatalogProjection() {
        assertInvalid(notification(1, "1", item("11", "ADD", "<Status>ON</Status>")));
        assertInvalid(notification(1, "1", item("11", "UPDATE", "<Name>A</Name>")));
        assertInvalid(notification(1, "1", item("1", "DEL", null)));
        assertInvalid(notification(1, "1", item("11", "ADD", "<Name>A</Name><Status>UNKNOWN</Status>")));
        assertInvalid(notification(1, "1", item("11", "ON", "<Name>A</Name><Name>B</Name>")));
    }

    @Test
    void catalogNotificationComponentDefaultsAcrossLegacyMessageConstructors() {
        assertEquals(null, new GbXml.Message("Notify", "Keepalive", 1, OWNER, "OK", 0, List.of())
                .catalogNotification());
        assertEquals(null, new GbXml.Message("Response", "DeviceInfo", 1, OWNER,
                null, 0, List.of(), "OK", null, null).catalogNotification());
        assertEquals(null, new GbXml.Message("Response", "RecordInfo", 1, OWNER,
                null, 0, List.of(), null, null, null, List.of()).catalogNotification());
        assertEquals(null, new GbXml.Message("Notify", "Alarm", 1, OWNER,
                null, 0, List.of(), null, null, null, List.of(), null).catalogNotification());
        assertEquals(null, new GbXml.Message("Response", "PresetQuery", 1, OWNER,
                null, 0, List.of(), null, null, null, List.of(), null, List.of()).catalogNotification());
    }

    private static GbCatalogNotification parse(String xml) {
        return GbXml.parse(xml.getBytes(StandardCharsets.UTF_8), MAX_MESSAGE_BYTES, 10).catalogNotification();
    }

    private static String notification(int total, String count, String items) {
        return "<Notify><CmdType>Catalog</CmdType><SN>1</SN><DeviceID>" + OWNER
                + "</DeviceID><SumNum>" + total + "</SumNum><DeviceList Num=\"" + count + "\">"
                + items + "</DeviceList></Notify>";
    }

    private static String item(String id, String event, String fields) {
        return "<Item><DeviceID>" + id + "</DeviceID>" + (fields == null ? "" : fields)
                + (event == null ? "" : "<Event>" + event + "</Event>") + "</Item>";
    }

    private static String text(byte[] xml) {
        return new String(xml, StandardCharsets.UTF_8);
    }

    private static void assertInvalid(String xml) {
        assertThrows(IllegalArgumentException.class,
                () -> GbXml.parse(xml.getBytes(StandardCharsets.UTF_8), MAX_MESSAGE_BYTES, 10));
    }
}
