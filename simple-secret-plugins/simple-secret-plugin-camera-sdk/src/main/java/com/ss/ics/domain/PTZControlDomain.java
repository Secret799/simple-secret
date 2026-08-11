package com.ss.ics.domain;

import com.ss.ics.constants.enums.PtzControlCommandEnums;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;

/** 云台控制参数。 */
public class PTZControlDomain implements Serializable {
    /** 默认速度等级。 */
    public static final int DEFAULT_SPEED_LEVEL = 5;
    /** 最小速度等级。 */
    public static final int MIN_SPEED_LEVEL = 1;
    /** 最大速度等级。 */
    public static final int MAX_SPEED_LEVEL = 10;

    @Serial
    private static final long serialVersionUID = 1L;

    private PtzControlCommandEnums command;
    private Boolean isBegin;
    private Duration duration;
    private Integer speedLevel;

    /** @return 云台命令 */
    public PtzControlCommandEnums getCommand() {
        return command;
    }

    /** @param command 云台命令 @return 当前对象 */
    public PTZControlDomain setCommand(PtzControlCommandEnums command) {
        this.command = command;
        return this;
    }

    /** @return 是否为开始命令 */
    public Boolean getIsBegin() {
        return isBegin;
    }

    /** @param isBegin 是否为开始命令 @return 当前对象 */
    public PTZControlDomain setIsBegin(Boolean isBegin) {
        this.isBegin = isBegin;
        return this;
    }

    /** @return 命令持续时长 */
    public Duration getDuration() {
        return duration;
    }

    /** @param duration 命令持续时长 @return 当前对象 */
    public PTZControlDomain setDuration(Duration duration) {
        this.duration = duration;
        return this;
    }

    /** @return 限制在 1 到 10 的速度等级 */
    public Integer getSpeedLevel() {
        if (speedLevel == null) {
            return DEFAULT_SPEED_LEVEL;
        }
        return Math.max(MIN_SPEED_LEVEL, Math.min(MAX_SPEED_LEVEL, speedLevel));
    }

    /** @param speedLevel 速度等级 @return 当前对象 */
    public PTZControlDomain setSpeedLevel(Integer speedLevel) {
        this.speedLevel = speedLevel;
        return this;
    }

    /**
     * 将 1 到 10 的抽象速度线性映射到厂商速度区间。
     *
     * @param min 厂商最小速度
     * @param max 厂商最大速度
     * @return 映射后的速度
     */
    public Integer getSpeed(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min must not exceed max");
        }
        double scale = (getSpeedLevel() - MIN_SPEED_LEVEL)
                / (double) (MAX_SPEED_LEVEL - MIN_SPEED_LEVEL);
        return (int) Math.round(min + scale * (max - min));
    }
}
