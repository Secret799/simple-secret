package com.ss.gb28181;

import java.time.Instant;

/** A catalog notification together with its authenticated owner, dialog and receipt time. */
public record GbCatalogEvent(String sourceDeviceId, String callId, Instant receivedAt,
                             GbCatalogNotification notification) {
}
