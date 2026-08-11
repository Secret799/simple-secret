package com.ss.camera.service;

import com.ss.camera.domain.StreamUrlAssemblyDomain;
import com.ss.camera.service.dahua.DahuaCameraUrlAssemblyService;
import com.ss.camera.service.dahua.DahuaNvrUrlAssemblyService;
import com.ss.camera.service.hikvision.HikCameraUrlAssemblyService;
import com.ss.camera.service.hikvision.HikNvrUrlAssemblyService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class CameraUrlAssemblyTest {

    @Test
    void assemblesAllBuiltInCameraUrls() {
        StreamUrlAssemblyDomain request = request();

        assertThat(new HikCameraUrlAssemblyService().assembly(request))
                .isEqualTo("rtsp://admin:p%40ss@192.0.2.10:554/h264/ch1/main/av_stream");
        assertThat(new HikNvrUrlAssemblyService().assembly(request))
                .isEqualTo("rtsp://admin:p%40ss@192.0.2.10:554/Streaming/Channels/101?transportmode=multicast");
        assertThat(new DahuaCameraUrlAssemblyService().assembly(request))
                .isEqualTo("rtsp://admin:p%40ss@192.0.2.10:554/cam/realmonitor?channel=1&subtype=0");
        assertThat(new DahuaNvrUrlAssemblyService().assembly(request))
                .isEqualTo("rtsp://admin:p%40ss@192.0.2.10:554/cam/realmonitor?channel=1&subtype=0");
    }

    @Test
    void percentEncodesEachCredentialComponent() {
        StreamUrlAssemblyDomain request = request()
                .setAccount("operator name")
                .setPassword("p:a/ss?#");

        assertThat(new DahuaCameraUrlAssemblyService().assembly(request))
                .startsWith("rtsp://operator%20name:p%3Aa%2Fss%3F%23@");
    }

    @Test
    void preservesCredentialWhitespaceDuringEncoding() {
        StreamUrlAssemblyDomain request = request()
                .setAccount(" operator ")
                .setPassword(" secret ");

        assertThat(new DahuaCameraUrlAssemblyService().assembly(request))
                .startsWith("rtsp://%20operator%20:%20secret%20@");
    }

    @Test
    void normalizesHikvisionNvrChannelAndSubStream() {
        HikNvrUrlAssemblyService service = new HikNvrUrlAssemblyService();

        assertThat(service.assembly(request().setChannelNo("1").setStreamType("sub")))
                .contains("/Streaming/Channels/102?");
        assertThat(service.assembly(request().setChannelNo("101").setStreamType("sub")))
                .contains("/Streaming/Channels/102?");
        assertThat(service.assembly(request().setChannelNo("102").setStreamType("main")))
                .contains("/Streaming/Channels/101?");
    }

    @Test
    void rejectsMalformedRequiredFieldsWithoutEchoingCredentials() {
        StreamUrlAssemblyDomain request = request().setChannelNo(" ");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HikCameraUrlAssemblyService().assembly(request))
                .withMessageContaining("channelNo")
                .withMessageNotContaining(request.getPassword());
    }

    @Test
    void rejectsNonAsciiChannelAndStreamTokens() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HikCameraUrlAssemblyService()
                        .assembly(request().setChannelNo("\u0661")))
                .withMessageContaining("channelNo");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HikCameraUrlAssemblyService()
                        .assembly(request().setStreamType("\u4E3B")))
                .withMessageContaining("streamType");
    }

    @Test
    void rejectsHostWithEmbeddedPort() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new HikCameraUrlAssemblyService()
                        .assembly(request().setIp("camera.example.com:8554")))
                .withMessageContaining("ip");
    }

    @Test
    void formatsBareAndBracketedIpv6Hosts() {
        HikCameraUrlAssemblyService service = new HikCameraUrlAssemblyService();

        assertThat(service.assembly(request().setIp("2001:db8::10")))
                .startsWith("rtsp://admin:p%40ss@[2001:db8::10]:554/");
        assertThat(service.assembly(request().setIp("[2001:db8::10]")))
                .startsWith("rtsp://admin:p%40ss@[2001:db8::10]:554/");
    }

    @Test
    void selectsServicesCaseInsensitivelyAndFailsForUnknownCombination() {
        UrlAssemblyHolder holder = new UrlAssemblyHolder(List.of(
                new HikCameraUrlAssemblyService(),
                new HikNvrUrlAssemblyService(),
                new DahuaCameraUrlAssemblyService(),
                new DahuaNvrUrlAssemblyService()));

        assertThat(holder.get("hikvision", "camera")).isInstanceOf(HikCameraUrlAssemblyService.class);
        assertThat(holder.assembly(request().setBrand("DAHUA").setType("nvr")))
                .contains("/cam/realmonitor");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> holder.assembly(request().setBrand("unknown")))
                .withMessageContaining("unknown")
                .withMessageNotContaining(request().getPassword());
    }

    @Test
    void rejectsDuplicateBrandAndTypeHandlers() {
        UrlAssemblyService duplicate = new HikCameraUrlAssemblyService();

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new UrlAssemblyHolder(List.of(
                        new HikCameraUrlAssemblyService(), duplicate)))
                .withMessageContaining("Hikvision")
                .withMessageContaining("CAMERA");
    }

    @Test
    void keepsDelimiterContainingBrandAndTypeCombinationsDistinct() {
        UrlAssemblyService first = service("ACME__NVR", "CAMERA");
        UrlAssemblyService second = service("ACME", "NVR__CAMERA");

        UrlAssemblyHolder holder = new UrlAssemblyHolder(List.of(first, second));

        assertThat(holder.get("acme__nvr", "camera")).isSameAs(first);
        assertThat(holder.get("acme", "nvr__camera")).isSameAs(second);
    }

    private static UrlAssemblyService service(String brand, String type) {
        return new UrlAssemblyService() {
            @Override
            public String brand() {
                return brand;
            }

            @Override
            public String type() {
                return type;
            }

            @Override
            public String assembly(StreamUrlAssemblyDomain domain) {
                return brand + ":" + type;
            }
        };
    }

    private static StreamUrlAssemblyDomain request() {
        return new StreamUrlAssemblyDomain()
                .setIp("192.0.2.10")
                .setPort("554")
                .setAccount("admin")
                .setPassword("p@ss")
                .setChannelNo("1")
                .setStreamType("main")
                .setBrand("Hikvision")
                .setType("CAMERA");
    }
}
