package com.ss.ics.service;

import com.ss.ics.domain.DeviceDomain;
import com.ss.ics.domain.PlayDomain;
import com.ss.ics.domain.PlaybackTimePeriodDomain;
import com.ss.ics.exception.UnsupportedCameraSdkOperationException;

import java.util.List;

/** 厂商 SDK 历史录像查询能力。 */
public interface PlayQueryService extends CameraSdkService {

    /**
     * @param device 设备
     * @param request 取流参数
     * @param year 年
     * @param month 月，范围 1 到 12
     * @return 当月录像分布
     */
    default List<PlaybackTimePeriodDomain> playbackRecordExistByMonth(
            DeviceDomain device, PlayDomain request, int year, int month) {
        throw new UnsupportedCameraSdkOperationException("Playback calendar query is not supported");
    }
}
