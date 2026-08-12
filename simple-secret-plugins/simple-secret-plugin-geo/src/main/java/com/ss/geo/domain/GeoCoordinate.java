package com.ss.geo.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * WGS84 地理坐标。
 */
public class GeoCoordinate implements Serializable {

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

    /** 创建空坐标。 */
    public GeoCoordinate() {
    }

    /**
     * 创建地理坐标。
     *
     * @param lat 纬度，单位为度
     * @param lon 经度，单位为度
     * @param alt 海拔，单位为米
     */
    public GeoCoordinate(double lat, double lon, double alt) {
        this.lat = lat;
        this.lon = lon;
        this.alt = alt;
    }

    /**
     * 返回纬度。
     *
     * @return 纬度
     */
    public double getLat() {
        return lat;
    }

    /**
     * 设置纬度。
     *
     * @param lat 纬度
     * @return 当前对象
     */
    public GeoCoordinate setLat(double lat) {
        this.lat = lat;
        return this;
    }

    /**
     * 返回经度。
     *
     * @return 经度
     */
    public double getLon() {
        return lon;
    }

    /**
     * 设置经度。
     *
     * @param lon 经度
     * @return 当前对象
     */
    public GeoCoordinate setLon(double lon) {
        this.lon = lon;
        return this;
    }

    /**
     * 返回海拔。
     *
     * @return 高度
     */
    public double getAlt() {
        return alt;
    }

    /**
     * 设置海拔。
     *
     * @param alt 高度
     * @return 当前对象
     */
    public GeoCoordinate setAlt(double alt) {
        this.alt = alt;
        return this;
    }
}
