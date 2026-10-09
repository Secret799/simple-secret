package com.ss.gb28181;

import com.ss.gb28181.internal.GbXml;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-query preset snapshot; bounded independently of catalog and recording capacities. */
final class PresetAccumulator {
    private final Map<String, PresetItem> presets = new LinkedHashMap<>();
    private int total = -1;

    List<PresetItem> accept(GbXml.Message message) {
        if (message.total() < 0 || message.total() > 255 || (total != -1 && total != message.total()))
            throw new IllegalArgumentException("Invalid or changed preset total");
        total = message.total();
        for (PresetItem item : message.presets()) {
            PresetItem previous = presets.get(item.presetId());
            if (previous != null) {
                if (!previous.equals(item)) throw new IllegalArgumentException("Conflicting preset item");
            } else {
                if (presets.size() >= total) throw new IllegalArgumentException("Preset count exceeds total");
                presets.put(item.presetId(), item);
            }
        }
        return presets.size() == total ? List.copyOf(presets.values()) : null;
    }
}
