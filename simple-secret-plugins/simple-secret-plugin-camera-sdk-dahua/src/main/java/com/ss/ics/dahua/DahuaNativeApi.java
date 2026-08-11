package com.ss.ics.dahua;

import com.ss.ics.domain.LoginDomain;

import java.time.LocalDateTime;
import java.util.List;

/** 驱动内部使用的最小大华原生 SDK 适配接口。 */
interface DahuaNativeApi {

    boolean initialize();

    boolean cleanup();

    int lastError();

    default DahuaNativeLoginResult login(LoginDomain login) {
        throw new UnsupportedOperationException("login is not implemented");
    }

    default boolean logout(long userId) {
        throw new UnsupportedOperationException("logout is not implemented");
    }

    default boolean ptzControl(
            long userId, int channel, int command,
            int param1, int param2, int param3, int stop) {
        throw new UnsupportedOperationException("PTZ control is not implemented");
    }

    default long startPreview(
            long userId, int channel, int streamType, DahuaNativeStreamCallback callback) {
        throw new UnsupportedOperationException("real-time preview is not implemented");
    }

    default boolean stopPreview(long previewHandle) {
        throw new UnsupportedOperationException("real-time preview is not implemented");
    }

    default long attachRadiometry(
            long userId, int channel, DahuaNativeThermalCallback callback) {
        throw new UnsupportedOperationException("radiometry is not implemented");
    }

    default boolean detachRadiometry(long subscriptionHandle) {
        throw new UnsupportedOperationException("radiometry is not implemented");
    }

    default int fetchRadiometry(long userId, int channel) {
        throw new UnsupportedOperationException("radiometry is not implemented");
    }

    default DahuaNativeTemperatureSummary queryPointTemperature(
            long userId, int channel, int x, int y) {
        throw new UnsupportedOperationException("radiometry is not implemented");
    }

    default DahuaNativeTemperatureSummary queryItemTemperature(
            long userId, int channel, int presetId, int ruleId, int meterType) {
        throw new UnsupportedOperationException("radiometry is not implemented");
    }

    default DahuaNativeRegionTemperature queryRegionTemperature(
            long userId, int channel, List<DahuaPoint> points) {
        throw new UnsupportedOperationException("radiometry is not implemented");
    }

    default DahuaNativeSearchStart startRadiometrySearch(
            long userId, int channel, int meterType, int period,
            LocalDateTime begin, LocalDateTime end) {
        throw new UnsupportedOperationException("radiometry search is not implemented");
    }

    default List<DahuaNativeRadiometryRecord> findRadiometryPage(
            long userId, int finderHandle, int offset, int count) {
        throw new UnsupportedOperationException("radiometry search is not implemented");
    }

    default boolean stopRadiometrySearch(long userId, int finderHandle) {
        throw new UnsupportedOperationException("radiometry search is not implemented");
    }
}
