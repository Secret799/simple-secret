package com.ss.gb28181.internal;

import com.ss.gb28181.PresetItem;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GbPresetXmlTest {

    private static final String CHANNEL_ID = "34020000001320000002";
    private static final int MAX_MESSAGE_BYTES = 100_000;

    @Test
    void createsUtf8PresetQuery() {
        String xml = new String(GbXml.presetQuery(CHANNEL_ID, 1), StandardCharsets.UTF_8);

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<Query>\r\n<CmdType>PresetQuery</CmdType>\r\n<SN>1</SN>\r\n"
                + "<DeviceID>34020000001320000002</DeviceID>\r\n</Query>", xml);
    }

    @Test
    void rejectsInvalidPresetQueryArguments() {
        assertThrows(IllegalArgumentException.class, () -> GbXml.presetQuery("123", 1));
        assertThrows(IllegalArgumentException.class, () -> GbXml.presetQuery(CHANNEL_ID, 0));
    }

    @Test
    void parsesPresetResponseWithEscapedUnicodeText() {
        GbXml.Message message = parse("""
                <Response>
                  <CmdType>PresetQuery</CmdType>
                  <SN>1</SN>
                  <DeviceID>34020000001320000002</DeviceID>
                  <SumNum>2</SumNum>
                  <PresetList Num="2">
                    <Item><PresetID>  vendor-A  </PresetID><PresetName>Gate &amp; Yard 北门</PresetName></Item>
                    <Item><PresetID>2</PresetID><PresetName></PresetName></Item>
                  </PresetList>
                </Response>
                """);

        assertEquals("Response", message.kind());
        assertEquals("PresetQuery", message.command());
        assertEquals(1, message.sn());
        assertEquals(CHANNEL_ID, message.deviceId());
        assertEquals(2, message.total());
        assertEquals(List.of(
                new PresetItem("vendor-A", "Gate & Yard 北门"),
                new PresetItem("2", "")
        ), message.presets());
        assertThrows(UnsupportedOperationException.class,
                () -> message.presets().add(new PresetItem("3", "Roof")));
    }

    @Test
    void parsesEmptyPresetResponse() {
        GbXml.Message message = parse("""
                <Response><CmdType>PresetQuery</CmdType><SN>2</SN>
                <DeviceID>34020000001320000002</DeviceID><SumNum>0</SumNum>
                <PresetList Num="0"/></Response>
                """);

        assertEquals(0, message.total());
        assertEquals(List.of(), message.presets());
    }

    @Test
    void acceptsAtMostTwoHundredFiftyFivePresets() {
        StringBuilder items = new StringBuilder();
        for (int index = 1; index <= 255; index++) {
            items.append("<Item><PresetID>").append(index)
                    .append("</PresetID><PresetName>P").append(index)
                    .append("</PresetName></Item>");
        }
        GbXml.Message message = parse("<Response><CmdType>PresetQuery</CmdType><SN>3</SN>"
                + "<DeviceID>" + CHANNEL_ID + "</DeviceID><SumNum>255</SumNum>"
                + "<PresetList Num=\"255\">" + items + "</PresetList></Response>");

        assertEquals(255, message.presets().size());
        assertEquals(new PresetItem("255", "P255"), message.presets().get(254));
    }

    @Test
    void rejectsTotalsAndDeclaredCountsAboveFixedLimit() {
        assertInvalid(response("256", "0", ""));
        assertInvalid(response("255", "256", ""));
    }

    @Test
    void rejectsCountMismatchesAndItemsAboveTotal() {
        String item = "<Item><PresetID>1</PresetID><PresetName>Gate</PresetName></Item>";
        assertInvalid(response("1", "0", item));
        assertInvalid(response("0", "1", item));
    }

    @Test
    void rejectsMissingAndDuplicateSingletonNodes() {
        assertInvalid("<Response><CmdType>PresetQuery</CmdType><SN>1</SN><DeviceID>" + CHANNEL_ID
                + "</DeviceID><PresetList Num=\"0\"/></Response>");
        assertInvalid("<Response><CmdType>PresetQuery</CmdType><SN>1</SN><DeviceID>" + CHANNEL_ID
                + "</DeviceID><SumNum>0</SumNum><SumNum>0</SumNum><PresetList Num=\"0\"/></Response>");
        assertInvalid("<Response><CmdType>PresetQuery</CmdType><SN>1</SN><DeviceID>" + CHANNEL_ID
                + "</DeviceID><SumNum>0</SumNum><PresetList Num=\"0\"/><PresetList Num=\"0\"/></Response>");
        assertInvalid(response("1", "1", "<Item><PresetID>1</PresetID></Item>"));
        assertInvalid(response("1", "1", "<Item><PresetName>Gate</PresetName></Item>"));
        assertInvalid(response("1", "1", "<Item><PresetID>1</PresetID><PresetID>2</PresetID>"
                + "<PresetName>Gate</PresetName></Item>"));
        assertInvalid(response("1", "1", "<Item><PresetID>1</PresetID><PresetName>A</PresetName>"
                + "<PresetName>B</PresetName></Item>"));
    }

    @Test
    void rejectsBlankOversizedAndNestedPresetFields() {
        assertInvalid(response("1", "1", "<Item><PresetID> </PresetID><PresetName>Gate</PresetName></Item>"));
        assertInvalid(response("1", "1", "<Item><PresetID>" + "x".repeat(65)
                + "</PresetID><PresetName>Gate</PresetName></Item>"));
        assertInvalid(response("1", "1", "<Item><PresetID>1</PresetID><PresetName>"
                + "x".repeat(257) + "</PresetName></Item>"));
        assertInvalid(response("1", "1", "<Item><PresetID><Value>1</Value></PresetID>"
                + "<PresetName>Gate</PresetName></Item>"));
        assertInvalid(response("1", "1", "<Item><PresetID>1</PresetID>"
                + "<PresetName><Value>Gate</Value></PresetName></Item>"));
    }

    @Test
    void rejectsMalformedAndXxePresetResponses() {
        assertInvalid("<Response><CmdType>PresetQuery</CmdType>");
        String secret = "UNIQUE-PRESET-MARKER";
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE Response ["
                + "<!ENTITY xxe SYSTEM \"file:///definitely-not-present/" + secret + "\">]>"
                + response("1", "1", "<Item><PresetID>1</PresetID>"
                + "<PresetName>&xxe;</PresetName></Item>");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> parse(xml));
        if (exception.getMessage() != null && exception.getMessage().contains(secret)) {
            throw new AssertionError("Parser error leaked XML body content");
        }
    }

    @Test
    void legacyMessageConstructorsDefaultPresetsToEmpty() {
        List<GbXml.Message> messages = new ArrayList<>();
        messages.add(new GbXml.Message("Notify", "Keepalive", 1, CHANNEL_ID,
                "OK", 0, List.of()));
        messages.add(new GbXml.Message("Response", "DeviceInfo", 1, CHANNEL_ID,
                null, 0, List.of(), "OK", null, null));
        messages.add(new GbXml.Message("Response", "RecordInfo", 1, CHANNEL_ID,
                null, 0, List.of(), null, null, null, List.of()));
        messages.add(new GbXml.Message("Notify", "Alarm", 1, CHANNEL_ID,
                null, 0, List.of(), null, null, null, List.of(), null));

        for (GbXml.Message message : messages) {
            assertEquals(List.of(), message.presets());
        }
    }

    private static GbXml.Message parse(String xml) {
        return GbXml.parse(xml.getBytes(StandardCharsets.UTF_8), MAX_MESSAGE_BYTES, 10);
    }

    private static String response(String total, String count, String items) {
        return "<Response><CmdType>PresetQuery</CmdType><SN>1</SN><DeviceID>" + CHANNEL_ID
                + "</DeviceID><SumNum>" + total + "</SumNum><PresetList Num=\"" + count + "\">"
                + items + "</PresetList></Response>";
    }

    private static void assertInvalid(String xml) {
        assertThrows(IllegalArgumentException.class, () -> parse(xml));
    }
}
