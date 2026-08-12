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
    public DjiCameraTelemetry setLat(double lat) { this.lat = lat; return this; }
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
    public DjiCameraTelemetry setLon(double lon) { this.lon = lon; return this; }
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
    public DjiCameraTelemetry setAlt(double alt) { this.alt = alt; return this; }
    /**
     * 返回飞机航向角。
     *
     * @return 飞行器偏航角
     */
    public double getFlightYaw() { return flightYaw; }
    /**
     * 设置飞机航向角。
     *
     * @param flightYaw 飞行器偏航角
     * @return 当前对象
     */
    public DjiCameraTelemetry setFlightYaw(double flightYaw) { this.flightYaw = flightYaw; return this; }
    /**
     * 返回云台偏航角。
     *
     * @return 云台偏航角
     */
    public double getGimbalYaw() { return gimbalYaw; }
    /**
     * 设置云台偏航角。
     *
     * @param gimbalYaw 云台偏航角
     * @return 当前对象
     */
    public DjiCameraTelemetry setGimbalYaw(double gimbalYaw) { this.gimbalYaw = gimbalYaw; return this; }
    /**
     * 返回云台俯仰角。
     *
     * @return 云台俯仰角
     */
    public double getGimbalPitch() { return gimbalPitch; }
    /**
     * 设置云台俯仰角。
     *
     * @param pitch 俯仰角
     * @return 当前对象
     */
    public DjiCameraTelemetry setGimbalPitch(double pitch) { this.gimbalPitch = pitch; return this; }
    /**
     * 返回云台横滚角。
     *
     * @return 云台横滚角
     */
    public double getGimbalRoll() { return gimbalRoll; }
    /**
     * 设置云台横滚角。
     *
     * @param roll 横滚角
     * @return 当前对象
     */
    public DjiCameraTelemetry setGimbalRoll(double roll) { this.gimbalRoll = roll; return this; }
    /**
     * 返回画面宽度。
     *
     * @return 图像帧宽度
     */
    public int getFrameWidth() { return frameWidth; }
    /**
     * 设置画面宽度。
     *
     * @param width 宽度
     * @return 当前对象
     */
    public DjiCameraTelemetry setFrameWidth(int width) { this.frameWidth = width; return this; }
    /**
     * 返回画面高度。
     *
     * @return 图像帧高度
     */
    public int getFrameHeight() { return frameHeight; }
    /**
     * 设置画面高度。
     *
     * @param height 高度
     * @return 当前对象
     */
    public DjiCameraTelemetry setFrameHeight(int height) { this.frameHeight = height; return this; }
    /**
     * 返回投影上下文。
     *
     * @return {@code projectionContext}
     */
    public DjiProjectionContext getProjectionContext() { return projectionContext; }
    /**
     * 设置投影上下文。
     *
     * @param context 调用上下文
     * @return 当前对象
     */
    public DjiCameraTelemetry setProjectionContext(DjiProjectionContext context) { this.projectionContext = context; return this; }
}
