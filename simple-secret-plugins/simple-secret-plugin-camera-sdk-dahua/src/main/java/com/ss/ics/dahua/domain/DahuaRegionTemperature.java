package com.ss.ics.dahua.domain;

/** 任意多边形区域的温度统计和极值坐标。 */
public record DahuaRegionTemperature(
        int temperatureUnit,
        double average,
        double maximum,
        double minimum,
        DahuaPoint maximumPoint,
        DahuaPoint minimumPoint) {
}
