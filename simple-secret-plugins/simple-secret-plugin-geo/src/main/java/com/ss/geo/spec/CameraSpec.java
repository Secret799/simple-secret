package com.ss.geo.spec;

/**
 * 单个相机规格。FOV 与等效焦距对应 {@link #baselineZoomFactor()} 返回的绝对基准倍率。
 *
 * @param type                 相机类型
 * @param diagonalFov          基准倍率下的对角线视场角（度）
 * @param equivFocalLength     基准倍率下的等效焦距（mm）
 * @param sensorWidth          传感器物理宽度（mm）
 * @param sensorHeight         传感器物理高度（mm）
 * @param maxImageWidth        最大图像宽度（px）
 * @param maxImageHeight       最大图像高度（px）
 * @param aperture             光圈值
 * @param hasMechanicalShutter 是否有机械快门
 * @param minZoom              最小变焦倍率
 * @param maxZoom              最大变焦倍率
 *
 * @author JunPzx
 * @since 2026/5/11
 */
public record CameraSpec(
        CameraType type,
        double diagonalFov,
        double equivFocalLength,
        double sensorWidth,
        double sensorHeight,
        int maxImageWidth,
        int maxImageHeight,
        String aperture,
        boolean hasMechanicalShutter,
        double minZoom,
        double maxZoom) {

    /** 传感器对角线（mm） */
    public double sensorDiagonal() {
        return Math.hypot(sensorWidth, sensorHeight);
    }

    /**
     * 根据 zoom_factor 计算当前对角线视场角
     *
     * @param zoomFactor DJI 绝对变焦倍率，必须位于规格允许范围
     * @return 当前对角线视场角（度）
     */
    public double diagonalFovAtZoom(double zoomFactor) {
        double effectiveZoom = normalizeZoomFactor(zoomFactor);
        if (effectiveZoom <= 1.0) {
            return diagonalFov;
        }
        double halfFov = Math.toRadians(diagonalFov / 2);
        return 2 * Math.toDegrees(Math.atan(Math.tan(halfFov) / effectiveZoom));
    }

    /**
     * 根据 zoom_factor 计算当前水平和垂直视场角
     *
     * @param zoomFactor DJI 绝对变焦倍率，必须位于规格允许范围
     * @return [水平FOV（度）, 垂直FOV（度）]
     */
    public double[] fovHvAtZoom(double zoomFactor) {
        return fovHvAtZoom(zoomFactor, maxImageWidth, maxImageHeight);
    }

    /**
     * 根据变焦倍率和实际输出画幅计算水平、垂直视场角。
     *
     * <p>当输出画幅比例与传感器比例不一致时，按居中裁切计算有效传感器区域，
     * 保证由结果构建的水平、垂直像素焦距一致。
     *
     * @param zoomFactor DJI 绝对变焦倍率，必须位于规格允许范围
     * @param frameWidth 实际画面宽度
     * @param frameHeight 实际画面高度
     * @return [水平FOV（度）, 垂直FOV（度）]
     */
    public double[] fovHvAtZoom(double zoomFactor, int frameWidth, int frameHeight) {
        if (frameWidth <= 0 || frameHeight <= 0) {
            throw new IllegalArgumentException("frame width and frame height must be positive");
        }
        double effectiveZoom = normalizeZoomFactor(zoomFactor);
        double focalLength = sensorDiagonal()
                / (2.0 * Math.tan(Math.toRadians(diagonalFov) / 2.0))
                * Math.max(effectiveZoom, 1.0);
        double frameAspect = (double) frameWidth / frameHeight;
        double sensorAspect = sensorWidth / sensorHeight;
        double effectiveWidth;
        double effectiveHeight;
        if (frameAspect >= sensorAspect) {
            effectiveWidth = sensorWidth;
            effectiveHeight = sensorWidth / frameAspect;
        } else {
            effectiveHeight = sensorHeight;
            effectiveWidth = sensorHeight * frameAspect;
        }
        double fovH = 2 * Math.toDegrees(Math.atan(effectiveWidth / (2.0 * focalLength)));
        double fovV = 2 * Math.toDegrees(Math.atan(effectiveHeight / (2.0 * focalLength)));
        return new double[]{fovH, fovV};
    }

    /** 返回该规格的绝对基准倍率。 */
    public double baselineZoomFactor() {
        return Math.max(minZoom, 1.0);
    }

    private double normalizeZoomFactor(double zoomFactor) {
        if (!Double.isFinite(zoomFactor) || zoomFactor <= 0.0) {
            throw new IllegalArgumentException("zoom factor must be a finite positive number");
        }
        double baseline = baselineZoomFactor();
        double maximum = maxZoom > 0.0 ? maxZoom : baseline;
        if (zoomFactor < baseline || zoomFactor > maximum) {
            throw new IllegalArgumentException(
                    "zoom factor must be within [" + baseline + ", " + maximum + "]");
        }
        return zoomFactor / baseline;
    }
}
