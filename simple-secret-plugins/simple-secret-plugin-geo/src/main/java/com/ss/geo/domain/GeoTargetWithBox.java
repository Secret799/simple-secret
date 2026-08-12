package com.ss.geo.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 保留原始检测框的地理定位结果。
 */
public class GeoTargetWithBox implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 原始检测框。
     */
    private BoundingBox box;
    /**
     * 检测框是否成功定位。
     */
    private boolean located;
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
     * 返回原始检测框。
     *
     * @return 检测框
     */
    public BoundingBox getBox() { return box; }
    /**
     * 设置原始检测框。
     *
     * @param box 检测框
     * @return 当前对象
     */
    public GeoTargetWithBox setBox(BoundingBox box) { this.box = box; return this; }
    /**
     * 返回是否已成功定位。
     *
     * @return 满足条件时返回 true
     */
    public boolean isLocated() { return located; }
    /**
     * 设置是否已成功定位。
     *
     * @param located 检测框是否成功定位
     * @return 当前对象
     */
    public GeoTargetWithBox setLocated(boolean located) { this.located = located; return this; }
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
    public GeoTargetWithBox setLat(double lat) { this.lat = lat; return this; }
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
    public GeoTargetWithBox setLon(double lon) { this.lon = lon; return this; }
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
    public GeoTargetWithBox setAlt(double alt) { this.alt = alt; return this; }
    /**
     * 返回斜距。
     *
     * @return 相机到目标的空间距离，单位米
     */
    public double getDistance() { return distance; }
    /**
     * 设置斜距。
     *
     * @param distance 相机到目标的空间距离，单位米
     * @return 当前对象
     */
    public GeoTargetWithBox setDistance(double distance) { this.distance = distance; return this; }
    /**
     * 返回水平距离。
     *
     * @return 相机与目标的水平距离，单位米
     */
    public double getHorizontalDistance() { return horizontalDistance; }
    /**
     * 设置水平距离。
     *
     * @param distance 相机到目标的空间距离，单位米
     * @return 当前对象
     */
    public GeoTargetWithBox setHorizontalDistance(double distance) { this.horizontalDistance = distance; return this; }
}
