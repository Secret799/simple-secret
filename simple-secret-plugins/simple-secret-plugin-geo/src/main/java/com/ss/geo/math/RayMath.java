package com.ss.geo.math;

import com.ss.geo.domain.CameraState;
import com.ss.geo.domain.PixelCoordinate;

/**
 * 射线计算：像素坐标 → 相机系方向 → NED 方向 → ECEF 方向
 *
 * 相机坐标系：X=右，Y=下，Z=前（视线方向）
 *
 * @author JunPzx
 * @since 2026/5/2
 */
public final class RayMath {

    private RayMath() {
    }

    /**
     * 像素坐标 → 相机系射线方向
     *
     * @param pixel 像素坐标
     * @param state 相机状态
     * @return [x, y, z] 相机系单位方向向量
     */
    public static double[] pixelToCameraDirection(PixelCoordinate pixel, CameraState state) {
        double cx = state.getIntrinsics().getCx();
        double cy = state.getIntrinsics().getCy();
        double fx = state.getIntrinsics().getFx();
        double fy = state.getIntrinsics().getFy();

        // 像素偏移（中心为原点，右为正，下为正）
        double dx = (pixel.getX() - cx) / fx;
        double dy = (pixel.getY() - cy) / fy;

        double[] dir = {dx, dy, 1.0};
        RotationMath.normalize(dir);
        return dir;
    }

    /**
     * 相机系方向 → NED 系方向
     *
     * @param cameraDir 相机系单位方向向量
     * @param state     相机状态
     * @return [north, east, down] NED 单位方向向量
     */
    public static double[] cameraToNedDirection(double[] cameraDir, CameraState state) {
        // DJI 云台角度均为绝对角度（相对水平面/正北，经增稳），不受飞机姿态影响
        double absYaw = state.getGimbalYaw();
        double absPitch = state.getGimbalPitch();
        double absRoll = state.getGimbalRoll();

        // 矩阵行是 NED 中的 right/down/look 基向量；转置即 CV → NED。
        double[][] r = RotationMath.fromEulerZYX(absYaw, absPitch, absRoll);
        return RotationMath.multiplyTranspose(r, cameraDir);
    }

    /**
     * 像素坐标 → NED 系射线方向（组合方法）
     *
     * @param pixel 像素坐标
     * @param state 相机状态
     * @return [north, east, down] NED 单位方向向量
     */
    public static double[] pixelToNedDirection(PixelCoordinate pixel, CameraState state) {
        double[] cameraDir = pixelToCameraDirection(pixel, state);
        return cameraToNedDirection(cameraDir, state);
    }
}
