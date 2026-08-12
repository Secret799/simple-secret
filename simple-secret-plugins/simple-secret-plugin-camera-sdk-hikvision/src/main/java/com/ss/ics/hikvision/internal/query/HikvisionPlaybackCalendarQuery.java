package com.ss.ics.hikvision.internal.query;

import com.ss.ics.domain.PlaybackTimePeriodDomain;
import com.ss.ics.hikvision.HikvisionSdkException;
import com.ss.ics.hikvision.internal.HikvisionNativeApi;
import com.ss.ics.hikvision.internal.model.HikvisionFileSearchCondition;
import com.ss.ics.hikvision.internal.model.HikvisionFileSearchResult;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * 海康录像文件轮询和按日月历组装。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public final class HikvisionPlaybackCalendarQuery {
    private static final int MAX_RECORDING_PERIODS = 10_000;

    /** 原生 SDK 适配器。 */
    private final HikvisionNativeApi nativeApi;

    /** 单次月历查询的整体超时。 */
    private final Duration timeout;

    /**
     * 创建录像月历查询器。
     *
     * @param nativeApi 原生 SDK 适配器
     * @param timeout 整体查询超时
     */
    public HikvisionPlaybackCalendarQuery(HikvisionNativeApi nativeApi, Duration timeout) {
        this.nativeApi = nativeApi;
        this.timeout = timeout;
    }

    /**
     * 查询指定月份的录像并按自然日组装。
     *
     * @param userId 原生用户编号
     * @param channel 原生通道号
     * @param streamType 码流类型
     * @param targetMonth 目标月份
     * @return 包含目标月份每天录像时间段的月历
     */
    public List<PlaybackTimePeriodDomain> execute(
            int userId, int channel, int streamType, YearMonth targetMonth) {
        LocalDateTime monthStart = targetMonth.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = targetMonth.atEndOfMonth().atTime(23, 59, 59);
        HikvisionFileSearchCondition condition = new HikvisionFileSearchCondition(
                channel,
                streamType,
                monthStart,
                monthEnd,
                nativeTimeoutMillis(timeout));
        long startedAt = System.nanoTime();
        long findHandle = nativeApi.findFiles(userId, condition);
        if (findHandle < 0) {
            throw failure("Hikvision playback calendar query failed");
        }

        List<RecordingPeriod> recordings = new ArrayList<>();
        RuntimeException primaryFailure = null;
        try {
            ensureWithinDeadline(startedAt);
            while (true) {
                ensureWithinDeadline(startedAt);
                HikvisionFileSearchResult result = nativeApi.findNextFile(findHandle);
                ensureWithinDeadline(startedAt);
                if (result == null) {
                    throw new HikvisionSdkException(
                            "Hikvision playback calendar query failed",
                            HikvisionFileSearchResult.FILE_EXCEPTION);
                }
                if (result.status() == HikvisionFileSearchResult.SUCCESS) {
                    if (recordings.size() >= MAX_RECORDING_PERIODS) {
                        throw new HikvisionSdkException(
                                "Hikvision playback calendar result limit exceeded",
                                HikvisionFileSearchResult.FILE_EXCEPTION);
                    }
                    recordings.add(recordingPeriod(result));
                    continue;
                }
                if (result.status() == HikvisionFileSearchResult.FINDING) {
                    pauseUntilNextPoll(startedAt);
                    continue;
                }
                if (result.status() == HikvisionFileSearchResult.NO_MORE_FILE
                        || result.status() == HikvisionFileSearchResult.NO_FILE) {
                    break;
                }
                if (result.status() < 0) {
                    throw failure("Hikvision playback calendar query failed");
                }
                throw new HikvisionSdkException(
                        "Hikvision playback calendar query failed", result.status());
            }
        } catch (RuntimeException exception) {
            primaryFailure = exception;
            throw exception;
        } finally {
            try {
                if (!nativeApi.closeFind(findHandle)) {
                    throw failure("Hikvision playback calendar search close failed");
                }
            } catch (RuntimeException cleanupFailure) {
                suppressOrThrow(primaryFailure, cleanupFailure);
            }
        }
        return toCalendar(targetMonth, recordings);
    }

    private void ensureWithinDeadline(long startedAt) {
        long elapsed = System.nanoTime() - startedAt;
        if (elapsed >= timeoutNanos(timeout)) {
            throw new HikvisionSdkException(
                    "Hikvision playback calendar query timed out",
                    HikvisionFileSearchResult.FIND_TIMEOUT);
        }
    }

    private void pauseUntilNextPoll(long startedAt) {
        long elapsed = System.nanoTime() - startedAt;
        long remaining = timeoutNanos(timeout) - elapsed;
        LockSupport.parkNanos(Math.min(TimeUnit.MILLISECONDS.toNanos(10), remaining));
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException(
                    "Hikvision playback calendar query was interrupted");
        }
    }

    private HikvisionSdkException failure(String operation) {
        return new HikvisionSdkException(operation, nativeApi.lastError());
    }

    private static RecordingPeriod recordingPeriod(HikvisionFileSearchResult result) {
        if (result.startTime() == null || result.stopTime() == null
                || result.stopTime().isBefore(result.startTime())) {
            throw new HikvisionSdkException(
                    "Hikvision playback calendar returned invalid time range",
                    HikvisionFileSearchResult.FILE_EXCEPTION);
        }
        return new RecordingPeriod(result.startTime(), result.stopTime());
    }

    private static List<PlaybackTimePeriodDomain> toCalendar(
            YearMonth targetMonth, List<RecordingPeriod> recordings) {
        List<PlaybackTimePeriodDomain> calendar = new ArrayList<>(targetMonth.lengthOfMonth());
        for (int day = 1; day <= targetMonth.lengthOfMonth(); day++) {
            LocalDate date = targetMonth.atDay(day);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.atTime(23, 59, 59);
            List<PlaybackTimePeriodDomain.TimePeriod> periods = new ArrayList<>();
            for (RecordingPeriod recording : recordings) {
                LocalDateTime start = recording.start().isAfter(dayStart)
                        ? recording.start() : dayStart;
                LocalDateTime stop = recording.stop().isBefore(dayEnd)
                        ? recording.stop() : dayEnd;
                if (!stop.isBefore(start)) {
                    periods.add(new PlaybackTimePeriodDomain.TimePeriod()
                            .setBeginTime(start.toLocalTime().withNano(0))
                            .setEndTime(stop.toLocalTime().withNano(0)));
                }
            }
            calendar.add(new PlaybackTimePeriodDomain()
                    .setDate(date)
                    .setExistRecord(!periods.isEmpty())
                    .setTimePeriods(periods));
        }
        return calendar;
    }

    private static void suppressOrThrow(
            RuntimeException primaryFailure, RuntimeException cleanupFailure) {
        if (primaryFailure != null) {
            primaryFailure.addSuppressed(cleanupFailure);
            return;
        }
        throw cleanupFailure;
    }

    private static int nativeTimeoutMillis(Duration value) {
        if (value.compareTo(Duration.ofSeconds(5)) <= 0) {
            return 5_000;
        }
        if (value.compareTo(Duration.ofSeconds(15)) >= 0) {
            return 15_000;
        }
        return Math.toIntExact(value.toMillis());
    }

    private static long timeoutNanos(Duration value) {
        Duration maximum = Duration.ofNanos(Long.MAX_VALUE);
        return value.compareTo(maximum) >= 0 ? Long.MAX_VALUE : value.toNanos();
    }

    private record RecordingPeriod(LocalDateTime start, LocalDateTime stop) {
    }
}
