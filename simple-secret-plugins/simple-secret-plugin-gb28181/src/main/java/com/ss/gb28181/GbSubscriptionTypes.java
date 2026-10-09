package com.ss.gb28181;

import com.ss.gb28181.internal.GbXml;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Payload and wire differences for the two direct-device subscription packages. */
final class GbSubscriptionTypes {
    private GbSubscriptionTypes() { }

    static final GbSubscriptions.Type<GbAlarmSubscriptionRequest, GbAlarm, GbAlarmSubscription> ALARM =
            new GbSubscriptions.Type<>() {
                public String command() { return "Alarm"; }
                public String eventType() { return "presence"; }
                public boolean usesEventId() { return false; }
                public long expires(GbAlarmSubscriptionRequest request) { return request.expires().getSeconds(); }
                public byte[] xml(String target, int sn, GbAlarmSubscriptionRequest request) {
                    return GbXml.alarmSubscribe(target, sn, request);
                }
                public GbAlarm decode(GbXml.Message content, String device, String target) {
                    if (!"Notify".equals(content.kind()) || !"Alarm".equals(content.command())
                            || !target.equals(device) && !target.equals(content.deviceId()))
                        throw new IllegalArgumentException("Unexpected Alarm notification target or command");
                    return content.alarm();
                }
                public GbAlarmSubscription handle(String device, String target, String id, GbAlarmSubscriptionRequest request,
                                                  CompletableFuture<Void> completion, Supplier<CompletableFuture<Void>> stop) {
                    return new GbAlarmSubscription(device, target, id, request, completion, stop);
                }
            };

    static final GbSubscriptions.Type<GbCatalogSubscriptionRequest, GbCatalogNotification, GbCatalogSubscription> CATALOG =
            new GbSubscriptions.Type<>() {
                public String command() { return "Catalog"; }
                public String eventType() { return "Catalog"; }
                public boolean usesEventId() { return true; }
                public long expires(GbCatalogSubscriptionRequest request) { return request.expires().getSeconds(); }
                public byte[] xml(String target, int sn, GbCatalogSubscriptionRequest request) {
                    return GbXml.catalogSubscribe(target, sn, request);
                }
                public GbCatalogNotification decode(GbXml.Message content, String device, String target) {
                    if (!"Notify".equals(content.kind()) || !"Catalog".equals(content.command())
                            || !device.equals(content.deviceId()))
                        throw new IllegalArgumentException("Unexpected Catalog notification owner or command");
                    return content.catalogNotification();
                }
                public GbCatalogSubscription handle(String device, String target, String id, GbCatalogSubscriptionRequest request,
                                                    CompletableFuture<Void> completion, Supplier<CompletableFuture<Void>> stop) {
                    return new GbCatalogSubscription(device, id, request, completion, stop);
                }
            };

    static final GbSubscriptions.Type<GbMobilePositionSubscriptionRequest, GbMobilePositionNotification,
            GbMobilePositionSubscription> MOBILE_POSITION = new GbSubscriptions.Type<>() {
                public String command() { return "MobilePosition"; }
                public String eventType() { return "presence"; }
                public boolean usesEventId() { return false; }
                public long expires(GbMobilePositionSubscriptionRequest request) { return request.expires().getSeconds(); }
                public byte[] xml(String target, int sn, GbMobilePositionSubscriptionRequest request) {
                    return GbXml.mobilePositionSubscription(target, sn, request);
                }
                public GbMobilePositionNotification decode(GbXml.Message content, String device, String target) {
                    if (!"Notify".equals(content.kind()) || !"MobilePosition".equals(content.command())
                            || !device.equals(content.deviceId())) {
                        throw new IllegalArgumentException("Unexpected MobilePosition notification owner or command");
                    }
                    return content.mobilePositionNotification();
                }
                public GbMobilePositionSubscription handle(String device, String target, String id,
                                                           GbMobilePositionSubscriptionRequest request,
                                                           CompletableFuture<Void> completion,
                                                           Supplier<CompletableFuture<Void>> stop) {
                    return new GbMobilePositionSubscription(device, id, request, completion, stop);
                }
            };
}
