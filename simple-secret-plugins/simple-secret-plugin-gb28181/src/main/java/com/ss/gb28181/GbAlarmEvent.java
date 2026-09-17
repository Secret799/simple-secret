package com.ss.gb28181;

import java.time.Instant;

/** An alarm together with its authenticated registration owner and receipt time. */
public record GbAlarmEvent(String sourceDeviceId, Instant receivedAt, GbAlarm alarm) {
}
