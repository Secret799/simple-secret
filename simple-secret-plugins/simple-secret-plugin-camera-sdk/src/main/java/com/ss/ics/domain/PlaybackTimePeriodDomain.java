package com.ss.ics.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 某天的历史录像存在状态和时间段。 */
public class PlaybackTimePeriodDomain implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private LocalDate date;
    private boolean existRecord;
    private List<TimePeriod> timePeriods;

    /** @return 日期 */
    public LocalDate getDate() {
        return date;
    }

    /** @param date 日期 @return 当前对象 */
    public PlaybackTimePeriodDomain setDate(LocalDate date) {
        this.date = date;
        return this;
    }

    /** @return 当天是否存在录像 */
    public boolean isExistRecord() {
        return existRecord;
    }

    /** @return 当天是否存在录像 */
    public boolean getExistRecord() {
        return existRecord;
    }

    /** @param existRecord 当天是否存在录像 @return 当前对象 */
    public PlaybackTimePeriodDomain setExistRecord(boolean existRecord) {
        this.existRecord = existRecord;
        return this;
    }

    /** @return 可变时间段列表，便于厂商查询逐条填充 */
    public List<TimePeriod> getTimePeriods() {
        return timePeriods;
    }

    /** @param timePeriods 时间段列表 @return 当前对象 */
    public PlaybackTimePeriodDomain setTimePeriods(List<TimePeriod> timePeriods) {
        this.timePeriods = timePeriods;
        return this;
    }

    /** 单个录像时间段。 */
    public static class TimePeriod implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private LocalTime beginTime;
        private LocalTime endTime;

        /** @return 开始时间 */
        public LocalTime getBeginTime() {
            return beginTime;
        }

        /** @param beginTime 开始时间 @return 当前对象 */
        public TimePeriod setBeginTime(LocalTime beginTime) {
            this.beginTime = beginTime;
            return this;
        }

        /** @return 结束时间 */
        public LocalTime getEndTime() {
            return endTime;
        }

        /** @param endTime 结束时间 @return 当前对象 */
        public TimePeriod setEndTime(LocalTime endTime) {
            this.endTime = endTime;
            return this;
        }
    }
}
