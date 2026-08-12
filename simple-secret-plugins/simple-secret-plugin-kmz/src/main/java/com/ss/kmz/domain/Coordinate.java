package com.ss.kmz.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

/**
 * WGS84 坐标，KML 顺序为经度、纬度、海拔。
 */
public class Coordinate implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 经度。
     */
    private double longitude;
    /**
     * 纬度。
     */
    private double latitude;
    /**
     * 海拔高度。
     */
    private double altitude;

    /** 创建零值坐标。 */
    public Coordinate() {
    }

    /**
     * 创建坐标。
     *
     * @param longitude 经度，范围 [-180, 180]
     * @param latitude 纬度，范围 [-90, 90]
     * @param altitude 海拔，单位米
     */
    public Coordinate(double longitude, double latitude, double altitude) {
        setLongitude(longitude);
        setLatitude(latitude);
        setAltitude(altitude);
    }

    /**
     * 从单个 KML 坐标元组解析坐标。
     *
     * @param kmlCoordinates KML 坐标文本
     * @return 当前对象
     */
    public static Coordinate fromKml(String kmlCoordinates) {
        if (kmlCoordinates == null || kmlCoordinates.isBlank()) {
            throw new IllegalArgumentException("KML coordinate must use longitude,latitude[,altitude] format");
        }
        String[] parts = kmlCoordinates.trim().split(",", -1);
        if (parts.length < 2 || parts.length > 3) {
            throw new IllegalArgumentException("KML coordinate must use longitude,latitude[,altitude] format");
        }
        try {
            double longitude = Double.parseDouble(parts[0].trim());
            double latitude = Double.parseDouble(parts[1].trim());
            double altitude = parts.length == 3 ? Double.parseDouble(parts[2].trim()) : 0.0;
            return new Coordinate(longitude, latitude, altitude);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("KML coordinate contains an invalid number", exception);
        }
    }

    /**
     * 返回固定精度、固定点号小数的 KML 坐标元组。
     *
     * @return 返回的 {@code String} 结果
     */
    public String toKml() {
        return String.format(Locale.ROOT, "%.8f,%.8f,%.2f", longitude, latitude, altitude);
    }

    /**
     * 返回经度。
     *
     * @return 经度
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * 设置经度。
     *
     * @param longitude 经度
     * @return 当前对象
     */
    public Coordinate setLongitude(double longitude) {
        requireFinite(longitude, "longitude");
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("longitude must be within [-180, 180]");
        }
        this.longitude = longitude;
        return this;
    }

    /**
     * 返回纬度。
     *
     * @return 纬度
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * 设置纬度。
     *
     * @param latitude 纬度
     * @return 当前对象
     */
    public Coordinate setLatitude(double latitude) {
        requireFinite(latitude, "latitude");
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("latitude must be within [-90, 90]");
        }
        this.latitude = latitude;
        return this;
    }

    /**
     * 返回海拔。
     *
     * @return 海拔高度
     */
    public double getAltitude() {
        return altitude;
    }

    /**
     * 设置海拔。
     *
     * @param altitude 海拔高度
     * @return 当前对象
     */
    public Coordinate setAltitude(double altitude) {
        requireFinite(altitude, "altitude");
        this.altitude = altitude;
        return this;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Coordinate that)) {
            return false;
        }
        return Double.compare(longitude, that.longitude) == 0
                && Double.compare(latitude, that.latitude) == 0
                && Double.compare(altitude, that.altitude) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(longitude, latitude, altitude);
    }

    @Override
    public String toString() {
        return "Coordinate{longitude=" + longitude + ", latitude=" + latitude + ", altitude=" + altitude + '}';
    }
}
