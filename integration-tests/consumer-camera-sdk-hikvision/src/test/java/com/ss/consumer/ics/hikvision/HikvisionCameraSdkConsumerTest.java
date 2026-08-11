package com.ss.consumer.ics.hikvision;

import com.ss.ics.hikvision.HikvisionCameraSdkService;
import com.ss.ics.hikvision.HikvisionSdkOptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证第三方应用构造海康配置时不会提前加载厂商原生库。 */
class HikvisionCameraSdkConsumerTest {

    @Test
    void loadsPublicApiAndBuildsOptionsWithoutNativeLibraries() throws ClassNotFoundException {
        HikvisionSdkOptions options = HikvisionSdkOptions.defaults(Path.of("vendor-sdk"));
        Class<?> serviceType = Class.forName(
                "com.ss.ics.hikvision.HikvisionCameraSdkService",
                true,
                Thread.currentThread().getContextClassLoader());

        assertThat(serviceType).isEqualTo(HikvisionCameraSdkService.class);
        assertThat(HikvisionCameraSdkService.PRODUCT).isEqualTo("Hikvision");
        assertThat(options.libraryDirectory()).isAbsolute();
        assertThat(options.fileSearchTimeout())
                .isEqualTo(HikvisionSdkOptions.DEFAULT_FILE_SEARCH_TIMEOUT);
    }
}
