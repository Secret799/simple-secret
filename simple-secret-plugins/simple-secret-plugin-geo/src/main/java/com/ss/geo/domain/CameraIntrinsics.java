package com.ss.geo.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 针孔相机内参。
 */
public class CameraIntrinsics implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 水平视场角。
     */
    private double fovH;
    /**
     * 垂直视场角。
     */
    private double fovV;
    /**
     * 水平方向焦距像素值。
     */
    private double fx;
    /**
     * 垂直方向焦距像素值。
     */
    private double fy;
    /**
     * 相机主点横坐标。
     */
    private double cx;
    /**
     * 相机主点纵坐标。
     */
    private double cy;

    /**
     * 根据视场角与画面尺寸创建内参。
     *
     * @param fovH 水平视场角
     * @param fovV 垂直视场角
     * @param frameWidth 图像帧宽度
     * @param frameHeight 图像帧高度
     * @return 当前对象
     */
    public static CameraIntrinsics fromFov(double fovH, double fovV, int frameWidth, int frameHeight) {
        if (!Double.isFinite(fovH) || !Double.isFinite(fovV) || fovH <= 0 || fovH >= 180
                || fovV <= 0 || fovV >= 180 || frameWidth <= 0 || frameHeight <= 0) {
            throw new IllegalArgumentException("视场角必须位于 (0, 180)，画面尺寸必须为正数");
        }
        double cx = frameWidth / 2.0;
        double cy = frameHeight / 2.0;
        return new CameraIntrinsics()
                .setFovH(fovH).setFovV(fovV)
                .setFx(cx / Math.tan(Math.toRadians(fovH) / 2.0))
                .setFy(cy / Math.tan(Math.toRadians(fovV) / 2.0))
                .setCx(cx).setCy(cy);
    }

    /**
     * 返回水平视场角。
     *
     * @return 水平视场角
     */
    public double getFovH() { return fovH; }
    /**
     * 设置水平视场角。
     *
     * @param fovH 水平视场角
     * @return 当前对象
     */
    public CameraIntrinsics setFovH(double fovH) { this.fovH = fovH; return this; }
    /**
     * 返回垂直视场角。
     *
     * @return 垂直视场角
     */
    public double getFovV() { return fovV; }
    /**
     * 设置垂直视场角。
     *
     * @param fovV 垂直视场角
     * @return 当前对象
     */
    public CameraIntrinsics setFovV(double fovV) { this.fovV = fovV; return this; }
    /**
     * 返回水平像素焦距。
     *
     * @return 水平方向焦距像素值
     */
    public double getFx() { return fx; }
    /**
     * 设置水平像素焦距。
     *
     * @param fx 水平方向焦距像素值
     * @return 当前对象
     */
    public CameraIntrinsics setFx(double fx) { this.fx = fx; return this; }
    /**
     * 返回垂直像素焦距。
     *
     * @return 垂直方向焦距像素值
     */
    public double getFy() { return fy; }
    /**
     * 设置垂直像素焦距。
     *
     * @param fy 垂直方向焦距像素值
     * @return 当前对象
     */
    public CameraIntrinsics setFy(double fy) { this.fy = fy; return this; }
    /**
     * 返回主点 X。
     *
     * @return 相机主点横坐标
     */
    public double getCx() { return cx; }
    /**
     * 设置主点 X。
     *
     * @param cx 相机主点横坐标
     * @return 当前对象
     */
    public CameraIntrinsics setCx(double cx) { this.cx = cx; return this; }
    /**
     * 返回主点 Y。
     *
     * @return 相机主点纵坐标
     */
    public double getCy() { return cy; }
    /**
     * 设置主点 Y。
     *
     * @param cy 相机主点纵坐标
     * @return 当前对象
     */
    public CameraIntrinsics setCy(double cy) { this.cy = cy; return this; }
}
