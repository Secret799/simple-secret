package com.ss.geo.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 像素射线与目标地面相交后的地理结果。
 */
public class GeoTarget implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 纬度。
     */
    private double lat;
    /**
     * 经度。
     */
    private double lon;
    /**
     * 高度。
     */
    private double alt;
    /**
     * 相机到目标的空间距离，单位米。
     */
    private double distance;
    /**
     * 相机与目标的水平距离，单位米。
     */
    private double horizontalDistance;

    /**
     * 返回纬度。
     *
     * @return 纬度
     */
    public double getLat() { return lat; }
    /**
     * 设置纬度。
     *
     * @param lat 纬度
     * @return 当前对象
     */
    public GeoTarget setLat(double lat) { this.lat = lat; return this; }
    /**
     * 返回经度。
     *
     * @return 经度
     */
    public double getLon() { return lon; }
    /**
     * 设置经度。
     *
     * @param lon 经度
     * @return 当前对象
     */
    public GeoTarget setLon(double lon) { this.lon = lon; return this; }
    /**
     * 返回目标海拔。
     *
     * @return 高度
     */
    public double getAlt() { return alt; }
    /**
     * 设置目标海拔。
     *
     * @param alt 高度
     * @return 当前对象
     */
    public GeoTarget setAlt(double alt) { this.alt = alt; return this; }
    /**
     * 返回相机到目标的斜距。
     *
     * @return 相机到目标的空间距离，单位米
     */
    public double getDistance() { return distance; }
    /**
     * 设置相机到目标的斜距。
     *
     * @param distance 相机到目标的空间距离，单位米
     * @return 当前对象
     */
    public GeoTarget setDistance(double distance) { this.distance = distance; return this; }
    /**
     * 返回相机到目标的水平距离。
     *
     * @return 相机与目标的水平距离，单位米
     */
    public double getHorizontalDistance() { return horizontalDistance; }
    /**
     * 设置相机到目标的水平距离。
     *
     * @param distance 相机到目标的空间距离，单位米
     * @return 当前对象
     */
    public GeoTarget setHorizontalDistance(double distance) { this.horizontalDistance = distance; return this; }
}
