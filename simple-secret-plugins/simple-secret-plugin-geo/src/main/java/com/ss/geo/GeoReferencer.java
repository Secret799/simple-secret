package com.ss.geo;

import com.ss.geo.domain.*;
import com.ss.geo.math.EarthMath;
import com.ss.geo.math.IntersectionMath;
import com.ss.geo.math.RayMath;
import com.ss.geo.math.RotationMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 地理坐标参照引擎：像素坐标 → 地理坐标
 *
 * <pre>
 * 算法流程：
 *   1. 像素 → 相机系射线方向（针孔模型）
 *   2. 相机系 → NED 系（云台 + 无人机姿态旋转）
 *   3. NED 系 → ECEF（局部切平面到地心地固）
 *   4. ECEF 射线与目标海拔面求交
 *   5. 交点 ECEF → WGS84 GPS
 * </pre>
 *
 * @author junpzx
 * @since 2026/5/2
 */
public final class GeoReferencer {

    private static final double MITER_LIMIT = 4.0;
    private static final double JOIN_EPSILON = 1.0e-9;

    private GeoReferencer() {
    }

    /**
     * 像素 → 地理坐标
     *
     * @param pixel          像素坐标
     * @param state          相机完整状态
     * @param groundAltitude 目标地面海拔（米），可通过 DEM 服务或起飞点海拔获取
     * @return 目标地理坐标，如果射线未命中地面则返回 null
     */
    public static GeoTarget pixelToGeo(PixelCoordinate pixel, CameraState state, double groundAltitude) {
        validatePixel(pixel);
        validateCameraState(state);
        validateFinite(groundAltitude, "ground altitude");
        // 1. 像素 → 相机系射线方向
        double[] camDir = RayMath.pixelToCameraDirection(pixel, state);

        // 2. 相机系 → NED
        double[] nedDir = RayMath.cameraToNedDirection(camDir, state);

        // 3. NED → ECEF
        double[] ecefDir = EarthMath.nedToEcef(nedDir, state.getLat(), state.getLon());

        // 4. 相机 GPS → ECEF
        double[] camEcef = EarthMath.gpsToEcef(state.getLat(), state.getLon(), state.getAlt());

        // 5. ECEF 射线与目标海拔面求交
        double[] targetEcef = IntersectionMath.intersect(camEcef, ecefDir, groundAltitude);
        if (targetEcef == null) {
            return null;
        }

        // 6. ECEF → GPS
        double[] lla = EarthMath.ecefToGps(targetEcef[0], targetEcef[1], targetEcef[2]);

        // 7. 计算距离
        double dx = targetEcef[0] - camEcef[0];
        double dy = targetEcef[1] - camEcef[1];
        double dz = targetEcef[2] - camEcef[2];
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        // ECEF 轴不是局部水平轴，必须先转换到相机位置的 NED 再忽略 Down 分量。
        double[] targetNed = ecefVectorToNed(
                new double[]{dx, dy, dz}, state.getLat(), state.getLon());
        double hDist = Math.hypot(targetNed[0], targetNed[1]);

        return new GeoTarget()
                .setLat(lla[0])
                .setLon(lla[1])
                .setAlt(lla[2])
                .setDistance(distance)
                .setHorizontalDistance(hDist);
    }

    /**
     * 地理坐标 → 像素坐标
     *
     * @param coordinate 地理坐标
     * @param state      相机完整状态
     * @return 像素坐标，如果目标在相机后方则返回 null
     */
    public static PixelCoordinate geoToPixel(GeoCoordinate coordinate, CameraState state) {
        validateCoordinate(coordinate);
        validateCameraState(state);
        double[] cameraEcef = EarthMath.gpsToEcef(state.getLat(), state.getLon(), state.getAlt());
        double[] targetEcef = EarthMath.gpsToEcef(coordinate.getLat(), coordinate.getLon(), coordinate.getAlt());
        double[] targetNed = ecefVectorToNed(subtract(targetEcef, cameraEcef), state.getLat(), state.getLon());
        double[] cameraVector = nedToCameraVector(targetNed, state);
        if (cameraVector[2] <= 0) {
            return null;
        }
        return cameraVectorToPixel(cameraVector, state);
    }

    /**
     * 管线中心线 → 像素坐标
     *
     * @param name     管线名称
     * @param pipeline 管线地理点集
     * @param state    相机完整状态
     * @return 管线像素投影结果
     */
    public static PipelineProjection pipelineToPixels(String name, List<GeoCoordinate> pipeline, CameraState state) {
        return pipelineToPixels(name, pipeline, state, 0);
    }

    /**
     * 管线中心线和缓冲区 → 像素坐标
     *
     * @param name         管线名称
     * @param pipeline     管线地理点集
     * @param state        相机完整状态
     * @param bufferMeters 管线缓冲半径（米），小于等于0时不生成区域
     * @return 管线像素投影结果
     */
    public static PipelineProjection pipelineToPixels(String name, List<GeoCoordinate> pipeline,
                                                      CameraState state, double bufferMeters) {
        validateCameraState(state);
        validateFinite(bufferMeters, "buffer meters");
        PipelineProjection projection = new PipelineProjection().setName(name);
        if (pipeline == null || pipeline.isEmpty()) {
            return projection;
        }
        for (GeoCoordinate point : pipeline) {
            if (point != null) {
                projection.getCenterline().add(projectPipelinePoint(point, state));
            }
        }
        List<GeoCoordinate> bufferPipeline = simplifyConsecutiveDuplicatePoints(pipeline);
        if (bufferMeters > 0 && bufferPipeline.size() >= 2) {
            for (GeoCoordinate point : buildBufferPolygon(bufferPipeline, bufferMeters)) {
                projection.getArea().add(projectPipelinePoint(point, state));
            }
        }
        return projection;
    }

    /**
     * 像素 → 地理坐标（通过 DEM 查询自动获取地面海拔）
     *
     * <p>注意：此方法会先使用相机海拔作为初始估计进行一次粗算，
     * 然后用粗算结果查询 DEM，再用精确海拔完成最终计算。
     *
     * @param pixel         像素坐标
     * @param state         相机完整状态
     * @param demQuery      DEM 查询回调：输入 (lat, lon)，返回海拔（米）
     * @param fallbackAlt   DEM 查询失败时的回退海拔（米）
     * @return 目标地理坐标
     */
    public static GeoTarget pixelToGeo(PixelCoordinate pixel, CameraState state,
                                       Function<double[], Double> demQuery, double fallbackAlt) {
        if (demQuery == null) {
            throw new IllegalArgumentException("DEM query must not be null");
        }
        validateFinite(fallbackAlt, "fallback altitude");
        // 先用 fallback 做第一次估计
        double initialAlt = fallbackAlt;
        GeoTarget rough = pixelToGeo(pixel, state, initialAlt);
        if (rough == null) {
            return null;
        }

        // 用粗算位置查 DEM
        Double queriedAltitude;
        try {
            queriedAltitude = demQuery.apply(new double[]{rough.getLat(), rough.getLon()});
        } catch (Exception e) {
            queriedAltitude = null;
        }
        double groundAlt = queriedAltitude != null && Double.isFinite(queriedAltitude)
                ? queriedAltitude : fallbackAlt;

        // 精确计算
        return pixelToGeo(pixel, state, groundAlt);
    }

    /**
     * 批量推理框 → 地理坐标（相同相机状态，一次批量计算）
     *
     * @param boxes          推理框列表
     * @param state          相机完整状态
     * @param groundAltitude 目标地面海拔（米）
     * @return 与输入框顺序一致的定位结果；通过 {@link GeoTargetWithBox#isLocated()} 判断是否命中地面
     */
    public static List<GeoTargetWithBox> boxesToGeo(List<BoundingBox> boxes, CameraState state, double groundAltitude) {
        validateCameraState(state);
        validateFinite(groundAltitude, "ground altitude");
        if (boxes == null || boxes.isEmpty()) {
            return new ArrayList<>();
        }
        List<GeoTargetWithBox> results = new ArrayList<>(boxes.size());
        for (BoundingBox box : boxes) {
            GeoTarget target = box == null ? null
                    : pixelToGeo(new PixelCoordinate(box.centerX(), box.centerY()), state, groundAltitude);
            results.add(buildResult(box, target));
        }
        return results;
    }

    /**
     * 批量推理框 → 地理坐标（通过 DEM 查询自动获取地面海拔）

     *
     * @param boxes 检测框集合
     * @param state 会话状态
     * @param demQuery 高程查询函数
     * @param fallbackAlt 高程查询失败时使用的备用海拔
     * @return 返回的 {@code List<GeoTargetWithBox>} 结果
     */
    public static List<GeoTargetWithBox> boxesToGeo(List<BoundingBox> boxes, CameraState state,
                                                    Function<double[], Double> demQuery, double fallbackAlt) {
        validateCameraState(state);
        if (demQuery == null) {
            throw new IllegalArgumentException("DEM query must not be null");
        }
        validateFinite(fallbackAlt, "fallback altitude");
        if (boxes == null || boxes.isEmpty()) {
            return new ArrayList<>();
        }
        List<GeoTargetWithBox> results = new ArrayList<>(boxes.size());
        for (BoundingBox box : boxes) {
            GeoTarget target = box == null ? null
                    : pixelToGeo(new PixelCoordinate(box.centerX(), box.centerY()), state, demQuery, fallbackAlt);
            results.add(buildResult(box, target));
        }
        return results;
    }

    private static GeoTargetWithBox buildResult(BoundingBox box, GeoTarget target) {
        GeoTargetWithBox result = new GeoTargetWithBox()
                .setBox(box)
                .setLocated(target != null);
        if (target != null) {
            result.setLat(target.getLat())
                    .setLon(target.getLon())
                    .setAlt(target.getAlt())
                    .setDistance(target.getDistance())
                    .setHorizontalDistance(target.getHorizontalDistance());
        }
        return result;
    }

    static void validateCameraState(CameraState state) {
        if (state == null) {
            throw new IllegalArgumentException("camera state must not be null");
        }
        validateLatitude(state.getLat(), "camera latitude");
        validateLongitude(state.getLon(), "camera longitude");
        validateFinite(state.getAlt(), "camera altitude");
        if (!Double.isFinite(state.getGimbalYaw())
                || !Double.isFinite(state.getGimbalPitch())
                || !Double.isFinite(state.getGimbalRoll())) {
            throw new IllegalArgumentException("camera attitude angles must be finite");
        }
        if (state.getFrameWidth() <= 0 || state.getFrameHeight() <= 0) {
            throw new IllegalArgumentException("camera frame width and frame height must be positive");
        }
        double fovH = state.getFovH();
        double fovV = state.getFovV();
        if (!Double.isFinite(fovH) || !Double.isFinite(fovV)
                || fovH <= 0 || fovH >= 180 || fovV <= 0 || fovV >= 180) {
            throw new IllegalArgumentException("camera FOV must be finite and within (0, 180)");
        }
        CameraIntrinsics intrinsics = state.getIntrinsics();
        if (intrinsics == null
                || !Double.isFinite(intrinsics.getFx()) || intrinsics.getFx() <= 0
                || !Double.isFinite(intrinsics.getFy()) || intrinsics.getFy() <= 0
                || !Double.isFinite(intrinsics.getCx()) || !Double.isFinite(intrinsics.getCy())) {
            throw new IllegalArgumentException("camera intrinsics must contain finite positive focal lengths");
        }
    }

    private static void validatePixel(PixelCoordinate pixel) {
        if (pixel == null || !Double.isFinite(pixel.getX()) || !Double.isFinite(pixel.getY())) {
            throw new IllegalArgumentException("pixel coordinates must be finite");
        }
    }

    private static void validateCoordinate(GeoCoordinate coordinate) {
        if (coordinate == null) {
            throw new IllegalArgumentException("geo coordinate must not be null");
        }
        validateLatitude(coordinate.getLat(), "latitude");
        validateLongitude(coordinate.getLon(), "longitude");
        validateFinite(coordinate.getAlt(), "altitude");
    }

    private static void validateLatitude(double latitude, String name) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException(name + " must be finite and within [-90, 90]");
        }
    }

    private static void validateLongitude(double longitude, String name) {
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException(name + " must be finite and within [-180, 180]");
        }
    }

    private static void validateFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    /**
     * 构建管线投影点
     *
     * @param coordinate 地理坐标
     * @param state      相机完整状态
     * @return 管线投影点
     */
    private static PipelineProjectionPoint projectPipelinePoint(GeoCoordinate coordinate, CameraState state) {
        PixelCoordinate pixel = geoToPixel(coordinate, state);
        return new PipelineProjectionPoint()
                .setCoordinate(coordinate)
                .setPixel(pixel)
                .setVisible(pixel != null)
                .setInsideFrame(pixel != null && isInsideFrame(pixel, state));
    }

    /**
     * 判断像素点是否在图片范围内
     *
     * @param pixel 像素坐标
     * @param state 相机完整状态
     * @return true表示在图片范围内
     */
    private static boolean isInsideFrame(PixelCoordinate pixel, CameraState state) {
        return pixel.getX() >= 0 && pixel.getX() < state.getFrameWidth()
                && pixel.getY() >= 0 && pixel.getY() < state.getFrameHeight();
    }

    /**
     * NED向量转相机坐标系向量
     *
     * @param ned   NED向量
     * @param state 相机完整状态
     * @return 相机坐标系向量
     */
    private static double[] nedToCameraVector(double[] ned, CameraState state) {
        double[][] rotation = RotationMath.fromEulerZYX(state.getGimbalYaw(), state.getGimbalPitch(), state.getGimbalRoll());
        return RotationMath.multiply(rotation, ned);
    }

    /**
     * 相机坐标系向量转像素坐标
     *
     * @param cameraVector 相机坐标系向量
     * @param state        相机完整状态
     * @return 像素坐标
     */
    private static PixelCoordinate cameraVectorToPixel(double[] cameraVector, CameraState state) {
        double cx = state.getIntrinsics().getCx();
        double cy = state.getIntrinsics().getCy();
        double fx = state.getIntrinsics().getFx();
        double fy = state.getIntrinsics().getFy();
        double x = cx + cameraVector[0] / cameraVector[2] * fx;
        double y = cy + cameraVector[1] / cameraVector[2] * fy;
        return new PixelCoordinate(x, y);
    }

    /**
     * ECEF向量转NED向量
     *
     * @param ecefVector ECEF向量
     * @param lat        参考点纬度
     * @param lon        参考点经度
     * @return NED向量
     */
    private static double[] ecefVectorToNed(double[] ecefVector, double lat, double lon) {
        double[][] basis = EarthMath.nedBasisVectors(lat, lon);
        return new double[]{
                dot(ecefVector, basis[0]),
                dot(ecefVector, basis[1]),
                dot(ecefVector, basis[2])
        };
    }

    /**
     * 移除连续重复坐标点，用于避免管线缓冲区在零长度线段处退化。
     *
     * @param pipeline 原始管线地理点集
     * @return 去除连续重复点后的管线地理点集
     */
    private static List<GeoCoordinate> simplifyConsecutiveDuplicatePoints(List<GeoCoordinate> pipeline) {
        List<GeoCoordinate> result = new ArrayList<>(pipeline.size());
        GeoCoordinate previous = null;
        for (GeoCoordinate point : pipeline) {
            if (point == null) {
                continue;
            }
            if (previous == null || !sameCoordinate(previous, point)) {
                result.add(point);
                previous = point;
            }
        }
        return result;
    }

    /**
     * 判断两个地理坐标是否相同。
     */
    private static boolean sameCoordinate(GeoCoordinate a, GeoCoordinate b) {
        return Double.compare(a.getLat(), b.getLat()) == 0
                && Double.compare(a.getLon(), b.getLon()) == 0
                && Double.compare(a.getAlt(), b.getAlt()) == 0;
    }

    /**
     * 构建管线缓冲区地理多边形
     *
     * @param pipeline     管线地理点集
     * @param bufferMeters 缓冲半径（米）
     * @return 缓冲区地理多边形
     */
    private static List<GeoCoordinate> buildBufferPolygon(List<GeoCoordinate> pipeline, double bufferMeters) {
        GeoCoordinate origin = pipeline.get(0);
        List<double[]> localPoints = toLocalNedPoints(pipeline, origin);
        List<GeoCoordinate> left = buildOffsetSide(origin, localPoints, bufferMeters);
        List<GeoCoordinate> right = buildOffsetSide(origin, localPoints, -bufferMeters);
        Collections.reverse(right);
        left.addAll(right);
        return left;
    }

    private static List<GeoCoordinate> buildOffsetSide(GeoCoordinate origin,
                                                       List<double[]> localPoints,
                                                       double offsetDistance) {
        List<GeoCoordinate> side = new ArrayList<>(localPoints.size());
        for (int i = 0; i < localPoints.size(); i++) {
            double[] point = localPoints.get(i);
            if (i == 0) {
                double[] normal = leftNormal(unitDirection(point, localPoints.get(1)));
                side.add(offsetLocalPoint(origin, point,
                        normal[0] * offsetDistance, normal[1] * offsetDistance));
                continue;
            }
            if (i == localPoints.size() - 1) {
                double[] normal = leftNormal(unitDirection(localPoints.get(i - 1), point));
                side.add(offsetLocalPoint(origin, point,
                        normal[0] * offsetDistance, normal[1] * offsetDistance));
                continue;
            }

            double[] previousDirection = unitDirection(localPoints.get(i - 1), point);
            double[] nextDirection = unitDirection(point, localPoints.get(i + 1));
            double[] previousNormal = leftNormal(previousDirection);
            double[] nextNormal = leftNormal(nextDirection);
            double denominator = 1.0 + previousDirection[0] * nextDirection[0]
                    + previousDirection[1] * nextDirection[1];
            if (denominator <= JOIN_EPSILON) {
                addBevelJoin(side, origin, point, previousNormal, nextNormal, offsetDistance);
                continue;
            }

            double northOffset = (previousNormal[0] + nextNormal[0]) * offsetDistance / denominator;
            double eastOffset = (previousNormal[1] + nextNormal[1]) * offsetDistance / denominator;
            if (Math.hypot(northOffset, eastOffset) > Math.abs(offsetDistance) * MITER_LIMIT) {
                addBevelJoin(side, origin, point, previousNormal, nextNormal, offsetDistance);
            } else {
                side.add(offsetLocalPoint(origin, point, northOffset, eastOffset));
            }
        }
        return side;
    }

    private static void addBevelJoin(List<GeoCoordinate> side, GeoCoordinate origin, double[] point,
                                     double[] previousNormal, double[] nextNormal, double offsetDistance) {
        side.add(offsetLocalPoint(origin, point,
                previousNormal[0] * offsetDistance, previousNormal[1] * offsetDistance));
        side.add(offsetLocalPoint(origin, point,
                nextNormal[0] * offsetDistance, nextNormal[1] * offsetDistance));
    }

    private static double[] unitDirection(double[] from, double[] to) {
        double north = to[0] - from[0];
        double east = to[1] - from[1];
        double length = Math.hypot(north, east);
        if (length <= JOIN_EPSILON) {
            throw new IllegalArgumentException("pipeline contains a zero-length segment");
        }
        return new double[]{north / length, east / length};
    }

    private static double[] leftNormal(double[] direction) {
        return new double[]{direction[1], -direction[0]};
    }

    /**
     * 转为以起点为原点的局部NED点
     *
     * @param pipeline 管线地理点集
     * @param origin   局部坐标原点
     * @return 局部NED点列表
     */
    private static List<double[]> toLocalNedPoints(List<GeoCoordinate> pipeline, GeoCoordinate origin) {
        double[] originEcef = EarthMath.gpsToEcef(origin.getLat(), origin.getLon(), origin.getAlt());
        List<double[]> points = new ArrayList<>(pipeline.size());
        for (GeoCoordinate point : pipeline) {
            double[] ecef = EarthMath.gpsToEcef(point.getLat(), point.getLon(), point.getAlt());
            points.add(ecefVectorToNed(subtract(ecef, originEcef), origin.getLat(), origin.getLon()));
        }
        return points;
    }

    /**
     * 偏移局部NED点并转换为地理坐标
     *
     * @param origin       局部坐标原点
     * @param localPoint   局部NED点
     * @param northOffset  北向偏移（米）
     * @param eastOffset   东向偏移（米）
     * @return 偏移后的地理坐标
     */
    private static GeoCoordinate offsetLocalPoint(GeoCoordinate origin, double[] localPoint,
                                                  double northOffset, double eastOffset) {
        double[] offsetNed = {
                localPoint[0] + northOffset,
                localPoint[1] + eastOffset,
                localPoint[2]
        };
        double[] originEcef = EarthMath.gpsToEcef(origin.getLat(), origin.getLon(), origin.getAlt());
        double[] offsetEcefVector = EarthMath.nedToEcef(offsetNed, origin.getLat(), origin.getLon());
        double[] gps = EarthMath.ecefToGps(
                originEcef[0] + offsetEcefVector[0],
                originEcef[1] + offsetEcefVector[1],
                originEcef[2] + offsetEcefVector[2]);
        return new GeoCoordinate(gps[0], gps[1], gps[2]);
    }

    /**
     * 向量相减
     *
     * @param a 被减向量
     * @param b 减向量
     * @return a-b
     */
    private static double[] subtract(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    /**
     * 向量点乘
     *
     * @param a 向量a
     * @param b 向量b
     * @return 点乘结果
     */
    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
}
