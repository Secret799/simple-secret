package com.ss.geo.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 保留原始检测框的地理定位结果。
 */
public class GeoTargetWithBox implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private BoundingBox box;
    private boolean located;
    private double lat;
    private double lon;
    private double alt;
    private double distance;
    private double horizontalDistance;

    /** 返回原始检测框。 */
    public BoundingBox getBox() { return box; }
    /** 设置原始检测框。 */
    public GeoTargetWithBox setBox(BoundingBox box) { this.box = box; return this; }
    /** 返回是否已成功定位。 */
    public boolean isLocated() { return located; }
    /** 设置是否已成功定位。 */
    public GeoTargetWithBox setLocated(boolean located) { this.located = located; return this; }
    /** 返回纬度。 */
    public double getLat() { return lat; }
    /** 设置纬度。 */
    public GeoTargetWithBox setLat(double lat) { this.lat = lat; return this; }
    /** 返回经度。 */
    public double getLon() { return lon; }
    /** 设置经度。 */
    public GeoTargetWithBox setLon(double lon) { this.lon = lon; return this; }
    /** 返回目标海拔。 */
    public double getAlt() { return alt; }
    /** 设置目标海拔。 */
    public GeoTargetWithBox setAlt(double alt) { this.alt = alt; return this; }
    /** 返回斜距。 */
    public double getDistance() { return distance; }
    /** 设置斜距。 */
    public GeoTargetWithBox setDistance(double distance) { this.distance = distance; return this; }
    /** 返回水平距离。 */
    public double getHorizontalDistance() { return horizontalDistance; }
    /** 设置水平距离。 */
    public GeoTargetWithBox setHorizontalDistance(double distance) { this.horizontalDistance = distance; return this; }
}
