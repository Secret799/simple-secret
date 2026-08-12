package com.ss.geo;

import com.ss.geo.domain.BoundingBox;
import com.ss.geo.domain.CameraState;
import com.ss.geo.domain.GeoCoordinate;
import com.ss.geo.domain.GeoTarget;
import com.ss.geo.domain.GeoTargetWithBox;
import com.ss.geo.domain.PipelineProjection;
import com.ss.geo.domain.PixelCoordinate;
import com.ss.geo.spec.CameraSpec;
import com.ss.geo.spec.DjiCameraTelemetry;
import com.ss.geo.spec.DjiProjectionContext;

import java.util.List;

/**
 * DJI 实时遥测地理参照。
 *
 * @author JunPzx
 * @since 2026/6/27
 */
public final class DjiTelemetryGeoreferencer {

    private DjiTelemetryGeoreferencer() {
    }

    /**
     * 根据实时遥测构建相机状态。
     *
     * @param telemetry DJI 实时相机遥测
     * @return 相机状态
     */
    public static CameraState buildCameraState(DjiCameraTelemetry telemetry) {
        if (telemetry == null) {
            throw new IllegalArgumentException("DJI telemetry must not be null");
        }
        DjiProjectionContext context = telemetry.getProjectionContext();
        if (context == null || context.getDroneModel() == null || context.getCameraType() == null) {
            throw new IllegalArgumentException("DJI telemetry requires droneModel and cameraType");
        }
        if (telemetry.getFrameWidth() <= 0 || telemetry.getFrameHeight() <= 0) {
            throw new IllegalArgumentException(
                    "DJI telemetry frame width and frame height must be positive");
        }
        CameraSpec spec = context.getDroneModel().getSpec(context.getCameraType());
        if (spec == null) {
            throw new IllegalArgumentException("Unsupported camera type for drone model: " + context.getCameraType());
        }
        double zoomFactor = context.getZoomFactor() != null
                ? context.getZoomFactor() : spec.baselineZoomFactor();
        double[] fov = spec.fovHvAtZoom(
                zoomFactor, telemetry.getFrameWidth(), telemetry.getFrameHeight());
        CameraState state = new CameraState()
                .setLat(telemetry.getLat())
                .setLon(telemetry.getLon())
                .setAlt(telemetry.getAlt())
                .setGimbalYaw(resolveCameraYaw(telemetry))
                .setGimbalPitch(telemetry.getGimbalPitch())
                .setGimbalRoll(telemetry.getGimbalRoll())
                .setFovH(fov[0])
                .setFovV(fov[1])
                .setFrameWidth(telemetry.getFrameWidth())
                .setFrameHeight(telemetry.getFrameHeight());
        GeoReferencer.validateCameraState(state);
        return state;
    }

    /**
     * 解析相机投影使用的水平朝向。
     *
     * <p>实时遥测在云台正下视（约 -90°）时，yaw/roll 语义同样会耦合。
     * 未翻转正射使用绝对云台 yaw；roll 接近 ±180° 时使用无人机航向，
     * 同时继续保留云台 roll 参与旋转。非正下视场景使用云台 yaw。
     */
    private static double resolveCameraYaw(DjiCameraTelemetry telemetry) {
        if (Math.abs(telemetry.getGimbalPitch() + 90.0) < 1.0) {
            if (Math.abs(Math.abs(telemetry.getGimbalRoll()) - 180.0) < 1.0) {
                return telemetry.getFlightYaw();
            }
            if (Math.abs(telemetry.getGimbalRoll()) < 1.0) {
                return telemetry.getGimbalYaw();
            }
            return telemetry.getFlightYaw();
        }
        return telemetry.getGimbalYaw();
    }

    /**
     * 像素点 → 地理坐标。

     *
     * @param telemetry DJI 遥测数据
     * @param pixel 像素坐标
     * @param groundAltitude 地面海拔高度
     * @return 返回的 {@code GeoTarget} 结果
     */
    public static GeoTarget pixelToGeo(DjiCameraTelemetry telemetry, PixelCoordinate pixel, double groundAltitude) {
        return GeoReferencer.pixelToGeo(pixel, buildCameraState(telemetry), groundAltitude);
    }

    /**
     * 推理框 → 地理坐标。

     *
     * @param telemetry DJI 遥测数据
     * @param boxes 检测框集合
     * @param groundAltitude 地面海拔高度
     * @return 返回的 {@code List<GeoTargetWithBox>} 结果
     */
    public static List<GeoTargetWithBox> boxesToGeo(DjiCameraTelemetry telemetry,
                                                    List<BoundingBox> boxes, double groundAltitude) {
        return GeoReferencer.boxesToGeo(boxes, buildCameraState(telemetry), groundAltitude);
    }

    /**
     * 地理管线 → 像素投影。

     *
     * @param telemetry DJI 遥测数据
     * @param name 名称
     * @param pipeline 管线坐标序列
     * @param bufferMeters 管线中心线缓冲半径，单位米
     * @return 返回的 {@code PipelineProjection} 结果
     */
    public static PipelineProjection pipelineToPixels(DjiCameraTelemetry telemetry, String name,
                                                      List<GeoCoordinate> pipeline, double bufferMeters) {
        return GeoReferencer.pipelineToPixels(name, pipeline, buildCameraState(telemetry), bufferMeters);
    }
}
