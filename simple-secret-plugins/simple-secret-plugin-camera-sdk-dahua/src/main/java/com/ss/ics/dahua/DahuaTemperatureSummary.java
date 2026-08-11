package com.ss.ics.dahua;

/** 点、线或区域的温度统计。 */
public record DahuaTemperatureSummary(
        int meterType,
        int temperatureUnit,
        float average,
        float maximum,
        float minimum,
        float middle,
        float standardDeviation) {
}
