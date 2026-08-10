package com.ss.geo.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * 图片或视频帧尺寸。
 */
public class FrameSize implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private int width;
    private int height;

    /** 创建空尺寸。 */
    public FrameSize() {
    }

    /** 创建帧尺寸。 */
    public FrameSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /** 返回宽度。 */
    public int getWidth() { return width; }

    /** 设置宽度。 */
    public FrameSize setWidth(int width) { this.width = width; return this; }

    /** 返回高度。 */
    public int getHeight() { return height; }

    /** 设置高度。 */
    public FrameSize setHeight(int height) { this.height = height; return this; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FrameSize that)) return false;
        return width == that.width && height == that.height;
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height);
    }
}
