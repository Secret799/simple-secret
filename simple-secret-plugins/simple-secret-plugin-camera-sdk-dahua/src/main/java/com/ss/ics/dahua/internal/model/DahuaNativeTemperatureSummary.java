package com.ss.ics.dahua.internal.model;

/**
 * 原生温度统计快照。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public record DahuaNativeTemperatureSummary(
        int meterType, int temperatureUnit,
        float average, float maximum, float minimum, float middle, float standardDeviation) {
}
