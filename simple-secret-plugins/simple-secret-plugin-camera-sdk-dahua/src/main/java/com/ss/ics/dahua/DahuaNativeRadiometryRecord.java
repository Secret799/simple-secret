package com.ss.ics.dahua;

import java.time.LocalDateTime;
import java.util.List;

/** 原生历史热成像记录快照。 */
record DahuaNativeRadiometryRecord(
        LocalDateTime timestamp, int presetId, int ruleId, String name, int channel,
        DahuaNativeTemperatureSummary temperature, List<DahuaPoint> coordinates) {
    DahuaNativeRadiometryRecord {
        coordinates = List.copyOf(coordinates);
    }
}
