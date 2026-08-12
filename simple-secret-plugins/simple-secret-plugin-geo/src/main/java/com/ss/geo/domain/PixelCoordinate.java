package com.ss.geo.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 图片像素坐标，原点位于画面左上角，X 向右、Y 向下。
 */
public class PixelCoordinate implements Serializable {

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

    /** 创建空坐标。 */
    public PixelCoordinate() {
    }

    /**
     * 创建像素坐标。
     *
     * @param x 横坐标
     * @param y 纵坐标
     */
    public PixelCoordinate(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /**
     * 返回横坐标。
     *
     * @return 像素横坐标
     */
    public double getX() {
        return x;
    }

    /**
     * 设置横坐标。
     *
     * @param x 像素横坐标
     * @return 当前对象
     */
    public PixelCoordinate setX(double x) {
        this.x = x;
        return this;
    }

    /**
     * 返回纵坐标。
     *
     * @return 像素纵坐标
     */
    public double getY() {
        return y;
    }

    /**
     * 设置纵坐标。
     *
     * @param y 像素纵坐标
     * @return 当前对象
     */
    public PixelCoordinate setY(double y) {
        this.y = y;
        return this;
    }
}
