package com.ss.ics.hikvision;

import com.ss.ics.domain.DeviceDomain;
import com.ss.ics.domain.LoginDomain;
import com.ss.ics.domain.PlayDomain;
import com.ss.ics.domain.PlaybackTimePeriodDomain;
import com.ss.ics.hikvision.internal.HikvisionNativeApi;
import com.ss.ics.hikvision.internal.model.HikvisionFileSearchCondition;
import com.ss.ics.hikvision.internal.model.HikvisionFileSearchResult;
import com.ss.ics.hikvision.internal.model.HikvisionNativeLoginResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

class HikvisionCameraSdkServicePlaybackQueryTest {

    @Test
    void returnsEveryDayAndSplitsRecordingsAcrossMidnight() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.results.add(new HikvisionFileSearchResult(
                HikvisionFileSearchResult.SUCCESS,
                LocalDateTime.of(2026, 2, 3, 23, 30),
                LocalDateTime.of(2026, 2, 4, 0, 15)));
        nativeApi.results.add(HikvisionFileSearchResult.completed());
        HikvisionCameraSdkService service = service(nativeApi, Duration.ofSeconds(1));

        List<PlaybackTimePeriodDomain> result = service.playbackRecordExistByMonth(
                device().setChannel("1"), request(1), 2026, 2);

        assertThat(result).hasSize(28);
        assertThat(result.get(0).getDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(result.get(0).isExistRecord()).isFalse();
        assertThat(result.get(0).getTimePeriods()).isEmpty();
        assertThat(result.get(2).getTimePeriods()).singleElement().satisfies(period -> {
            assertThat(period.getBeginTime()).isEqualTo(LocalTime.of(23, 30));
            assertThat(period.getEndTime()).isEqualTo(LocalTime.of(23, 59, 59));
        });
        assertThat(result.get(3).getTimePeriods()).singleElement().satisfies(period -> {
            assertThat(period.getBeginTime()).isEqualTo(LocalTime.MIDNIGHT);
            assertThat(period.getEndTime()).isEqualTo(LocalTime.of(0, 15));
        });
        assertThat(nativeApi.lastCondition).isEqualTo(new HikvisionFileSearchCondition(
                33,
                1,
                LocalDateTime.of(2026, 2, 1, 0, 0),
                LocalDateTime.of(2026, 2, 28, 23, 59, 59),
                5_000));
        assertThat(nativeApi.events).containsExactly(
                "login", "find:42", "next:81", "next:81", "close-find:81", "logout:42");
        service.close();
    }

    @Test
    void closesFindHandleAndLogsOutWhenNativeSearchFails() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.results.add(new HikvisionFileSearchResult(1004, null, null));
        HikvisionCameraSdkService service = service(nativeApi, Duration.ofSeconds(1));

        assertThatThrownBy(() -> service.playbackRecordExistByMonth(
                device(), request(0), 2026, 8))
                .isInstanceOf(HikvisionSdkException.class)
                .hasMessage("Hikvision playback calendar query failed (code=1004)")
                .hasMessageNotContaining("secret");
        assertThat(nativeApi.events).containsExactly(
                "login", "find:42", "next:81", "close-find:81", "logout:42");
        service.close();
    }

    @Test
    void rejectsInvalidQueryBeforeLoggingIn() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        HikvisionCameraSdkService service = service(nativeApi, Duration.ofSeconds(1));

        assertThatThrownBy(() -> service.playbackRecordExistByMonth(
                device(), request(0), 2026, 13))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("month must be between 1 and 12");
        assertThat(nativeApi.events).isEmpty();
        service.close();
    }

    @Test
    void stopsPollingAtConfiguredDeadlineAndReleasesResources() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.results.add(HikvisionFileSearchResult.finding());
        HikvisionCameraSdkService service = service(nativeApi, Duration.ofNanos(1));

        assertThatThrownBy(() -> service.playbackRecordExistByMonth(
                device(), request(0), 2026, 8))
                .isInstanceOf(HikvisionSdkException.class)
                .hasMessage("Hikvision playback calendar query timed out (code=1005)");
        assertThat(nativeApi.events).containsExactly(
                "login", "find:42", "close-find:81", "logout:42");
        service.close();
    }

    @Test
    void successfulResultsCannotBypassTheOverallDeadline() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.repeatedResult = new HikvisionFileSearchResult(
                HikvisionFileSearchResult.SUCCESS,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 1, 0, 1));
        nativeApi.nextDelay = Duration.ofMillis(1);
        HikvisionCameraSdkService service = service(nativeApi, Duration.ofMillis(2));

        assertThatThrownBy(() -> service.playbackRecordExistByMonth(
                device(), request(0), 2026, 8))
                .isInstanceOf(HikvisionSdkException.class)
                .hasMessage("Hikvision playback calendar query timed out (code=1005)");
        assertThat(nativeApi.events).contains("close-find:81", "logout:42");
        service.close();
    }

    @Test
    void preservesQueryFailureWhenClosingTheFindHandleAlsoFails() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.results.add(new HikvisionFileSearchResult(1004, null, null));
        nativeApi.closeFindResult = false;
        HikvisionCameraSdkService service = service(nativeApi, Duration.ofSeconds(1));

        Throwable thrown = catchThrowable(() -> service.playbackRecordExistByMonth(
                device(), request(0), 2026, 8));

        assertThat(thrown)
                .isInstanceOf(HikvisionSdkException.class)
                .hasMessage("Hikvision playback calendar query failed (code=1004)");
        assertThat(thrown.getSuppressed()).singleElement().satisfies(suppressed ->
                assertThat(suppressed)
                        .isInstanceOf(HikvisionSdkException.class)
                        .hasMessage("Hikvision playback calendar search close failed (code=17)"));
        service.close();
    }

    @Test
    void includesInitialNativeFindCallInOverallDeadline() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.findDelay = Duration.ofMillis(5);
        nativeApi.results.add(HikvisionFileSearchResult.completed());
        HikvisionCameraSdkService service = service(nativeApi, Duration.ofMillis(1));

        assertThatThrownBy(() -> service.playbackRecordExistByMonth(
                device(), request(0), 2026, 8))
                .isInstanceOf(HikvisionSdkException.class)
                .hasMessage("Hikvision playback calendar query timed out (code=1005)");
        assertThat(nativeApi.events).containsExactly(
                "login", "find:42", "close-find:81", "logout:42");
        service.close();
    }

    @Test
    void rejectsUnboundedRecordingResultsBeforeTheyExhaustMemory() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.repeatedResult = new HikvisionFileSearchResult(
                HikvisionFileSearchResult.SUCCESS,
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 1, 0, 1));
        HikvisionCameraSdkService service = service(nativeApi, Duration.ofSeconds(1));

        assertThatThrownBy(() -> service.playbackRecordExistByMonth(
                device(), request(0), 2026, 8))
                .isInstanceOf(HikvisionSdkException.class)
                .hasMessage("Hikvision playback calendar result limit exceeded (code=1004)");
        assertThat(nativeApi.events).contains("close-find:81", "logout:42");
        service.close();
    }

    @Test
    void usesNativeLastErrorWhenFindNextReturnsMinusOne() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.results.add(new HikvisionFileSearchResult(-1, null, null));
        HikvisionCameraSdkService service = service(nativeApi, Duration.ofSeconds(5));

        assertThatThrownBy(() -> service.playbackRecordExistByMonth(
                device(), request(0), 2026, 8))
                .isInstanceOf(HikvisionSdkException.class)
                .hasMessage("Hikvision playback calendar query failed (code=17)");
        service.close();
    }

    @Test
    void preservesQueryFailureWhenCloseFindThrows() {
        FakeNativeApi nativeApi = new FakeNativeApi();
        nativeApi.results.add(new HikvisionFileSearchResult(1004, null, null));
        nativeApi.closeFindException = new IllegalStateException("native close failed");
        HikvisionCameraSdkService service = service(nativeApi, Duration.ofSeconds(5));

        Throwable thrown = catchThrowable(() -> service.playbackRecordExistByMonth(
                device(), request(0), 2026, 8));

        assertThat(thrown)
                .isInstanceOf(HikvisionSdkException.class)
                .hasMessage("Hikvision playback calendar query failed (code=1004)");
        assertThat(thrown.getSuppressed()).singleElement()
                .isSameAs(nativeApi.closeFindException);
        service.close();
    }

    private static PlayDomain request(int streamType) {
        return new PlayDomain().setTakeStreamParam(
                new PlayDomain.TakeStreamParam().setStreamType(streamType));
    }

    private static DeviceDomain device() {
        return new DeviceDomain()
                .setIp("192.0.2.10")
                .setPort("8000")
                .setUsername("operator")
                .setPassword("secret");
    }

    private static HikvisionCameraSdkService service(
            FakeNativeApi nativeApi, Duration searchTimeout) {
        HikvisionSdkOptions options = new HikvisionSdkOptions(
                Path.of("sdk"), Duration.ofSeconds(5), 2);
        HikvisionSdkRuntime runtime = HikvisionSdkRuntime.openForTesting(options, nativeApi);
        return HikvisionCameraSdkService.createForTesting(runtime, searchTimeout);
    }

    private static final class FakeNativeApi implements HikvisionNativeApi {
        private final List<String> events = new ArrayList<>();
        private final Deque<HikvisionFileSearchResult> results = new ArrayDeque<>();
        private HikvisionFileSearchCondition lastCondition;
        private HikvisionFileSearchResult repeatedResult;
        private boolean closeFindResult = true;
        private Duration findDelay = Duration.ZERO;
        private Duration nextDelay = Duration.ZERO;
        private RuntimeException closeFindException;

        @Override
        public boolean initialize() {
            return true;
        }

        @Override
        public boolean cleanup() {
            return true;
        }

        @Override
        public int lastError() {
            return 17;
        }

        @Override
        public HikvisionNativeLoginResult login(LoginDomain login) {
            events.add("login");
            return new HikvisionNativeLoginResult(42, 33, 71, 8, "serial-01");
        }

        @Override
        public boolean logout(int userId) {
            events.add("logout:" + userId);
            return true;
        }

        @Override
        public long findFiles(int userId, HikvisionFileSearchCondition condition) {
            events.add("find:" + userId);
            lastCondition = condition;
            if (!findDelay.isZero()) {
                try {
                    Thread.sleep(findDelay.toMillis());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("find interrupted", exception);
                }
            }
            return 81;
        }

        @Override
        public HikvisionFileSearchResult findNextFile(long findHandle) {
            events.add("next:" + findHandle);
            if (!nextDelay.isZero()) {
                try {
                    Thread.sleep(nextDelay.toMillis());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("find next interrupted", exception);
                }
            }
            if (repeatedResult != null) {
                return repeatedResult;
            }
            return results.removeFirst();
        }

        @Override
        public boolean closeFind(long findHandle) {
            events.add("close-find:" + findHandle);
            if (closeFindException != null) {
                throw closeFindException;
            }
            return closeFindResult;
        }
    }
}
