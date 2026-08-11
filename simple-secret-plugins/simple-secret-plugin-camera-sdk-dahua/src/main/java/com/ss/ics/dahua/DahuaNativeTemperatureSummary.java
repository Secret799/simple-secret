package com.ss.ics.dahua;

/** 原生温度统计快照。 */
record DahuaNativeTemperatureSummary(
        int meterType, int temperatureUnit,
        float average, float maximum, float minimum, float middle, float standardDeviation) {
}
