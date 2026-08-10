package com.ss.geo.spec;

import java.io.Serial;
import java.io.Serializable;

/**
 * DJI 实时相机遥测。
 */
public class DjiCameraTelemetry implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private double lat;
    private double lon;
    private double alt;
    private double flightYaw;
    private double gimbalYaw;
    private double gimbalPitch;
    private double gimbalRoll;
    private int frameWidth;
    private int frameHeight;
    private DjiProjectionContext projectionContext;

    /** 返回纬度。 */
    public double getLat() { return lat; }
    /** 设置纬度。 */
    public DjiCameraTelemetry setLat(double lat) { this.lat = lat; return this; }
    /** 返回经度。 */
    public double getLon() { return lon; }
    /** 设置经度。 */
    public DjiCameraTelemetry setLon(double lon) { this.lon = lon; return this; }
    /** 返回海拔。 */
    public double getAlt() { return alt; }
    /** 设置海拔。 */
    public DjiCameraTelemetry setAlt(double alt) { this.alt = alt; return this; }
    /** 返回飞机航向角。 */
    public double getFlightYaw() { return flightYaw; }
    /** 设置飞机航向角。 */
    public DjiCameraTelemetry setFlightYaw(double flightYaw) { this.flightYaw = flightYaw; return this; }
    /** 返回云台偏航角。 */
    public double getGimbalYaw() { return gimbalYaw; }
    /** 设置云台偏航角。 */
    public DjiCameraTelemetry setGimbalYaw(double gimbalYaw) { this.gimbalYaw = gimbalYaw; return this; }
    /** 返回云台俯仰角。 */
    public double getGimbalPitch() { return gimbalPitch; }
    /** 设置云台俯仰角。 */
    public DjiCameraTelemetry setGimbalPitch(double pitch) { this.gimbalPitch = pitch; return this; }
    /** 返回云台横滚角。 */
    public double getGimbalRoll() { return gimbalRoll; }
    /** 设置云台横滚角。 */
    public DjiCameraTelemetry setGimbalRoll(double roll) { this.gimbalRoll = roll; return this; }
    /** 返回画面宽度。 */
    public int getFrameWidth() { return frameWidth; }
    /** 设置画面宽度。 */
    public DjiCameraTelemetry setFrameWidth(int width) { this.frameWidth = width; return this; }
    /** 返回画面高度。 */
    public int getFrameHeight() { return frameHeight; }
    /** 设置画面高度。 */
    public DjiCameraTelemetry setFrameHeight(int height) { this.frameHeight = height; return this; }
    /** 返回投影上下文。 */
    public DjiProjectionContext getProjectionContext() { return projectionContext; }
    /** 设置投影上下文。 */
    public DjiCameraTelemetry setProjectionContext(DjiProjectionContext context) { this.projectionContext = context; return this; }
}
