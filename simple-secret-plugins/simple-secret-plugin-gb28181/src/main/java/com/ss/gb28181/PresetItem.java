package com.ss.gb28181;

/** Preset metadata reported by a channel. String IDs are retained without numeric conversion. */
public record PresetItem(String presetId, String presetName) {
    public PresetItem {
        if (presetId == null || presetId.isBlank() || presetId.length() > 64)
            throw new IllegalArgumentException("Preset ID must be nonblank and at most 64 characters");
        if (presetName == null || presetName.length() > 256)
            throw new IllegalArgumentException("Preset name is required and must not exceed 256 characters");
    }
}
