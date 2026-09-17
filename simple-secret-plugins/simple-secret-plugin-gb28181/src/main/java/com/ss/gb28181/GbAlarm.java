package com.ss.gb28181;

/** Alarm values reported by a GB28181 device or channel. */
public record GbAlarm(String deviceId, int sn, int priority, int method, String time,
                      String description, Double longitude, Double latitude,
                      Integer type, Integer eventType) {
}
