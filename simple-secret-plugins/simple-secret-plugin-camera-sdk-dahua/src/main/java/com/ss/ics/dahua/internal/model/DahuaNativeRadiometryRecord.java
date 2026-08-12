package com.ss.ics.dahua.internal.model;

import com.ss.ics.dahua.DahuaPoint;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 原生历史热成像记录快照。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public record DahuaNativeRadiometryRecord(
        LocalDateTime timestamp, int presetId, int ruleId, String name, int channel,
        DahuaNativeTemperatureSummary temperature, List<DahuaPoint> coordinates) {
    /**
     * 创建不可变的历史热成像记录快照。
     *
     * @param timestamp 记录时间
     * @param presetId 预置点编号
     * @param ruleId 规则编号
     * @param name 规则名称
     * @param channel 原生通道号
     * @param temperature 温度统计
     * @param coordinates 区域坐标
     */
    public DahuaNativeRadiometryRecord {
        coordinates = List.copyOf(coordinates);
    }
}
