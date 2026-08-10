package com.ss.geo.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 相机位置与绝对姿态。
 */
public class CameraPose implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private double lat;
    private double lon;
    private double alt;
    private double yaw;
    private double pitch;
    private double roll;

    /** 返回纬度。 */
    public double getLat() { return lat; }
    /** 设置纬度。 */
    public CameraPose setLat(double lat) { this.lat = lat; return this; }
    /** 返回经度。 */
    public double getLon() { return lon; }
    /** 设置经度。 */
    public CameraPose setLon(double lon) { this.lon = lon; return this; }
    /** 返回海拔。 */
    public double getAlt() { return alt; }
    /** 设置海拔。 */
    public CameraPose setAlt(double alt) { this.alt = alt; return this; }
    /** 返回偏航角。 */
    public double getYaw() { return yaw; }
    /** 设置偏航角。 */
    public CameraPose setYaw(double yaw) { this.yaw = yaw; return this; }
    /** 返回俯仰角。 */
    public double getPitch() { return pitch; }
    /** 设置俯仰角。 */
    public CameraPose setPitch(double pitch) { this.pitch = pitch; return this; }
    /** 返回横滚角。 */
    public double getRoll() { return roll; }
    /** 设置横滚角。 */
    public CameraPose setRoll(double roll) { this.roll = roll; return this; }
}
