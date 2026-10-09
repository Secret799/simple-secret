package com.ss.gb28181;

import com.ss.gb28181.internal.GbXml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HomePositionXmlTest {
    private static final String DEVICE = "34020000001320000001";

    @Test void preservesUnreportedConfigurationInsteadOfInferringDisabled() {
        HomePosition home = parse("").homePosition();
        assertEquals(DEVICE, home.deviceId());
        assertNull(home.enabled());
        assertNull(home.resetTime());
        assertNull(home.presetIndex());
    }

    @Test void acceptsDisabledAndBoundaryValuesWithoutInferringOptionalFields() {
        HomePosition disabled = parse("<HomePosition><Enabled>0</Enabled></HomePosition>").homePosition();
        assertFalse(disabled.enabled());
        assertNull(disabled.resetTime());
        assertNull(disabled.presetIndex());
        HomePosition upper = parse("<HomePosition><Enabled>1</Enabled><ResetTime>2147483647</ResetTime>"
                + "<PresetIndex>255</PresetIndex></HomePosition>").homePosition();
        assertEquals(Integer.MAX_VALUE, upper.resetTime());
        assertEquals(255, upper.presetIndex());
        HomePosition zero = parse("<HomePosition><Enabled>1</Enabled><ResetTime>0</ResetTime>"
                + "<PresetIndex>0</PresetIndex></HomePosition>").homePosition();
        assertEquals(0, zero.resetTime());
        assertEquals(0, zero.presetIndex());
    }

    @ParameterizedTest @ValueSource(strings = {
            "<HomePosition/>",
            "<HomePosition><Enabled>2</Enabled></HomePosition>",
            "<HomePosition><Enabled>-1</Enabled></HomePosition>",
            "<HomePosition><Enabled>1</Enabled><ResetTime>-1</ResetTime></HomePosition>",
            "<HomePosition><Enabled>1</Enabled><ResetTime>2147483648</ResetTime></HomePosition>",
            "<HomePosition><Enabled>1</Enabled><PresetIndex>256</PresetIndex></HomePosition>",
            "<HomePosition><Enabled>1</Enabled><PresetIndex/></HomePosition>",
            "<HomePosition><Enabled>1</Enabled><Enabled>0</Enabled></HomePosition>",
            "<HomePosition><Enabled>1</Enabled><Extra><ResetTime>20</ResetTime></Extra></HomePosition>",
            "<Extra><HomePosition><Enabled>1</Enabled></HomePosition></Extra>",
            "<HomePosition><Enabled>1</Enabled></HomePosition><Enabled>0</Enabled>",
            "<HomePosition><Enabled>1</Enabled></HomePosition><HomePosition><Enabled>0</Enabled></HomePosition>"
    })
    void rejectsInvalidOrMisplacedConfiguration(String body) {
        assertThrows(IllegalArgumentException.class, () -> parse(body));
    }

    @Test void preservesExplicitErrorResultForQueryFailure() {
        assertEquals("ERROR", parse("<Result>ERROR</Result>").result());
    }

    private GbXml.Message parse(String body) {
        String xml = "<Response><CmdType>HomePositionQuery</CmdType><SN>7</SN><DeviceID>"
                + DEVICE + "</DeviceID>" + body + "</Response>";
        return GbXml.parse(xml.getBytes(StandardCharsets.UTF_8), 65536, 100);
    }
}
