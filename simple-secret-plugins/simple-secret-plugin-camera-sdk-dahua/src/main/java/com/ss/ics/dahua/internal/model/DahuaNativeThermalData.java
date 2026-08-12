package com.ss.ics.dahua.internal.model;

import java.time.LocalDateTime;

/**
 * 原生热图解析结果的驱动内部快照。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public record DahuaNativeThermalData(
        LocalDateTime timestamp, int width, int height,
        short[] grayscale, float[] temperatures) {
    /**
     * 创建与原生内存分离的热图快照。
     *
     * @param timestamp 采集时间
     * @param width 热图宽度
     * @param height 热图高度
     * @param grayscale 灰度数据
     * @param temperatures 温度数据
     */
    public DahuaNativeThermalData {
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
