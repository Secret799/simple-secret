package com.ss.gb28181.internal;

import com.ss.gb28181.PtzPosition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class GbPtzPositionXmlTest {
    @Test void parsesPosition() {
        var m = parse("<PTZPosInfo><Pan>12.50</Pan><Tilt>-3.25</Tilt><Zoom>2.00</Zoom></PTZPosInfo>");
        assertEquals(new PtzPosition("34020000001320000002", 12.5, -3.25, 2.0), m.ptzPosition());
    }
    @Test void allowsMissingOptionalAxes() {
        assertNull(parse("<PTZPosInfo><Pan>0</Pan></PTZPosInfo>").ptzPosition().tilt());
    }
    @ParameterizedTest @ValueSource(strings = {"<PTZPosInfo/>", "<PTZPosInfo><Pan>NaN</Pan></PTZPosInfo>",
            "<PTZPosInfo><Pan>361</Pan></PTZPosInfo>", "<PTZPosInfo><Tilt>-361</Tilt></PTZPosInfo>",
            "<PTZPosInfo><Zoom>-1</Zoom></PTZPosInfo>", "<PTZPosInfo><Pan>1</Pan><Pan>2</Pan></PTZPosInfo>"})
    void rejectsInvalidPosition(String body) {
        assertThrows(IllegalArgumentException.class, () -> parse(body));
    }
    private GbXml.Message parse(String body) {
        String xml = "<Response><CmdType>PTZPosition</CmdType><SN>7</SN><DeviceID>34020000001320000002</DeviceID>"
                + body + "</Response>";
        return GbXml.parse(xml.getBytes(StandardCharsets.UTF_8), 65536, 100);
    }
}
