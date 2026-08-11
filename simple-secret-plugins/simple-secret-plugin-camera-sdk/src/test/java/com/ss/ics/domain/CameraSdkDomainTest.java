package com.ss.ics.domain;

import com.ss.ics.constants.CameraBrandEnums;
import com.ss.ics.constants.enums.PtzControlCommandEnums;
import com.ss.ics.error.SdkError;
import org.junit.jupiter.api.Test;

import java.beans.Introspector;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CameraSdkDomainTest {

    @Test
    void exposesStableBrandAndSdkErrorMetadata() {
        SdkError error = SdkError.undefined(CameraBrandEnums.HIKVISION.getCode());

        assertThat(CameraBrandEnums.DAHUA.getCode()).isEqualTo("Dahua");
        assertThat(error.code()).isEqualTo("-1");
        assertThat(error.formatErrorMessage("Login failed"))
                .isEqualTo("Login failed, SDK brand=[Hikvision], code=[-1], message=[Unknown SDK error]");
    }

    @Test
    void convertsDeviceToLoginWithoutChangingCredentials() {
        DeviceDomain device = new DeviceDomain()
                .setDeviceId("device-01")
                .setDeviceName("gate")
                .setUsername(" operator ")
                .setPassword(" secret ")
                .setIp("192.0.2.10")
                .setPort("8000")
                .setChannel("1");

        LoginDomain login = device.toLoginDomain();

        assertThat(login.getUsername()).isEqualTo(" operator ");
        assertThat(login.getPassword()).isEqualTo(" secret ");
        assertThat(login.getIp()).isEqualTo("192.0.2.10");
        assertThat(login.getPort()).isEqualTo("8000");
    }

    @Test
    void clampsAndScalesPtzSpeed() {
        PTZControlDomain control = new PTZControlDomain()
                .setCommand(PtzControlCommandEnums.LEFT_UP)
                .setIsBegin(true)
                .setDuration(Duration.ofMillis(500));

        assertThat(control.getSpeedLevel()).isEqualTo(5);
        assertThat(control.setSpeedLevel(-1).getSpeedLevel()).isEqualTo(1);
        assertThat(control.getSpeed(0, 100)).isZero();
        assertThat(control.setSpeedLevel(20).getSpeedLevel()).isEqualTo(10);
        assertThat(control.getSpeed(0, 100)).isEqualTo(100);
        assertThat(PtzControlCommandEnums.getByCode("5")).isEqualTo(PtzControlCommandEnums.LEFT_UP);
    }

    @Test
    void exposesOnlyTheMigratedIsBeginBeanProperty() throws Exception {
        Set<String> propertyNames = Arrays.stream(
                        Introspector.getBeanInfo(PTZControlDomain.class).getPropertyDescriptors())
                .map(descriptor -> descriptor.getName())
                .collect(Collectors.toSet());

        assertThat(propertyNames).contains("isBegin").doesNotContain("begin");
        assertThat(PTZControlDomain.class.getDeclaredField("isBegin").getType())
                .isEqualTo(Boolean.class);
    }

    @Test
    void preservesPlayParametersAndDefaultsFrameRate() {
        LocalDateTime begin = LocalDateTime.of(2026, 8, 11, 10, 0);
        LocalDateTime end = begin.plusMinutes(30);
        PlayDomain.PlaybackParam playback = new PlayDomain.PlaybackParam()
                .setCode("playback-01")
                .setBeginTime(begin)
                .setEndTime(end)
                .setMultiplier(2.0);
        PlayDomain.TakeStreamParam stream = new PlayDomain.TakeStreamParam()
                .setStreamType(1)
                .setByProtoType("1")
                .setVideoEncode(0)
                .setAudioEncode(1);
        PlayDomain.VideoParam video = new PlayDomain.VideoParam()
                .setResolutionWidth(1920)
                .setResolutionHeight(1080)
                .setBitRate(4096);

        PlayDomain request = new PlayDomain()
                .setPlaybackParam(playback)
                .setTakeStreamParam(stream)
                .setVideoParam(video);

        assertThat(request.getPlaybackParam().getBeginTime()).isEqualTo(begin);
        assertThat(request.getPlaybackParam().getEndTime()).isEqualTo(end);
        assertThat(request.getTakeStreamParam().getStreamType()).isEqualTo(1);
        assertThat(request.getVideoParam().getFrameRate()).isEqualTo(25);
    }

    @Test
    void preservesLoginPlaybackCalendarAndStreamSummary() {
        LoggedDomain logged = new LoggedDomain()
                .setUserId("42")
                .setDeviceId("serial-01")
                .setChannelNo("1")
                .setDeviceCategory("IPC")
                .setDeviceType("model-x")
                .setLoginTime(LocalDateTime.of(2026, 8, 11, 11, 0));
        PlaybackTimePeriodDomain calendar = new PlaybackTimePeriodDomain()
                .setDate(LocalDate.of(2026, 8, 11))
                .setExistRecord(true)
                .setTimePeriods(new ArrayList<>());
        calendar.getTimePeriods().add(new PlaybackTimePeriodDomain.TimePeriod()
                .setBeginTime(LocalTime.of(10, 0))
                .setEndTime(LocalTime.of(10, 30)));
        StreamSummaryDomain stream = new StreamSummaryDomain()
                .setApp("live")
                .setStream("camera-01")
                .setSchema("rtsp")
                .setPushStreamWaitTime(3_000L);

        assertThat(logged.getUserId()).isEqualTo("42");
        assertThat(calendar.getTimePeriods()).hasSize(1);
        assertThat(stream.getPushStreamWaitTime()).isEqualTo(3_000L);
    }
}
