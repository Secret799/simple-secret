package com.ss.gb28181;

/**
 * Recording metadata reported by a device. Optional fields remain {@code null}; times retain the
 * device XML dateTime text. The file path is metadata only and is never opened by this component.
 * Device ID, name and secrecy are required by GB/T 28181-2022 A.2.1.10.
 */
public record RecordItem(String deviceId, String name, String filePath, String address,
                         String startTime, String endTime, Integer secrecy, String type,
                         String recorderId, Long fileSize) {
}
