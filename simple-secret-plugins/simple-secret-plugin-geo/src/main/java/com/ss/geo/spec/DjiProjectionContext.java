package com.ss.geo.spec;

import java.io.Serial;
import java.io.Serializable;

/**
 * DJI 投影所需的机型、相机类型与变焦上下文。
 */
public class DjiProjectionContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * DJI 飞行器型号。
     */
    private DjiDroneModel droneModel;
    /**
     * DJI 相机类型。
     */
    private CameraType cameraType;
    /**
     * 相机变焦倍率。
     */
    private Double zoomFactor;

    /**
     * 返回无人机型号。
     *
     * @return DJI 飞行器型号
     */
    public DjiDroneModel getDroneModel() { return droneModel; }
    /**
     * 设置无人机型号。
     *
     * @param droneModel DJI 飞行器型号
     * @return 当前对象
     */
    public DjiProjectionContext setDroneModel(DjiDroneModel droneModel) { this.droneModel = droneModel; return this; }
    /**
     * 返回相机类型。
     *
     * @return DJI 相机类型
     */
    public CameraType getCameraType() { return cameraType; }
    /**
     * 设置相机类型。
     *
     * @param cameraType DJI 相机类型
     * @return 当前对象
     */
    public DjiProjectionContext setCameraType(CameraType cameraType) { this.cameraType = cameraType; return this; }
    /**
     * 返回变焦倍率。
     *
     * @return 相机变焦倍率
     */
    public Double getZoomFactor() { return zoomFactor; }
    /**
     * 设置变焦倍率。
     *
     * @param zoomFactor 相机变焦倍率
     * @return 当前对象
     */
    public DjiProjectionContext setZoomFactor(Double zoomFactor) { this.zoomFactor = zoomFactor; return this; }
}
