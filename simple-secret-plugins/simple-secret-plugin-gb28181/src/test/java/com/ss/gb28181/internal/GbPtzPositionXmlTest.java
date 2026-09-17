package com.ss.gb28181.internal;

import com.ss.gb28181.PtzPosition;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class GbPtzPositionXmlTest {
    private static final String DEVICE = "34020000001320000001";

    @Test
    void parsesCoordinatesAndOptionalValues() {
        GbXml.Message message = parse("<PTZPosInfo><Pan>12.50</Pan><Tilt>-3.25</Tilt><Zoom>2.00</Zoom></PTZPosInfo>");
        assertEquals("PTZPosition", message.command());
        assertEquals(new PtzPosition(DEVICE, 12.5, -3.25, 2.0), message.ptzPosition());
    }

    @Test
    void preservesMissingCoordinates() {
        assertEquals(new PtzPosition(DEVICE, null, null, null),
                parse("<PTZPosInfo/>").ptzPosition());
    }

    @Test
    void rejectsMissingDuplicateNestedAndInvalidCoordinates() {
        assertInvalid("");
        assertInvalid("<PTZPosInfo><Pan>1</Pan><Pan>2</Pan></PTZPosInfo>");
        assertInvalid("<PTZPosInfo><Wrapper><Pan>1</Pan></Wrapper></PTZPosInfo>");
        for (String token : new String[]{"NaN", "Infinity", "1e", "1 2"}) {
            assertInvalid("<PTZPosInfo><Pan>" + token + "</Pan></PTZPosInfo>");
        }
        assertInvalid("<PTZPosInfo><Pan>361</Pan></PTZPosInfo>");
        assertInvalid("<PTZPosInfo><Tilt>-361</Tilt></PTZPosInfo>");
        assertInvalid("<PTZPosInfo><Zoom>-0.1</Zoom></PTZPosInfo>");
    }

    @Test
    void preservesErrorResult() {
        GbXml.Message message = parse("<Result>ERROR</Result><PTZPosInfo/>");
        assertEquals("ERROR", message.result());
    }

    private GbXml.Message parse(String body) {
        String xml = "<Response><CmdType>PTZPosition</CmdType><SN>7</SN><DeviceID>" + DEVICE
                + "</DeviceID>" + body + "</Response>";
        return GbXml.parse(xml.getBytes(StandardCharsets.UTF_8), 65536, 100);
    }

    private void assertInvalid(String body) {
        assertThrows(IllegalArgumentException.class, () -> parse(body));
    }
}
