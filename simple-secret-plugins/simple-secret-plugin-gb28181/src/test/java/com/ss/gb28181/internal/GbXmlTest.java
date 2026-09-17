package com.ss.gb28181.internal;

import com.ss.gb28181.CatalogItem;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GbXmlTest {

    private static final int MAX_MESSAGE_BYTES = 8_192;
    private static final int MAX_CATALOG_ITEMS = 10;

    @Test
    void parsesKeepaliveNotify() {
        GbXml.Message message = parse("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Notify>
                  <CmdType>Keepalive</CmdType>
                  <SN>42</SN>
                  <DeviceID>34020000002000000001</DeviceID>
                  <Status>OK</Status>
                </Notify>
                """);

        assertEquals("Notify", message.kind());
        assertEquals("Keepalive", message.command());
        assertEquals(42, message.sn());
        assertEquals("34020000002000000001", message.deviceId());
        assertEquals("OK", message.status());
        assertEquals(0, message.total());
        assertEquals(List.of(), message.items());
    }

    @Test
    void parsesCatalogResponsePageWithChineseText() {
        GbXml.Message message = parse("""
                <?xml version="1.0" encoding="UTF-8"?>
                <Response>
                  <CmdType>Catalog</CmdType>
                  <SN>7</SN>
                  <DeviceID>34020000002000000001</DeviceID>
                  <SumNum>4</SumNum>
                  <DeviceList Num="2">
                    <Item>
                      <DeviceID>34020000001320000001</DeviceID>
                      <Name>一号大门</Name>
                      <ParentID>34020000002000000001</ParentID>
                      <Status>ON</Status>
                    </Item>
                    <Item>
                      <DeviceID>34020000001320000002</DeviceID>
                      <Name>二号大门</Name>
                      <ParentID>34020000002000000001</ParentID>
                      <Status>OFF</Status>
                    </Item>
                  </DeviceList>
                </Response>
                """);

        assertEquals("Response", message.kind());
        assertEquals("Catalog", message.command());
        assertEquals(4, message.total());
        assertEquals(List.of(
                new CatalogItem("34020000001320000001", "一号大门", "34020000002000000001", "ON"),
                new CatalogItem("34020000001320000002", "二号大门", "34020000002000000001", "OFF")
        ), message.items());
        assertThrows(UnsupportedOperationException.class,
                () -> message.items().add(new CatalogItem("11", "x", null, null)));
    }

    @Test
    void parsesEmptyCatalogPage() {
        GbXml.Message message = parse("""
                <Response>
                  <CmdType>Catalog</CmdType>
                  <SN>8</SN>
                  <DeviceID>34020000002000000001</DeviceID>
                  <SumNum>0</SumNum>
                  <DeviceList Num="0"/>
                </Response>
                """);

        assertEquals(0, message.total());
        assertEquals(List.of(), message.items());
    }

    @Test
    void honorsGb2312XmlDeclaration() {
        String xml = """
                <?xml version="1.0" encoding="GB2312"?>
                <Response><CmdType>Catalog</CmdType><SN>9</SN>
                <DeviceID>34020000002000000001</DeviceID><SumNum>1</SumNum>
                <DeviceList Num="1"><Item><DeviceID>11000000000000000001</DeviceID>
                <Name>中文通道</Name><Status>ON</Status></Item></DeviceList></Response>
                """;

        GbXml.Message message = GbXml.parse(xml.getBytes(Charset.forName("GB2312")),
                MAX_MESSAGE_BYTES, MAX_CATALOG_ITEMS);

        assertEquals("中文通道", message.items().get(0).name());
    }

    @Test
    void createsUtf8CatalogQuery() {
        byte[] query = GbXml.catalogQuery("34020000002000000001", 19);
        String xml = new String(query, StandardCharsets.UTF_8);

        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(xml.contains("<Query>"));
        assertTrue(xml.contains("<CmdType>Catalog</CmdType>"));
        assertTrue(xml.contains("<SN>19</SN>"));
        assertTrue(xml.contains("<DeviceID>34020000002000000001</DeviceID>"));
        assertArrayEquals(query, xml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsUnsupportedCommandsAndRootKinds() {
        assertInvalid("<Notify><CmdType>Alarm</CmdType><SN>1</SN>"
                + "<DeviceID>34020000002000000001</DeviceID></Notify>");
        assertInvalid("<Query><CmdType>Catalog</CmdType><SN>1</SN>"
                + "<DeviceID>34020000002000000001</DeviceID></Query>");
    }

    @Test
    void rejectsMissingDuplicateAndNestedRequiredFields() {
        assertInvalid("<Notify><CmdType>Keepalive</CmdType><SN>1</SN>"
                + "<Status>OK</Status></Notify>");
        assertInvalid("<Notify><CmdType>Keepalive</CmdType><CmdType>Keepalive</CmdType><SN>1</SN>"
                + "<DeviceID>34020000002000000001</DeviceID><Status>OK</Status></Notify>");
        assertInvalid("<Notify><CmdType>Keepalive</CmdType><SN>1</SN><Wrapper>"
                + "<DeviceID>34020000002000000001</DeviceID></Wrapper><Status>OK</Status></Notify>");
    }

    @Test
    void rejectsInvalidKeepaliveValues() {
        assertInvalid("<Notify><CmdType>Keepalive</CmdType><SN>0</SN>"
                + "<DeviceID>34020000002000000001</DeviceID><Status>OK</Status></Notify>");
        assertInvalid("<Notify><CmdType>Keepalive</CmdType><SN>1</SN>"
                + "<DeviceID>3402000000200000000X</DeviceID><Status>OK</Status></Notify>");
        assertInvalid("<Notify><CmdType>Keepalive</CmdType><SN>1</SN>"
                + "<DeviceID>34020000002000000001</DeviceID><Status>ON</Status></Notify>");
        assertInvalid("<Notify><CmdType>Keepalive</CmdType><SN>\u0661</SN>"
                + "<DeviceID>34020000002000000001</DeviceID><Status>OK</Status></Notify>");
    }

    @Test
    void rejectsCatalogCountAndItemLimitViolations() {
        assertInvalid("<Response><CmdType>Catalog</CmdType><SN>1</SN>"
                + "<DeviceID>34020000002000000001</DeviceID><SumNum>-1</SumNum>"
                + "<DeviceList Num=\"0\"/></Response>");
        assertInvalid("<Response><CmdType>Catalog</CmdType><SN>1</SN>"
                + "<DeviceID>34020000002000000001</DeviceID><SumNum>1</SumNum>"
                + "<DeviceList Num=\"2\"><Item><DeviceID>11</DeviceID></Item></DeviceList></Response>");

        String xml = "<Response><CmdType>Catalog</CmdType><SN>1</SN>"
                + "<DeviceID>34020000002000000001</DeviceID><SumNum>2</SumNum>"
                + "<DeviceList Num=\"2\"><Item><DeviceID>11</DeviceID></Item>"
                + "<Item><DeviceID>12</DeviceID></Item></DeviceList></Response>";
        assertThrows(IllegalArgumentException.class,
                () -> GbXml.parse(xml.getBytes(StandardCharsets.UTF_8), MAX_MESSAGE_BYTES, 1));
    }

    @Test
    void rejectsDuplicateItemFieldsAndInvalidAdministrativeIds() {
        assertInvalid("<Response><CmdType>Catalog</CmdType><SN>1</SN>"
                + "<DeviceID>34020000002000000001</DeviceID><SumNum>1</SumNum>"
                + "<DeviceList Num=\"1\"><Item><DeviceID>11</DeviceID><Name>a</Name><Name>b</Name>"
                + "</Item></DeviceList></Response>");
        assertInvalid("<Response><CmdType>Catalog</CmdType><SN>1</SN>"
                + "<DeviceID>34020000002000000001</DeviceID><SumNum>1</SumNum>"
                + "<DeviceList Num=\"1\"><Item><DeviceID>1</DeviceID>"
                + "</Item></DeviceList></Response>");
        assertInvalid("<Response><CmdType>Catalog</CmdType><SN>1</SN>"
                + "<DeviceID>34020000002000000001</DeviceID><SumNum>1</SumNum>"
                + "<DeviceList Num=\"1\"><Item><DeviceID>11</DeviceID><Name>"
                + "x".repeat(257) + "</Name></Item></DeviceList></Response>");
    }

    @Test
    void rejectsDoctypeAndExternalEntitiesWithoutLeakingBody() {
        String secret = "UNIQUE-SENSITIVE-MARKER";
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE Notify ["
                + "<!ENTITY xxe SYSTEM \"file:///definitely-not-present/" + secret + "\">]>"
                + "<Notify><CmdType>Keepalive</CmdType><SN>1</SN>"
                + "<DeviceID>34020000002000000001</DeviceID><Status>&xxe;</Status></Notify>";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> GbXml.parse(xml.getBytes(StandardCharsets.UTF_8), MAX_MESSAGE_BYTES, MAX_CATALOG_ITEMS));

        assertTrue(exception.getMessage() == null || !exception.getMessage().contains(secret));
    }

    @Test
    void rejectsOversizedBodiesAndInvalidLimits() {
        byte[] body = "<Notify/>".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> GbXml.parse(body, body.length - 1, 1));
        assertThrows(IllegalArgumentException.class, () -> GbXml.parse(body, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> GbXml.parse(body, body.length, -1));
    }

    @Test
    void rejectsExcessiveXmlDepthAndElementCount() {
        String deeplyNested = "<Notify>" + "<X>".repeat(40) + "value"
                + "</X>".repeat(40) + "</Notify>";
        assertInvalid(deeplyNested);

        String tooManyElements = "<Notify>" + "<X/>".repeat(2_100) + "</Notify>";
        assertThrows(IllegalArgumentException.class,
                () -> GbXml.parse(tooManyElements.getBytes(StandardCharsets.UTF_8),
                        tooManyElements.length() + 1, MAX_CATALOG_ITEMS));
    }

    @Test
    void rejectsMalformedOrUnsupportedEncodings() {
        byte[] malformedUtf8 = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Notify>"
                + "<CmdType>Keepalive</CmdType><SN>1</SN><DeviceID>34020000002000000001</DeviceID>"
                + "<Status>").getBytes(StandardCharsets.UTF_8);
        byte[] suffix = "</Status></Notify>".getBytes(StandardCharsets.UTF_8);
        byte[] body = java.util.Arrays.copyOf(malformedUtf8, malformedUtf8.length + 1 + suffix.length);
        body[malformedUtf8.length] = (byte) 0xC3;
        System.arraycopy(suffix, 0, body, malformedUtf8.length + 1, suffix.length);

        assertThrows(IllegalArgumentException.class,
                () -> GbXml.parse(body, MAX_MESSAGE_BYTES, MAX_CATALOG_ITEMS));
        assertInvalid("<?xml version=\"1.0\" encoding=\"NO-SUCH-ENCODING\"?>"
                + "<Notify><CmdType>Keepalive</CmdType><SN>1</SN>"
                + "<DeviceID>34020000002000000001</DeviceID><Status>OK</Status></Notify>");
    }

    @Test
    void rejectsInvalidCatalogQueryArguments() {
        assertThrows(IllegalArgumentException.class, () -> GbXml.catalogQuery("123", 1));
        assertThrows(IllegalArgumentException.class,
                () -> GbXml.catalogQuery("34020000002000000001", 0));
    }

    private static GbXml.Message parse(String xml) {
        return GbXml.parse(xml.getBytes(StandardCharsets.UTF_8), MAX_MESSAGE_BYTES, MAX_CATALOG_ITEMS);
    }

    private static void assertInvalid(String xml) {
        assertThrows(IllegalArgumentException.class, () -> parse(xml));
    }
}
