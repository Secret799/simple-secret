package com.ss.geo.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 像素射线与目标地面相交后的地理结果。
 */
public class GeoTarget implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private double lat;
    private double lon;
    private double alt;
    private double distance;
    private double horizontalDistance;

    /** 返回纬度。 */
    public double getLat() { return lat; }
    /** 设置纬度。 */
    public GeoTarget setLat(double lat) { this.lat = lat; return this; }
    /** 返回经度。 */
    public double getLon() { return lon; }
    /** 设置经度。 */
    public GeoTarget setLon(double lon) { this.lon = lon; return this; }
    /** 返回目标海拔。 */
    public double getAlt() { return alt; }
    /** 设置目标海拔。 */
    public GeoTarget setAlt(double alt) { this.alt = alt; return this; }
    /** 返回相机到目标的斜距。 */
    public double getDistance() { return distance; }
    /** 设置相机到目标的斜距。 */
    public GeoTarget setDistance(double distance) { this.distance = distance; return this; }
    /** 返回相机到目标的水平距离。 */
    public double getHorizontalDistance() { return horizontalDistance; }
    /** 设置相机到目标的水平距离。 */
    public GeoTarget setHorizontalDistance(double distance) { this.horizontalDistance = distance; return this; }
}
