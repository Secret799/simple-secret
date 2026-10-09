package com.ss.gb28181.internal;

import com.ss.gb28181.GbAlarmSubscriptionRequest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GbAlarmSubscriptionXmlTest {
    private static final String DEVICE = "34020000002000000001";
    private static final String CENTER = "3402000000";

    @Test
    void encodesAllFieldsInStandardOrderWithSortedMethods() {
        var request = new GbAlarmSubscriptionRequest(Duration.ofMinutes(10), 1, 4,
                Set.of(7, 2, 1), 3,
                LocalDateTime.of(2026, 9, 15, 10, 0, 1),
                LocalDateTime.of(2026, 9, 15, 11, 2, 3));

        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                        + "<Query><CmdType>Alarm</CmdType><SN>42</SN><DeviceID>" + DEVICE + "</DeviceID>"
                        + "<StartAlarmPriority>1</StartAlarmPriority><EndAlarmPriority>4</EndAlarmPriority>"
                        + "<AlarmMethod>1/2/7</AlarmMethod><AlarmType>3</AlarmType>"
                        + "<StartAlarmTime>2026-09-15T10:00:01</StartAlarmTime>"
                        + "<EndAlarmTime>2026-09-15T11:02:03</EndAlarmTime></Query>",
                new String(GbXml.alarmSubscribe(DEVICE, 42, request), StandardCharsets.UTF_8));
    }

    @Test
    void encodesAllFilterForTenDigitAlarmCenter() {
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                        + "<Query><CmdType>Alarm</CmdType><SN>1</SN><DeviceID>" + CENTER + "</DeviceID>"
                        + "<StartAlarmPriority>0</StartAlarmPriority><EndAlarmPriority>0</EndAlarmPriority>"
                        + "<AlarmMethod>0</AlarmMethod></Query>",
                new String(GbXml.alarmSubscribe(CENTER, 1,
                        GbAlarmSubscriptionRequest.all(Duration.ofSeconds(1))), StandardCharsets.UTF_8));
    }

    @Test
    void rejectsInvalidTargetSequenceAndMissingRequest() {
        var request = GbAlarmSubscriptionRequest.all(Duration.ofMinutes(1));
        for (String target : new String[]{"340200000", "34020000000", "3402000000200000000",
                "340200000020000000000", "3402000000200000000A"}) {
            assertThrows(IllegalArgumentException.class, () -> GbXml.alarmSubscribe(target, 1, request), target);
        }
        assertThrows(IllegalArgumentException.class, () -> GbXml.alarmSubscribe(DEVICE, 0, request));
        assertThrows(NullPointerException.class, () -> GbXml.alarmSubscribe(DEVICE, 1, null));
    }
}
