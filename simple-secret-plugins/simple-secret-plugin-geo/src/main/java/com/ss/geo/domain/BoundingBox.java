package com.ss.geo.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 图片中的矩形检测框。
 */
public class BoundingBox implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private double x;
    private double y;
    private double width;
    private double height;
    private String label;
    private Double confidence;

    /** 创建空检测框。 */
    public BoundingBox() {
    }

    /** 创建检测框。 */
    public BoundingBox(double x, double y, double width, double height, String label, Double confidence) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.label = label;
        this.confidence = confidence;
    }

    /** 返回左上角 X。 */
    public double getX() { return x; }

    /** 设置左上角 X。 */
    public BoundingBox setX(double x) { this.x = x; return this; }

    /** 返回左上角 Y。 */
    public double getY() { return y; }

    /** 设置左上角 Y。 */
    public BoundingBox setY(double y) { this.y = y; return this; }

    /** 返回宽度。 */
    public double getWidth() { return width; }

    /** 设置宽度。 */
    public BoundingBox setWidth(double width) { this.width = width; return this; }

    /** 返回高度。 */
    public double getHeight() { return height; }

    /** 设置高度。 */
    public BoundingBox setHeight(double height) { this.height = height; return this; }

    /** 返回标签。 */
    public String getLabel() { return label; }

    /** 设置标签。 */
    public BoundingBox setLabel(String label) { this.label = label; return this; }

    /** 返回置信度。 */
    public Double getConfidence() { return confidence; }

    /** 设置置信度。 */
    public BoundingBox setConfidence(Double confidence) { this.confidence = confidence; return this; }

    /** 返回检测框中心 X。 */
    public double centerX() { return x + width / 2.0; }

    /** 返回检测框中心 Y。 */
    public double centerY() { return y + height / 2.0; }
}
