package com.ss.ics.dahua;

import java.time.LocalDateTime;

/** 已复制的热成像灰度与每像素温度数据。 */
public record DahuaThermalData(
        LocalDateTime timestamp,
        int width,
        int height,
        short[] grayscale,
        float[] temperatures) {
    /**
     * 校验尺寸并隔离数组所有权。
     *
     * @param timestamp 消息时间戳
     * @param width 宽度
     * @param height 高度
     * @param grayscale 热成像灰度数据
     * @param temperatures 温度矩阵数据
     */
    public DahuaThermalData {
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
        int pixels;
        try {
            pixels = Math.multiplyExact(width, height);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("thermal dimensions are too large", exception);
        }
        if (width <= 0 || height <= 0 || grayscale == null || temperatures == null
                || grayscale.length != pixels || temperatures.length != pixels) {
            throw new IllegalArgumentException("thermal arrays must match width * height");
        }
        grayscale = grayscale.clone();
        temperatures = temperatures.clone();
    }

    @Override
    public short[] grayscale() {
        return grayscale.clone();
    }

    @Override
    public float[] temperatures() {
        return temperatures.clone();
    }
}
