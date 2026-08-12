package com.ss.ics.dahua;

import java.time.LocalDateTime;
import java.util.List;

/** 历史热成像测温记录。 */
public record DahuaRadiometryRecord(
        LocalDateTime timestamp,
        int presetId,
        int ruleId,
        String name,
        int channel,
        DahuaTemperatureSummary temperature,
        List<DahuaPoint> coordinates) {
    /**
     * 隔离坐标集合所有权。
     *
     * @param timestamp 消息时间戳
     * @param presetId 预置点编号
     * @param ruleId 测温规则编号
     * @param name 名称
     * @param channel 通道
     * @param temperature 温度统计结果
     * @param coordinates 坐标点列表
     */
    public DahuaRadiometryRecord {
        if (timestamp == null || temperature == null || coordinates == null) {
            throw new IllegalArgumentException("radiometry record fields must not be null");
        }
        coordinates = List.copyOf(coordinates);
    }
}
