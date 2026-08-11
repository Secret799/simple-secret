package com.ss.ics.dahua;

/** 大华热成像 8192 坐标系中的点。 */
public record DahuaPoint(int x, int y) {
    /** 校验坐标范围。 */
    public DahuaPoint {
        if (x < 0 || x > 8192 || y < 0 || y > 8192) {
            throw new IllegalArgumentException("coordinates must be between 0 and 8192");
        }
    }
}
