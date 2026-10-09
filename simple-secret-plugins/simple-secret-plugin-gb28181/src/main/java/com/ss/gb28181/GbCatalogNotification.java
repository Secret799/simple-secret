package com.ss.gb28181;

import java.util.List;
import java.util.Objects;

/** One Catalog NOTIFY batch. A null entry type denotes plain directory information. */
public record GbCatalogNotification(String deviceId, int sn, int total, List<Entry> entries) {
    public GbCatalogNotification {
        Objects.requireNonNull(deviceId, "deviceId");
        entries = List.copyOf(entries);
    }

    public record Entry(CatalogItem item, Type type) {
        public Entry {
            Objects.requireNonNull(item, "item");
        }
    }

    public enum Type {
        ON, OFF, VLOST, DEFECT, ADD, DEL, UPDATE
    }
}
