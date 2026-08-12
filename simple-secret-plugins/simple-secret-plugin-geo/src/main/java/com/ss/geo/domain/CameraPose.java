package com.ss.geo.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 相机位置与绝对姿态。
 */
public class CameraPose implements Serializable {

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
     * 偏航角。
     */
    private double yaw;
    /**
     * 俯仰角。
     */
    private double pitch;
    /**
     * 横滚角。
     */
    private double roll;

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
    public CameraPose setLat(double lat) { this.lat = lat; return this; }
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
    public CameraPose setLon(double lon) { this.lon = lon; return this; }
    /**
     * 返回海拔。
     *
     * @return 高度
     */
    public double getAlt() { return alt; }
    /**
     * 设置海拔。
     *
     * @param alt 高度
     * @return 当前对象
     */
    public CameraPose setAlt(double alt) { this.alt = alt; return this; }
    /**
     * 返回偏航角。
     *
     * @return 偏航角
     */
    public double getYaw() { return yaw; }
    /**
     * 设置偏航角。
     *
     * @param yaw 偏航角
     * @return 当前对象
     */
    public CameraPose setYaw(double yaw) { this.yaw = yaw; return this; }
    /**
     * 返回俯仰角。
     *
     * @return 俯仰角
     */
    public double getPitch() { return pitch; }
    /**
     * 设置俯仰角。
     *
     * @param pitch 俯仰角
     * @return 当前对象
     */
    public CameraPose setPitch(double pitch) { this.pitch = pitch; return this; }
    /**
     * 返回横滚角。
     *
     * @return 横滚角
     */
    public double getRoll() { return roll; }
    /**
     * 设置横滚角。
     *
     * @param roll 横滚角
     * @return 当前对象
     */
    public CameraPose setRoll(double roll) { this.roll = roll; return this; }
}
