package com.ss.geo.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * 相机完整状态，包含位置姿态、内参与画面尺寸。
 */
public class CameraState implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 相机位姿。
     */
    private CameraPose pose = new CameraPose();
    /**
     * 相机内参。
     */
    private CameraIntrinsics intrinsics = new CameraIntrinsics();
    /**
     * 视频帧尺寸。
     */
    private FrameSize frame = new FrameSize();

    /**
     * 返回相机外参。
     *
     * @return 相机位姿
     */
    public CameraPose getPose() { return pose; }

    /**
     * 设置相机外参。
     *
     * @param pose 相机位姿
     * @return 当前对象
     */
    public CameraState setPose(CameraPose pose) {
        this.pose = Objects.requireNonNull(pose, "pose");
        return this;
    }

    /**
     * 返回相机内参。
     *
     * @return 相机内参
     */
    public CameraIntrinsics getIntrinsics() { return intrinsics; }

    /**
     * 设置相机内参。
     *
     * @param intrinsics 相机内参
     * @return 当前对象
     */
    public CameraState setIntrinsics(CameraIntrinsics intrinsics) {
        this.intrinsics = Objects.requireNonNull(intrinsics, "intrinsics");
        return this;
    }

    /**
     * 返回画面尺寸。
     *
     * @return 视频帧尺寸
     */
    public FrameSize getFrame() { return frame; }

    /**
     * 设置画面尺寸。
     *
     * @param frame 视频帧尺寸
     * @return 当前对象
     */
    public CameraState setFrame(FrameSize frame) {
        this.frame = Objects.requireNonNull(frame, "frame");
        refreshIntrinsicsFromFov();
        return this;
    }

    /**
     * 返回纬度。
     *
     * @return 纬度
     */
    public double getLat() { return pose.getLat(); }
    /**
     * 设置纬度。
     *
     * @param lat 纬度
     * @return 当前对象
     */
    public CameraState setLat(double lat) { pose.setLat(lat); return this; }
    /**
     * 返回经度。
     *
     * @return 经度
     */
    public double getLon() { return pose.getLon(); }
    /**
     * 设置经度。
     *
     * @param lon 经度
     * @return 当前对象
     */
    public CameraState setLon(double lon) { pose.setLon(lon); return this; }
    /**
     * 返回海拔。
     *
     * @return 高度
     */
    public double getAlt() { return pose.getAlt(); }
    /**
     * 设置海拔。
     *
     * @param alt 高度
     * @return 当前对象
     */
    public CameraState setAlt(double alt) { pose.setAlt(alt); return this; }
    /**
     * 返回云台偏航角。
     *
     * @return 云台偏航角
     */
    public double getGimbalYaw() { return pose.getYaw(); }
    /**
     * 设置云台偏航角。
     *
     * @param yaw 偏航角
     * @return 当前对象
     */
    public CameraState setGimbalYaw(double yaw) { pose.setYaw(yaw); return this; }
    /**
     * 返回云台俯仰角。
     *
     * @return 云台俯仰角
     */
    public double getGimbalPitch() { return pose.getPitch(); }
    /**
     * 设置云台俯仰角。
     *
     * @param pitch 俯仰角
     * @return 当前对象
     */
    public CameraState setGimbalPitch(double pitch) { pose.setPitch(pitch); return this; }
    /**
     * 返回云台横滚角。
     *
     * @return 云台横滚角
     */
    public double getGimbalRoll() { return pose.getRoll(); }
    /**
     * 设置云台横滚角。
     *
     * @param roll 横滚角
     * @return 当前对象
     */
    public CameraState setGimbalRoll(double roll) { pose.setRoll(roll); return this; }
    /**
     * 返回水平视场角。
     *
     * @return 水平视场角
     */
    public double getFovH() { return intrinsics.getFovH(); }
    /**
     * 设置水平视场角。
     *
     * @param fovH 水平视场角
     * @return 当前对象
     */
    public CameraState setFovH(double fovH) { intrinsics.setFovH(fovH); refreshIntrinsicsFromFov(); return this; }
    /**
     * 返回垂直视场角。
     *
     * @return 垂直视场角
     */
    public double getFovV() { return intrinsics.getFovV(); }
    /**
     * 设置垂直视场角。
     *
     * @param fovV 垂直视场角
     * @return 当前对象
     */
    public CameraState setFovV(double fovV) { intrinsics.setFovV(fovV); refreshIntrinsicsFromFov(); return this; }
    /**
     * 返回画面宽度。
     *
     * @return 图像帧宽度
     */
    public int getFrameWidth() { return frame.getWidth(); }
    /**
     * 设置画面宽度。
     *
     * @param width 宽度
     * @return 当前对象
     */
    public CameraState setFrameWidth(int width) { frame.setWidth(width); refreshIntrinsicsFromFov(); return this; }
    /**
     * 返回画面高度。
     *
     * @return 图像帧高度
     */
    public int getFrameHeight() { return frame.getHeight(); }
    /**
     * 设置画面高度。
     *
     * @param height 高度
     * @return 当前对象
     */
    public CameraState setFrameHeight(int height) { frame.setHeight(height); refreshIntrinsicsFromFov(); return this; }

    private void refreshIntrinsicsFromFov() {
        if (frame.getWidth() > 0 && frame.getHeight() > 0
                && intrinsics.getFovH() > 0 && intrinsics.getFovV() > 0) {
            intrinsics = CameraIntrinsics.fromFov(
                    intrinsics.getFovH(), intrinsics.getFovV(), frame.getWidth(), frame.getHeight());
        }
    }
}
