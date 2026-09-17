package com.ss.gb28181;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GbCatalogModelsTest {
    private static final String DEVICE = "34020000002000000001";

    @Test
    void notificationCopiesEntriesWhilePreservingOrderDuplicatesAndNullEventType() {
        var item = new CatalogItem("34020000001320000001", "Gate", DEVICE, "ON");
        var entries = new ArrayList<>(List.of(
                new GbCatalogNotification.Entry(item, GbCatalogNotification.Type.ADD),
                new GbCatalogNotification.Entry(item, GbCatalogNotification.Type.ADD),
                new GbCatalogNotification.Entry(item, null)));

        var notification = new GbCatalogNotification(DEVICE, 7, 3, entries);
        entries.clear();

        assertEquals(3, notification.entries().size());
        assertEquals(GbCatalogNotification.Type.ADD, notification.entries().get(0).type());
        assertEquals(GbCatalogNotification.Type.ADD, notification.entries().get(1).type());
        assertEquals(null, notification.entries().get(2).type());
        assertThrows(UnsupportedOperationException.class, () -> notification.entries().clear());
    }

    @Test
    void catalogEventAndListenerExposeAuthenticatedSourceDialogAndReceiptTime() {
        var notification = new GbCatalogNotification(DEVICE, 7, 0, List.of());
        var receivedAt = Instant.parse("2026-09-15T02:00:00Z");
        var event = new GbCatalogEvent(DEVICE, "catalog-call", receivedAt, notification);
        GbCatalogListener listener = delivered -> assertSame(event, delivered);

        listener.onCatalog(event);
        assertEquals(DEVICE, event.sourceDeviceId());
        assertEquals("catalog-call", event.callId());
        assertEquals(receivedAt, event.receivedAt());
        assertSame(notification, event.notification());
    }

    @Test
    void subscriptionExposesReadOnlyCompletionAndIdempotentStopContract() {
        var completion = new CompletableFuture<Void>();
        var stopped = CompletableFuture.<Void>completedFuture(null);
        var request = GbCatalogSubscriptionRequest.all(Duration.ofMinutes(5));
        var subscription = new GbCatalogSubscription(DEVICE, "catalog-call", request, completion, () -> stopped);

        assertEquals(DEVICE, subscription.deviceId());
        assertEquals("catalog-call", subscription.callId());
        assertSame(request, subscription.request());
        assertSame(stopped, subscription.stop());
        assertSame(stopped, subscription.stop());
        subscription.completion().toCompletableFuture().complete(null);
        assertEquals(false, completion.isDone());
    }
}
