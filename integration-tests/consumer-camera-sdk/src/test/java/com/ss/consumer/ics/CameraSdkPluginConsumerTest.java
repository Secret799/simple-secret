package com.ss.consumer.ics;

import com.ss.ics.domain.DeviceDomain;
import com.ss.ics.domain.PTZControlDomain;
import com.ss.ics.domain.PlayDomain;
import com.ss.ics.registry.CameraSdkServiceRegistry;
import com.ss.ics.service.PlayService;
import com.ss.ics.service.PtzControlService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证第三方应用只声明 Camera SDK plugin 时可以实现并注册厂商能力。 */
class CameraSdkPluginConsumerTest {

    @Test
    void usesApplicationProvidedVendorServiceWithoutSpringOrJna() {
        PtzControlService ptzService = new PtzControlService() {
            @Override
            public String product() {
                return "ConsumerVendor";
            }

            @Override
            public boolean syncControl(DeviceDomain device, PTZControlDomain control) {
                return "camera-01".equals(device.getDeviceId());
            }
        };
        ConsumerPlayService playService = new ConsumerPlayService();
        CameraSdkServiceRegistry registry = new CameraSdkServiceRegistry(List.of(ptzService, playService));

        boolean accepted = registry.requirePtz("consumervendor").syncControl(
                new DeviceDomain().setDeviceId("camera-01"), new PTZControlDomain());
        String target = registry.requirePlay("ConsumerVendor", ConsumerPlayService.class)
                .realPlay(new DeviceDomain(), new PlayDomain(), "consumer-target");

        assertThat(accepted).isTrue();
        assertThat(target).isEqualTo("consumer-target");
    }

    private static final class ConsumerPlayService implements PlayService<String, String> {
        @Override
        public String product() {
            return "ConsumerVendor";
        }

        @Override
        public String realPlay(DeviceDomain device, PlayDomain request, String target) {
            return target;
        }
    }
}
