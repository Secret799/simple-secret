package com.ss.ics.registry;

import com.ss.ics.domain.DeviceDomain;
import com.ss.ics.domain.LoggedDomain;
import com.ss.ics.domain.LoginDomain;
import com.ss.ics.domain.PTZControlDomain;
import com.ss.ics.domain.PlayDomain;
import com.ss.ics.domain.PlaybackTimePeriodDomain;
import com.ss.ics.exception.UnsupportedCameraSdkOperationException;
import com.ss.ics.service.DeviceLoginService;
import com.ss.ics.service.PlayQueryService;
import com.ss.ics.service.PlayService;
import com.ss.ics.service.PtzControlService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CameraSdkServiceRegistryTest {

    @Test
    void selectsServicesByProductCaseInsensitively() {
        HikPtzService ptz = new HikPtzService();
        HikPlayService play = new HikPlayService();
        HikLoginAndQueryService loginAndQuery = new HikLoginAndQueryService();
        CameraSdkServiceRegistry registry = new CameraSdkServiceRegistry(List.of(ptz, play, loginAndQuery));

        assertThat(registry.requirePtz("hikvision")).isSameAs(ptz);
        HikPlayService selectedPlay = registry.requirePlay("HIKVISION", HikPlayService.class);
        assertThat(selectedPlay).isSameAs(play);
        assertThat(selectedPlay.realPlay(new DeviceDomain(), new PlayDomain(), "target"))
                .isEqualTo("target");
        assertThat(registry.requireLogin("Hikvision")).isSameAs(loginAndQuery);
        assertThat(registry.requirePlayQuery("hikvision")).isSameAs(loginAndQuery);
        assertThat(registry.findPtz("unknown")).isEmpty();
    }

    @Test
    void rejectsDuplicateProductWithinSameCapability() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CameraSdkServiceRegistry(List.of(
                        new HikPtzService(), new DuplicateHikPtzService())))
                .withMessageContaining("Hikvision")
                .withMessageContaining("PTZ");
    }

    @Test
    void distinguishesInvalidProductFromUnsupportedCapability() {
        CameraSdkServiceRegistry registry = new CameraSdkServiceRegistry(List.of());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> registry.requirePtz(" "))
                .withMessage("product must not be blank");
        assertThatThrownBy(() -> registry.requirePtz("UnknownVendor:operator/secret"))
                .isInstanceOf(UnsupportedCameraSdkOperationException.class)
                .hasMessage("No PTZ camera SDK service is registered");
    }

    @Test
    void defaultOperationsFailExplicitly() {
        PtzControlService unsupported = new PtzControlService() {
            @Override
            public String product() {
                return "Acme";
            }
        };

        assertThatThrownBy(() -> unsupported.syncControl(new DeviceDomain(), new PTZControlDomain()))
                .isInstanceOf(UnsupportedCameraSdkOperationException.class)
                .hasMessageContaining("PTZ");
    }

    private static final class HikPtzService implements PtzControlService {
        @Override
        public String product() {
            return "Hikvision";
        }

        @Override
        public boolean syncControl(DeviceDomain device, PTZControlDomain control) {
            return true;
        }
    }

    private static final class DuplicateHikPtzService implements PtzControlService {
        @Override
        public String product() {
            return "Hikvision";
        }
    }

    private static final class HikPlayService implements PlayService<String, String> {
        @Override
        public String product() {
            return "Hikvision";
        }

        @Override
        public String realPlay(DeviceDomain device, PlayDomain request, String target) {
            return target;
        }
    }

    private static final class HikLoginAndQueryService
            implements DeviceLoginService, PlayQueryService {
        @Override
        public String product() {
            return "Hikvision";
        }

        @Override
        public LoggedDomain login(LoginDomain login) {
            return new LoggedDomain().setUserId("42");
        }

        @Override
        public void logout(String userId) {
        }

        @Override
        public List<PlaybackTimePeriodDomain> playbackRecordExistByMonth(
                DeviceDomain device, PlayDomain request, int year, int month) {
            return List.of();
        }
    }
}
