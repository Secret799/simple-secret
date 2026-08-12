package com.ss.ics.dahua.internal.model;

import com.ss.ics.dahua.DahuaPoint;

/**
 * 原生区域温度快照。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public record DahuaNativeRegionTemperature(
        int temperatureUnit, double average, double maximum, double minimum,
        DahuaPoint maximumPoint, DahuaPoint minimumPoint) {
}
