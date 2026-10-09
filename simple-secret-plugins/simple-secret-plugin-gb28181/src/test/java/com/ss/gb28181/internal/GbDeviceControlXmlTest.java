package com.ss.gb28181.internal;

import com.ss.gb28181.GbAlarmResetCommand;
import com.ss.gb28181.GbDragZoomCommand;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GbDeviceControlXmlTest {
    private static final String TARGET = "34020000001320000002";

    @Test
    void createsExactUtf8AlarmResetXmlForAllFilteredAndTypedCommands() {
        assertEquals(control(7, "<AlarmCmd>ResetAlarm</AlarmCmd>\r\n<Info>\r\n<AlarmMethod>0</AlarmMethod>\r\n</Info>"),
                text(GbXml.alarmReset(TARGET, 7, GbAlarmResetCommand.all())));
        assertEquals(control(8, "<AlarmCmd>ResetAlarm</AlarmCmd>\r\n<Info>\r\n<AlarmMethod>2/5/7</AlarmMethod>\r\n</Info>"),
                text(GbXml.alarmReset(TARGET, 8, new GbAlarmResetCommand(Set.of(7, 2, 5), null))));
        assertEquals(control(9, "<AlarmCmd>ResetAlarm</AlarmCmd>\r\n<Info>\r\n<AlarmMethod>2</AlarmMethod>\r\n<AlarmType>5</AlarmType>\r\n</Info>"),
                text(GbXml.alarmReset(TARGET, 9, new GbAlarmResetCommand(Set.of(2), 5))));
    }

    @Test
    void createsExactUtf8KeyFrameXmlWithoutOtherOperations() {
        assertEquals(control(11, "<IFrameCmd>Send</IFrameCmd>"), text(GbXml.keyFrameRequest(TARGET, 11)));
    }

    @Test
    void createsExactUtf8DragZoomXmlWithRequiredChildOrder() {
        var command = new GbDragZoomCommand(640, 360, 320, 180, 640, 360);
        String children = "<DragZoomIn>\r\n<Length>640</Length>\r\n<Width>360</Width>\r\n"
                + "<MidPointX>320</MidPointX>\r\n<MidPointY>180</MidPointY>\r\n"
                + "<LengthX>640</LengthX>\r\n<LengthY>360</LengthY>\r\n</DragZoomIn>";
        assertEquals(control(12, children), text(GbXml.dragZoomIn(TARGET, 12, command)));
        assertEquals(control(13, children.replace("DragZoomIn", "DragZoomOut")),
                text(GbXml.dragZoomOut(TARGET, 13, command)));
    }

    @Test
    void rejectsInvalidDragZoomInputs() {
        assertThrows(IllegalArgumentException.class, () -> GbXml.dragZoomIn("123", 1,
                new GbDragZoomCommand(1, 1, 1, 1, 1, 1)));
        assertThrows(IllegalArgumentException.class, () -> GbXml.dragZoomIn(TARGET, 0,
                new GbDragZoomCommand(1, 1, 1, 1, 1, 1)));
        assertThrows(NullPointerException.class, () -> GbXml.dragZoomIn(TARGET, 1, null));
    }

    @Test
    void rejectsInvalidTargetsSequenceNumbersAndMissingCommand() {
        assertThrows(IllegalArgumentException.class, () -> GbXml.alarmReset("123", 1, GbAlarmResetCommand.all()));
        assertThrows(IllegalArgumentException.class, () -> GbXml.alarmReset(TARGET, 0, GbAlarmResetCommand.all()));
        assertThrows(NullPointerException.class, () -> GbXml.alarmReset(TARGET, 1, null));
        assertThrows(IllegalArgumentException.class, () -> GbXml.keyFrameRequest("123", 1));
        assertThrows(IllegalArgumentException.class, () -> GbXml.keyFrameRequest(TARGET, 0));
    }

    private static String control(int sn, String operation) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<Control>\r\n<CmdType>DeviceControl</CmdType>\r\n<SN>" + sn + "</SN>\r\n"
                + "<DeviceID>" + TARGET + "</DeviceID>\r\n" + operation + "\r\n</Control>";
    }

    private static String text(byte[] bytes) { return new String(bytes, StandardCharsets.UTF_8); }
}
