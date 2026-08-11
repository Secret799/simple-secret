package com.ss.ics.dahua;

import java.time.LocalDateTime;

/** 原生热图解析结果的驱动内部快照。 */
record DahuaNativeThermalData(
        LocalDateTime timestamp, int width, int height,
        short[] grayscale, float[] temperatures) {
    DahuaNativeThermalData {
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
