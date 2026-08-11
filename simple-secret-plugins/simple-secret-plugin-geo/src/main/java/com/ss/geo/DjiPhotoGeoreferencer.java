package com.ss.geo;

import com.ss.geo.domain.*;
import com.ss.geo.photo.DjiMetadataReader;
import com.ss.geo.photo.DjiPhotoMetadata;
import com.ss.geo.spec.CameraSpec;
import com.ss.geo.spec.CameraType;
import com.ss.geo.spec.DjiDroneModel;
import com.ss.geo.spec.DjiProjectionContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * DJI 照片坐标参照：照片 + 推理框 → 地理坐标
 *
 * <p>自动从照片 EXIF/XMP 中提取相机内外参，结合推理框像素坐标计算目标真实 GPS。
 * <p>FOV 计算优先级：CalibratedFocalLength（仅显式 DJI XMP 直接读取，MakerNote 刻意忽略）{@code >}
 * FocalPlaneXResolution（EXIF 直接读取 + 物理焦距换算）{@code >}
 * 物理焦距 + 传感器宽度 {@code >} 35mm 等效焦距 {@code >} DJI 规格 FOV {@code >} 默认 FOV。
 *
 * @author junpzx
 * @since 2026/5/2
 */
public final class DjiPhotoGeoreferencer {

    /**
     * 35mm 标准胶片对角线长度（mm）
     */
    // 43.27mm
    private static final double FILM_DIAGONAL_35MM = Math.sqrt(36.0 * 36.0 + 24.0 * 24.0);

    /** 35mm 标准胶片宽度（mm） */
    private static final double FILM_WIDTH_35MM = 36.0;

    /**
     * 默认水平视场角（度）
     */
    private static final double DEFAULT_FOV_H = 84.0;
    /**
     * 默认垂直视场角（度）
     */
    private static final double DEFAULT_FOV_V = 53.0;

    private DjiPhotoGeoreferencer() {
    }

    /**
     * 从 DJI 照片读取元数据并定位单个像素。
     */
    public static GeoTarget refer(Path photoPath, double x, double y, double groundAltitude) {
        return refer(DjiMetadataReader.read(photoPath), x, y, groundAltitude);
    }

    /**
     * 从 DJI 照片读取元数据，并通过 DEM 回调迭代查询地面海拔。
     */
    public static GeoTarget refer(Path photoPath, double x, double y,
                                  Function<double[], Double> demQuery, double fallbackAltitude) {
        CameraState state = buildCameraState(DjiMetadataReader.read(photoPath));
        return GeoReferencer.pixelToGeo(new PixelCoordinate(x, y), state, demQuery, fallbackAltitude);
    }

    /**
     * 根据 DJI 照片元数据 + 推理框中心坐标计算目标地理坐标（避免重复读取照片元数据）
     *
     * @param metadata       已读取的照片元数据
     * @param boxCenterX     推理框中心 X 像素坐标
     * @param boxCenterY     推理框中心 Y 像素坐标
     * @param groundAltitude 目标地面海拔（米）
     * @return 目标地理坐标
     */
    public static GeoTarget refer(DjiPhotoMetadata metadata, double boxCenterX, double boxCenterY, double groundAltitude) {
        CameraState state = buildCameraState(metadata);
        return GeoReferencer.pixelToGeo(new PixelCoordinate(boxCenterX, boxCenterY), state, groundAltitude);
    }

    /**
     * 根据 DJI 照片元数据 + 投影上下文 + 推理框中心坐标计算目标地理坐标。
     *
     * @param metadata       已读取的照片元数据
     * @param context        DJI 投影上下文
     * @param boxCenterX     推理框中心 X 像素坐标
     * @param boxCenterY     推理框中心 Y 像素坐标
     * @param groundAltitude 目标地面海拔（米）
     * @return 目标地理坐标
     */
    public static GeoTarget refer(DjiPhotoMetadata metadata, DjiProjectionContext context,
                                  double boxCenterX, double boxCenterY, double groundAltitude) {
        CameraState state = buildCameraState(metadata, context);
        return GeoReferencer.pixelToGeo(new PixelCoordinate(boxCenterX, boxCenterY), state, groundAltitude);
    }

    /**
     * 根据 DJI 照片元数据构建 CameraState
     * <p>FOV 始终优先使用图片中直接读取的数据，DJI 规格仅作为回退：
     * <ol>
     *   <li>CalibratedFocalLength（仅显式 DJI XMP 直接提供，MakerNote 不参与）</li>
     *   <li>FocalPlaneXResolution + 物理焦距（EXIF 直接提供）</li>
     *   <li>物理焦距 + 已识别镜头的传感器宽度</li>
     *   <li>35mm 等效焦距 + 图片尺寸 + 标准胶片对角线</li>
     *   <li>DJI 规格 FOV</li>
     * </ol>
     *
     * @param meta DJI 照片元数据
     * @return 相机状态
     */
    public static CameraState buildCameraState(DjiPhotoMetadata meta) {
        DjiProjectionContext context = buildContextFromMetadata(meta);
        return buildCameraState(meta, context);
    }

    /**
     * 根据 DJI 照片元数据和投影上下文构建 CameraState。
     *
     * @param meta    DJI 照片元数据
     * @param context DJI 投影上下文
     * @return 相机状态
     */
    public static CameraState buildCameraState(DjiPhotoMetadata meta, DjiProjectionContext context) {
        if (meta == null) {
            throw new IllegalArgumentException("DJI photo metadata must not be null");
        }
        requireAbsolutePose(meta);
        requirePositiveFrameSize(meta.getImageWidth(), meta.getImageHeight(), "DJI photo");
        double[] fov = computeFov(meta, context);

        double lat = meta.getGpsLat();
        double lon = meta.getGpsLon();
        double alt = meta.getGpsAlt();

        CameraState state = new CameraState()
                .setLat(lat)
                .setLon(lon)
                .setAlt(alt)
                .setGimbalYaw(resolveCameraYaw(meta))
                .setGimbalPitch(resolveCameraPitch(meta))
                .setGimbalRoll(resolveCameraRoll(meta))
                .setFovH(fov[0])
                .setFovV(fov[1])
                .setFrameWidth(meta.getImageWidth())
                .setFrameHeight(meta.getImageHeight());
        GeoReferencer.validateCameraState(state);
        return state;
    }

    private static void requireAbsolutePose(DjiPhotoMetadata meta) {
        if (!isFinite(meta.getGpsLat()) || !isFinite(meta.getGpsLon())
                || meta.getGpsLat() < -90 || meta.getGpsLat() > 90
                || meta.getGpsLon() < -180 || meta.getGpsLon() > 180) {
            throw new IllegalArgumentException("DJI photo requires valid WGS84 latitude and longitude");
        }
        if (!isFinite(meta.getGpsAlt())) {
            throw new IllegalArgumentException(
                    "DJI photo requires absolute altitude; relative altitude cannot be used as WGS84 altitude");
        }
    }

    private static void requirePositiveFrameSize(int width, int height, String source) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(source + " frame width and frame height must be positive");
        }
    }

    /**
     * 解析相机投影使用的水平朝向。
     *
     * <p>DJI 照片在云台正下视（约 -90°）时，yaw/roll 语义会耦合。
     * 例如 M3TD 正射图常见 {@code GimbalYawDegree ≈ FlightYawDegree + 180°} 且
     * {@code GimbalRollDegree = 180°}；此时投影 yaw 使用飞机航向，同时继续保留
     * 云台 roll 参与旋转，才能还原图片上/下/左/右方向。正下视但未翻转时使用
     * 有效的绝对云台 yaw；其他场景优先云台 yaw，缺失时回退飞机 yaw。
     */
    private static double resolveCameraYaw(DjiPhotoMetadata meta) {
        Double gimbalYaw = meta.getGimbalYaw();
        Double gimbalPitch = meta.getGimbalPitch();
        Double gimbalRoll = meta.getGimbalRoll();
        Double flightYaw = meta.getFlightYaw();

        if (isFinite(gimbalPitch) && Math.abs(gimbalPitch + 90.0) < 1.0) {
            if (isFinite(gimbalRoll) && Math.abs(Math.abs(gimbalRoll) - 180.0) < 1.0) {
                return requireFiniteAttitude(flightYaw, "yaw");
            }
            if (isFinite(gimbalRoll) && Math.abs(gimbalRoll) < 1.0 && isFinite(gimbalYaw)) {
                return gimbalYaw;
            }
            return requireFiniteAttitude(flightYaw, "yaw");
        }
        return isFinite(gimbalYaw) ? gimbalYaw : requireFiniteAttitude(flightYaw, "yaw");
    }

    private static double resolveCameraPitch(DjiPhotoMetadata meta) {
        return isFinite(meta.getGimbalPitch())
                ? meta.getGimbalPitch() : requireFiniteAttitude(meta.getFlightPitch(), "pitch");
    }

    private static double resolveCameraRoll(DjiPhotoMetadata meta) {
        return isFinite(meta.getGimbalRoll())
                ? meta.getGimbalRoll() : requireFiniteAttitude(meta.getFlightRoll(), "roll");
    }

    private static boolean isFinite(Double value) {
        return value != null && Double.isFinite(value);
    }

    private static double requireFiniteAttitude(Double value, String angle) {
        if (!isFinite(value)) {
            throw new IllegalArgumentException("DJI photo camera attitude " + angle + " is required and must be finite");
        }
        return value;
    }

    /**
     * 计算视场角（FOV），优先级依次递减，所有数据均来自图片本身。
     *
     * @param meta DJI 照片元数据
     * @return [fovH, fovV]（度）
     */
    private static double[] computeFov(DjiPhotoMetadata meta, DjiProjectionContext context) {
        int w = meta.getImageWidth();
        int h = meta.getImageHeight();
        double digitalZoomRatio = positiveFiniteOrDefault(meta.getDigitalZoomRatio(), 1.0);

        // 1) CalibratedFocalLength 仅直接从显式 DJI XMP 读取
        Double cf = meta.getCalibratedFocalLength();
        if (cf != null && cf > 0) {
            double fovH = 2.0 * Math.toDegrees(Math.atan(w / (2.0 * cf)));
            double fovV = 2.0 * Math.toDegrees(Math.atan(h / (2.0 * cf)));
            return new double[]{fovH, fovV};
        }

        // 2) FocalPlaneXResolution + 物理焦距（直接来自 EXIF）
        Double fl = meta.getFocalLength();
        Double fpXRes = meta.getFocalPlaneXResolution();
        if (isPositiveFinite(fl) && isPositiveFinite(fpXRes)) {
            Double unitMm = millimetersPerResolutionUnit(meta.getFocalPlaneResolutionUnit());
            if (unitMm != null) {
                // 直接换算：focalLength(mm) × resolution(px/unit) / unit(mm) → 像素焦距
                double calibPx = fl * fpXRes / unitMm * digitalZoomRatio;
                double fovH = 2.0 * Math.toDegrees(Math.atan(w / (2.0 * calibPx)));
                double fovV = 2.0 * Math.toDegrees(Math.atan(h / (2.0 * calibPx)));
                return new double[]{fovH, fovV};
            }
        }

        Double fl35 = meta.getFocalLength35mm();
        if (digitalZoomRatio != 1.0 && fl35 != null && fl35 > 0 && Double.isFinite(fl35)) {
            double focalPx = fl35 * w / FILM_WIDTH_35MM * digitalZoomRatio;
            double fovH = 2.0 * Math.toDegrees(Math.atan(w / (2.0 * focalPx)));
            double fovV = 2.0 * Math.toDegrees(Math.atan(h / (2.0 * focalPx)));
            return new double[]{fovH, fovV};
        }

        // 3) 物理焦距 + 已识别镜头的传感器宽度
        CameraSpec cameraSpec = resolveCameraSpec(context);
        if (fl != null && fl > 0 && cameraSpec != null && cameraSpec.sensorWidth() > 0) {
            double focalPx = fl * w / cameraSpec.sensorWidth() * digitalZoomRatio;
            double fovH = 2.0 * Math.toDegrees(Math.atan(w / (2.0 * focalPx)));
            double fovV = 2.0 * Math.toDegrees(Math.atan(h / (2.0 * focalPx)));
            return new double[]{fovH, fovV};
        }

        // 4) 35mm 等效焦距（直接来自 EXIF）+ 标准胶片对角线
        if (fl35 != null && fl35 > 0) {
            double imageDiagonal = Math.sqrt((double) w * w + (double) h * h);
            double diagonalFov = 2.0 * Math.toDegrees(Math.atan(FILM_DIAGONAL_35MM / (2.0 * fl35)));
            double halfDiagonalFovRad = Math.toRadians(diagonalFov / 2.0);
            double fovH = 2.0 * Math.toDegrees(Math.atan(Math.tan(halfDiagonalFovRad) * w / imageDiagonal));
            double fovV = 2.0 * Math.toDegrees(Math.atan(Math.tan(halfDiagonalFovRad) * h / imageDiagonal));
            return new double[]{fovH, fovV};
        }

        double[] specFov = computeFovFromDjiSpec(context, w, h);
        if (specFov != null) {
            return specFov;
        }

        // 无可用焦距数据，使用默认值
        return new double[]{DEFAULT_FOV_H, DEFAULT_FOV_V};
    }

    private static double positiveFiniteOrDefault(Double value, double fallback) {
        return value != null && value > 0.0 && Double.isFinite(value) ? value : fallback;
    }

    private static boolean isPositiveFinite(Double value) {
        return value != null && value > 0.0 && Double.isFinite(value);
    }

    private static Double millimetersPerResolutionUnit(Integer unit) {
        if (unit == null) {
            return null;
        }
        return switch (unit) {
            case 2 -> 25.4;
            case 3 -> 10.0;
            case 4 -> 1.0;
            case 5 -> 0.001;
            default -> null;
        };
    }

    private static double[] computeFovFromDjiSpec(DjiProjectionContext context, int frameWidth, int frameHeight) {
        CameraSpec spec = resolveCameraSpec(context);
        if (spec == null) {
            return null;
        }
        double zoomFactor = context.getZoomFactor() != null
                ? context.getZoomFactor() : spec.baselineZoomFactor();
        return spec.fovHvAtZoom(zoomFactor, frameWidth, frameHeight);
    }

    private static CameraSpec resolveCameraSpec(DjiProjectionContext context) {
        if (context == null || context.getDroneModel() == null || context.getCameraType() == null) {
            return null;
        }
        return context.getDroneModel().getSpec(context.getCameraType());
    }

    private static DjiProjectionContext buildContextFromMetadata(DjiPhotoMetadata meta) {
        DjiProjectionContext context = new DjiProjectionContext()
                .setCameraType(resolveCameraType(meta.getImageSource()))
                .setZoomFactor(meta.getZoomFactor());
        DjiDroneModel droneModel = null;
        if (meta.getDroneTypeCode() != null && !meta.getDroneTypeCode().isBlank()) {
            try {
                droneModel = DjiDroneModel.getByTypeCode(meta.getDroneTypeCode());
            } catch (IllegalArgumentException ignored) {
                // 未识别机型时仍可使用 EXIF/XMP 焦距路径。
            }
        }
        if (droneModel == null) {
            droneModel = DjiDroneModel.findByName(meta.getDroneModel());
        }
        if (droneModel == null) {
            droneModel = DjiDroneModel.findByName(meta.getModel());
        }
        if (droneModel == null) {
            droneModel = DjiDroneModel.findByName(meta.getProductName());
        }
        context.setDroneModel(droneModel);
        return context;
    }

    private static CameraType resolveCameraType(String imageSource) {
        if (imageSource == null) {
            return null;
        }
        String normalized = imageSource.trim()
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .toUpperCase(Locale.ROOT);
        if (hasToken(normalized, "MEDIUM") && hasToken(normalized, "TELE")) {
            return CameraType.MEDIUM_TELE;
        }
        if (hasToken(normalized, "ZOOM") || hasToken(normalized, "TELE")) {
            return CameraType.ZOOM;
        }
        if (hasToken(normalized, "WIDE")) {
            return CameraType.WIDE;
        }
        if (hasToken(normalized, "THERMAL") || hasToken(normalized, "INFRARED")
                || hasToken(normalized, "IR")) {
            return CameraType.THERMAL;
        }
        return null;
    }

    private static boolean hasToken(String normalized, String token) {
        return ("_" + normalized + "_").contains("_" + token + "_");
    }

    // === 批量接口：多框 → 多坐标（照片元数据只读一次） ===

    /**
     * 从 DJI 照片读取元数据并定位多个检测框。
     */
    public static List<GeoTargetWithBox> refer(Path photoPath, List<BoundingBox> boxes, double groundAltitude) {
        return refer(DjiMetadataReader.read(photoPath), boxes, groundAltitude);
    }

    /**
     * 从 DJI 照片读取元数据，并通过 DEM 回调定位多个检测框。
     */
    public static List<GeoTargetWithBox> refer(Path photoPath, List<BoundingBox> boxes,
                                               Function<double[], Double> demQuery, double fallbackAltitude) {
        CameraState state = buildCameraState(DjiMetadataReader.read(photoPath));
        return GeoReferencer.boxesToGeo(boxes, state, demQuery, fallbackAltitude);
    }

    /**
     * 照片元数据 + 多推理框 → 地理坐标列表（避免重复读取）
     *
     * @param meta           已读取的照片元数据
     * @param boxes          推理框列表
     * @param groundAltitude 目标地面海拔（米）
     * @return 每个框的地理坐标结果（含原始框信息）
     */
    public static List<GeoTargetWithBox> refer(DjiPhotoMetadata meta, List<BoundingBox> boxes, double groundAltitude) {
        CameraState state = buildCameraState(meta);
        return GeoReferencer.boxesToGeo(boxes, state, groundAltitude);
    }

    /**
     * 照片元数据 + 投影上下文 + 多推理框 → 地理坐标列表。
     */
    public static List<GeoTargetWithBox> refer(DjiPhotoMetadata meta, DjiProjectionContext context,
                                               List<BoundingBox> boxes, double groundAltitude) {
        CameraState state = buildCameraState(meta, context);
        return GeoReferencer.boxesToGeo(boxes, state, groundAltitude);
    }

    /**
     * 照片元数据 + 管线 → 像素投影
     *
     * @param meta         已读取的照片元数据
     * @param name         管线名称
     * @param pipeline     管线地理点集
     * @param bufferMeters 管线缓冲半径（米），小于等于0时只输出中心线
     * @return 管线像素投影结果
     */
    public static PipelineProjection referPipeline(DjiPhotoMetadata meta, String name,
                                                   List<GeoCoordinate> pipeline, double bufferMeters) {
        CameraState state = buildCameraState(meta);
        return GeoReferencer.pipelineToPixels(name, pipeline, state, bufferMeters);
    }

    /**
     * 从 DJI 照片读取元数据并把地理管线投影到画面。
     */
    public static PipelineProjection referPipeline(Path photoPath, String name,
                                                    List<GeoCoordinate> pipeline, double bufferMeters) {
        return referPipeline(DjiMetadataReader.read(photoPath), name, pipeline, bufferMeters);
    }

    /**
     * 照片元数据 + 投影上下文 + 管线 → 像素投影。
     */
    public static PipelineProjection referPipeline(DjiPhotoMetadata meta, DjiProjectionContext context, String name,
                                                   List<GeoCoordinate> pipeline, double bufferMeters) {
        CameraState state = buildCameraState(meta, context);
        return GeoReferencer.pipelineToPixels(name, pipeline, state, bufferMeters);
    }

    /**
     * 照片元数据 + 多条管线 → 像素投影
     *
     * @param meta         已读取的照片元数据
     * @param pipelines    管线列表
     * @param bufferMeters 管线缓冲半径（米），小于等于0时只输出中心线
     * @return 管线像素投影结果列表
     */
    public static List<PipelineProjection> referPipelines(DjiPhotoMetadata meta,
                                                          List<GeoPipeline> pipelines, double bufferMeters) {
        CameraState state = buildCameraState(meta);
        List<PipelineProjection> results = new ArrayList<>(pipelines.size());
        for (GeoPipeline pipeline : pipelines) {
            results.add(GeoReferencer.pipelineToPixels(
                    pipeline.getName(), pipeline.getPoints(), state, bufferMeters));
        }
        return results;
    }

}
