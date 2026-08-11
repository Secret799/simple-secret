package com.ss.ics.dahua;

/** 原生区域温度快照。 */
record DahuaNativeRegionTemperature(
        int temperatureUnit, double average, double maximum, double minimum,
        DahuaPoint maximumPoint, DahuaPoint minimumPoint) {
}
