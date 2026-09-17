package com.ss.gb28181;

/** Device information from a successful query; absent optional fields remain {@code null}. */
public record DeviceInfo(String deviceId, String deviceName, String manufacturer, String model,
                         String firmware, Integer channelCount) {
}
