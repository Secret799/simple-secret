package com.ss.ics.dahua;

import com.ss.ics.domain.LoginDomain;
import com.ss.ics.dahua.internal.DahuaNativeApi;
import com.ss.ics.dahua.internal.DahuaNativeStreamCallback;
import com.ss.ics.dahua.internal.DahuaNativeThermalCallback;
import com.ss.ics.dahua.internal.model.DahuaNativeLoginResult;
import com.ss.ics.dahua.internal.model.DahuaNativeRadiometryRecord;
import com.ss.ics.dahua.internal.model.DahuaNativeRegionTemperature;
import com.ss.ics.dahua.internal.model.DahuaNativeSearchStart;
import com.ss.ics.dahua.internal.model.DahuaNativeTemperatureSummary;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class FakeDahuaNativeApi implements DahuaNativeApi {
    final List<String> events = new ArrayList<>();
    final Deque<Boolean> ptzResults = new ArrayDeque<>();
    final CountDownLatch firstPtzStarted = new CountDownLatch(1);
    final CountDownLatch releaseFirstPtz = new CountDownLatch(1);
    final CountDownLatch completedCommands = new CountDownLatch(2);
    boolean initializeResult = true;
    boolean cleanupResult = true;
    boolean logoutResult = true;
    boolean ptzResult = true;
    boolean blockFirstPtz;
    boolean stopPreviewResult = true;
    boolean previewLinkageFailure;
    boolean stopPreviewLinkageFailure;
    boolean thermalLinkageFailure;
    boolean loginLinkageFailure;
    long previewHandle = 99L;
    DahuaNativeStreamCallback streamCallback;
    DahuaNativeThermalCallback thermalCallback;
    boolean detachResult = true;
    boolean stopSearchResult = true;
    int fetchStatus = 2;
    int searchTotalCount = 3;
    long thermalHandle = 77L;
    int searchHandle = 88;
    int errorCode;
    int initializeCalls;
    int cleanupCalls;
    LoginDomain lastLogin;

    @Override
    public boolean initialize() {
        initializeCalls++;
        return initializeResult;
    }

    @Override
    public boolean cleanup() {
        cleanupCalls++;
        events.add("cleanup");
        return cleanupResult;
    }

    @Override
    public int lastError() {
        return errorCode;
    }

    @Override
    public DahuaNativeLoginResult login(LoginDomain login) {
        lastLogin = login;
        events.add("login");
        if (loginLinkageFailure) {
            throw new UnsatisfiedLinkError("missing login symbol");
        }
        return new DahuaNativeLoginResult(42L, 0, 71, 4, "serial-01");
    }

    @Override
    public boolean logout(long userId) {
        events.add("logout:" + userId);
        completedCommands.countDown();
        return logoutResult;
    }

    @Override
    public boolean ptzControl(
            long userId, int channel, int command, int param1, int param2, int param3, int stop) {
        events.add("ptz:" + userId + ":" + channel + ":" + command + ":"
                + param1 + ":" + param2 + ":" + param3 + ":" + stop);
        if (blockFirstPtz) {
            blockFirstPtz = false;
            firstPtzStarted.countDown();
            try {
                releaseFirstPtz.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return ptzResults.isEmpty() ? ptzResult : ptzResults.removeFirst();
    }

    @Override
    public long startPreview(
            long userId, int channel, int streamType, DahuaNativeStreamCallback callback) {
        events.add("preview:start:" + userId + ":" + channel + ":" + streamType);
        if (previewLinkageFailure) {
            throw new UnsatisfiedLinkError("missing preview symbol");
        }
        streamCallback = callback;
        return previewHandle;
    }

    @Override
    public boolean stopPreview(long handle) {
        events.add("preview:stop:" + handle);
        if (stopPreviewLinkageFailure) {
            throw new UnsatisfiedLinkError("missing stop preview symbol");
        }
        return stopPreviewResult;
    }

    @Override
    public long attachRadiometry(
            long userId, int channel, DahuaNativeThermalCallback callback) {
        events.add("thermal:attach:" + userId + ":" + channel);
        if (thermalLinkageFailure) {
            throw new UnsatisfiedLinkError("missing radiometry symbol");
        }
        thermalCallback = callback;
        return thermalHandle;
    }

    @Override
    public boolean detachRadiometry(long handle) {
        events.add("thermal:detach:" + handle);
        return detachResult;
    }

    @Override
    public int fetchRadiometry(long userId, int channel) {
        events.add("thermal:fetch:" + userId + ":" + channel);
        return fetchStatus;
    }

    @Override
    public DahuaNativeTemperatureSummary queryPointTemperature(
            long userId, int channel, int x, int y) {
        events.add("thermal:point:" + userId + ":" + channel + ":" + x + ":" + y);
        return summary();
    }

    @Override
    public DahuaNativeTemperatureSummary queryItemTemperature(
            long userId, int channel, int presetId, int ruleId, int meterType) {
        events.add("thermal:item:" + userId + ":" + channel + ":"
                + presetId + ":" + ruleId + ":" + meterType);
        return summary();
    }

    @Override
    public DahuaNativeRegionTemperature queryRegionTemperature(
            long userId, int channel, List<DahuaPoint> points) {
        events.add("thermal:region:" + userId + ":" + channel + ":" + points.size());
        return new DahuaNativeRegionTemperature(0, 20.5, 30.5, 10.5,
                new DahuaPoint(2, 3), new DahuaPoint(4, 5));
    }

    @Override
    public DahuaNativeSearchStart startRadiometrySearch(
            long userId, int channel, int meterType, int period,
            LocalDateTime begin, LocalDateTime end) {
        events.add("thermal:search:start:" + userId + ":" + channel);
        return new DahuaNativeSearchStart(searchHandle, searchTotalCount);
    }

    @Override
    public List<DahuaNativeRadiometryRecord> findRadiometryPage(
            long userId, int finderHandle, int offset, int count) {
        events.add("thermal:search:page:" + finderHandle + ":" + offset + ":" + count);
        int remaining = Math.max(0, searchTotalCount - offset);
        int size = Math.min(remaining, count);
        List<DahuaNativeRadiometryRecord> results = new ArrayList<>();
        for (int index = 0; index < size; index++) {
            results.add(new DahuaNativeRadiometryRecord(
                    LocalDateTime.of(2026, 1, 1, 0, 0).plusMinutes(offset + index),
                    1, offset + index, "rule", 0, summary(), List.of(new DahuaPoint(1, 2))));
        }
        return results;
    }

    @Override
    public boolean stopRadiometrySearch(long userId, int finderHandle) {
        events.add("thermal:search:stop:" + finderHandle);
        return stopSearchResult;
    }

    private static DahuaNativeTemperatureSummary summary() {
        return new DahuaNativeTemperatureSummary(1, 0, 20.5f, 30.5f, 10.5f, 19.5f, 1.5f);
    }
}
