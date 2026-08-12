package com.ss.geo.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 图片中的矩形检测框。
 */
public class BoundingBox implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 像素横坐标。
     */
    private double x;
    /**
     * 像素纵坐标。
     */
    private double y;
    /**
     * 宽度。
     */
    private double width;
    /**
     * 高度。
     */
    private double height;
    /**
     * 检测类别标签。
     */
    private String label;
    /**
     * 检测置信度。
     */
    private Double confidence;

    /** 创建空检测框。 */
    public BoundingBox() {
    }

    /**
     * 创建检测框。
     *
     * @param x 像素横坐标
     * @param y 像素纵坐标
     * @param width 宽度
     * @param height 高度
     * @param label 显示标签
     * @param confidence 检测置信度
     */
    public BoundingBox(double x, double y, double width, double height, String label, Double confidence) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.label = label;
        this.confidence = confidence;
    }

    /**
     * 返回左上角 X。
     *
     * @return 像素横坐标
     */
    public double getX() { return x; }

    /**
     * 设置左上角 X。
     *
     * @param x 像素横坐标
     * @return 当前对象
     */
    public BoundingBox setX(double x) { this.x = x; return this; }

    /**
     * 返回左上角 Y。
     *
     * @return 像素纵坐标
     */
    public double getY() { return y; }

    /**
     * 设置左上角 Y。
     *
     * @param y 像素纵坐标
     * @return 当前对象
     */
    public BoundingBox setY(double y) { this.y = y; return this; }

    /**
     * 返回宽度。
     *
     * @return 宽度
     */
    public double getWidth() { return width; }

    /**
     * 设置宽度。
     *
     * @param width 宽度
     * @return 当前对象
     */
    public BoundingBox setWidth(double width) { this.width = width; return this; }

    /**
     * 返回高度。
     *
     * @return 高度
     */
    public double getHeight() { return height; }

    /**
     * 设置高度。
     *
     * @param height 高度
     * @return 当前对象
     */
    public BoundingBox setHeight(double height) { this.height = height; return this; }

    /**
     * 返回标签。
     *
     * @return 显示标签
     */
    public String getLabel() { return label; }

    /**
     * 设置标签。
     *
     * @param label 显示标签
     * @return 当前对象
     */
    public BoundingBox setLabel(String label) { this.label = label; return this; }

    /**
     * 返回置信度。
     *
     * @return 检测置信度
     */
    public Double getConfidence() { return confidence; }

    /**
     * 设置置信度。
     *
     * @param confidence 检测置信度
     * @return 当前对象
     */
    public BoundingBox setConfidence(Double confidence) { this.confidence = confidence; return this; }

    /**
     * 返回检测框中心 X。
     *
     * @return 返回的 {@code double} 结果
     */
    public double centerX() { return x + width / 2.0; }

    /**
     * 返回检测框中心 Y。
     *
     * @return 返回的 {@code double} 结果
     */
    public double centerY() { return y + height / 2.0; }
}
