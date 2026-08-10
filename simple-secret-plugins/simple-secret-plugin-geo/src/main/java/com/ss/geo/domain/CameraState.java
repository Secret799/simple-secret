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

    private CameraPose pose = new CameraPose();
    private CameraIntrinsics intrinsics = new CameraIntrinsics();
    private FrameSize frame = new FrameSize();

    /** 返回相机外参。 */
    public CameraPose getPose() { return pose; }

    /** 设置相机外参。 */
    public CameraState setPose(CameraPose pose) {
        this.pose = Objects.requireNonNull(pose, "pose");
        return this;
    }

    /** 返回相机内参。 */
    public CameraIntrinsics getIntrinsics() { return intrinsics; }

    /** 设置相机内参。 */
    public CameraState setIntrinsics(CameraIntrinsics intrinsics) {
        this.intrinsics = Objects.requireNonNull(intrinsics, "intrinsics");
        return this;
    }

    /** 返回画面尺寸。 */
    public FrameSize getFrame() { return frame; }

    /** 设置画面尺寸。 */
    public CameraState setFrame(FrameSize frame) {
        this.frame = Objects.requireNonNull(frame, "frame");
        refreshIntrinsicsFromFov();
        return this;
    }

    /** 返回纬度。 */
    public double getLat() { return pose.getLat(); }
    /** 设置纬度。 */
    public CameraState setLat(double lat) { pose.setLat(lat); return this; }
    /** 返回经度。 */
    public double getLon() { return pose.getLon(); }
    /** 设置经度。 */
    public CameraState setLon(double lon) { pose.setLon(lon); return this; }
    /** 返回海拔。 */
    public double getAlt() { return pose.getAlt(); }
    /** 设置海拔。 */
    public CameraState setAlt(double alt) { pose.setAlt(alt); return this; }
    /** 返回云台偏航角。 */
    public double getGimbalYaw() { return pose.getYaw(); }
    /** 设置云台偏航角。 */
    public CameraState setGimbalYaw(double yaw) { pose.setYaw(yaw); return this; }
    /** 返回云台俯仰角。 */
    public double getGimbalPitch() { return pose.getPitch(); }
    /** 设置云台俯仰角。 */
    public CameraState setGimbalPitch(double pitch) { pose.setPitch(pitch); return this; }
    /** 返回云台横滚角。 */
    public double getGimbalRoll() { return pose.getRoll(); }
    /** 设置云台横滚角。 */
    public CameraState setGimbalRoll(double roll) { pose.setRoll(roll); return this; }
    /** 返回水平视场角。 */
    public double getFovH() { return intrinsics.getFovH(); }
    /** 设置水平视场角。 */
    public CameraState setFovH(double fovH) { intrinsics.setFovH(fovH); refreshIntrinsicsFromFov(); return this; }
    /** 返回垂直视场角。 */
    public double getFovV() { return intrinsics.getFovV(); }
    /** 设置垂直视场角。 */
    public CameraState setFovV(double fovV) { intrinsics.setFovV(fovV); refreshIntrinsicsFromFov(); return this; }
    /** 返回画面宽度。 */
    public int getFrameWidth() { return frame.getWidth(); }
    /** 设置画面宽度。 */
    public CameraState setFrameWidth(int width) { frame.setWidth(width); refreshIntrinsicsFromFov(); return this; }
    /** 返回画面高度。 */
    public int getFrameHeight() { return frame.getHeight(); }
    /** 设置画面高度。 */
    public CameraState setFrameHeight(int height) { frame.setHeight(height); refreshIntrinsicsFromFov(); return this; }

    private void refreshIntrinsicsFromFov() {
        if (frame.getWidth() > 0 && frame.getHeight() > 0
                && intrinsics.getFovH() > 0 && intrinsics.getFovV() > 0) {
            intrinsics = CameraIntrinsics.fromFov(
                    intrinsics.getFovH(), intrinsics.getFovV(), frame.getWidth(), frame.getHeight());
        }
    }
}
