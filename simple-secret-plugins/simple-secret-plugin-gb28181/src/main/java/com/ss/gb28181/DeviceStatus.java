package com.ss.gb28181;

/**
 * Device status from a successful query. Online is ONLINE/OFFLINE, status is OK/ERROR,
 * and optional encode/record values are ON/OFF. Missing optional fields remain {@code null}.
 * Device time retains its XML dateTime text, including an optional timezone; no timezone is inferred.
 */
public record DeviceStatus(String deviceId, String online, String status, String encode,
                           String record, String deviceTime) {
}
