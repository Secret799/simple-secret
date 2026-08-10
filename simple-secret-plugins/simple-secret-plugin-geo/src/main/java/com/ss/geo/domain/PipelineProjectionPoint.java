package com.ss.geo.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 单个管线地理点的像素投影结果。
 */
public class PipelineProjectionPoint implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private GeoCoordinate coordinate;
    private PixelCoordinate pixel;
    private boolean visible;
    private boolean insideFrame;

    /** 返回原始地理坐标。 */
    public GeoCoordinate getCoordinate() { return coordinate; }
    /** 设置原始地理坐标。 */
    public PipelineProjectionPoint setCoordinate(GeoCoordinate coordinate) { this.coordinate = coordinate; return this; }
    /** 返回像素坐标；目标位于相机后方时为 null。 */
    public PixelCoordinate getPixel() { return pixel; }
    /** 设置像素坐标。 */
    public PipelineProjectionPoint setPixel(PixelCoordinate pixel) { this.pixel = pixel; return this; }
    /** 返回目标是否位于相机前方。 */
    public boolean isVisible() { return visible; }
    /** 设置目标是否位于相机前方。 */
    public PipelineProjectionPoint setVisible(boolean visible) { this.visible = visible; return this; }
    /** 返回像素是否位于画面范围内。 */
    public boolean isInsideFrame() { return insideFrame; }
    /** 设置像素是否位于画面范围内。 */
    public PipelineProjectionPoint setInsideFrame(boolean insideFrame) { this.insideFrame = insideFrame; return this; }
}
