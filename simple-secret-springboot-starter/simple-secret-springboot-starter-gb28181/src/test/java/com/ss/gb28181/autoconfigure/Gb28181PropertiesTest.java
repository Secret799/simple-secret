package com.ss.gb28181.autoconfigure;

import com.ss.gb28181.GbServerOptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class Gb28181PropertiesTest {

    @Test
    void validatesMobilePositionCapacitiesAndMapsThemToCoreOptions() {
        var properties = new Gb28181Properties();
        properties.setServerId("34020000002000000001");
        properties.setRealm("example.test");

        assertThat(properties.getMaxMobilePositionSubscriptions()).isEqualTo(256);
        assertThat(properties.getMaxPendingMobilePositionNotifications()).isEqualTo(256);
        assertThat(properties.getMaxMobilePositionItems()).isEqualTo(1000);
        assertThat(properties.toOptions().maxMobilePositionItems()).isEqualTo(1000);
        for (int valid : new int[] {1, 37, 10000}) {
            properties.setMaxMobilePositionSubscriptions(valid);
            properties.setMaxPendingMobilePositionNotifications(valid);
            properties.setMaxMobilePositionItems(valid);
            assertThat(properties.toOptions().maxMobilePositionSubscriptions()).isEqualTo(valid);
            assertThat(properties.toOptions().maxPendingMobilePositionNotifications()).isEqualTo(valid);
            assertThat(properties.toOptions().maxMobilePositionItems()).isEqualTo(valid);
        }
        properties.setMaxPendingMobilePositionNotifications(256);
        properties.setMaxMobilePositionItems(1000);
        for (int invalid : new int[] {0, 10001}) {
            properties.setMaxMobilePositionSubscriptions(invalid);
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                    .isThrownBy(properties::toOptions)
                    .withMessage("maxMobilePositionSubscriptions outside supported range");
        }
        properties.setMaxMobilePositionSubscriptions(256);
        for (int invalid : new int[] {0, 10001}) {
            properties.setMaxPendingMobilePositionNotifications(invalid);
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                    .isThrownBy(properties::toOptions)
                    .withMessage("maxPendingMobilePositionNotifications outside supported range");
        }
        properties.setMaxPendingMobilePositionNotifications(256);
        for (int invalid : new int[] {0, 10001}) {
            properties.setMaxMobilePositionItems(invalid);
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                    .isThrownBy(properties::toOptions)
                    .withMessage("maxMobilePositionItems outside supported range");
        }
    }

    @Test
    void validatesCatalogSubscriptionCapacitiesAndMapsThemToCoreOptions() {
        var properties = new Gb28181Properties();
        properties.setServerId("34020000002000000001");
        properties.setRealm("example.test");

        assertThat(properties.getMaxCatalogSubscriptions()).isEqualTo(256);
        assertThat(properties.getMaxPendingCatalogNotifications()).isEqualTo(256);
        for (int valid : new int[] {1, 37, 10000}) {
            properties.setMaxCatalogSubscriptions(valid);
            properties.setMaxPendingCatalogNotifications(valid);
            assertThat(properties.toOptions().maxCatalogSubscriptions()).isEqualTo(valid);
            assertThat(properties.toOptions().maxPendingCatalogNotifications()).isEqualTo(valid);
        }
        properties.setMaxPendingCatalogNotifications(256);
        for (int invalid : new int[] {0, 10001}) {
            properties.setMaxCatalogSubscriptions(invalid);
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                    .isThrownBy(properties::toOptions)
                    .withMessage("maxCatalogSubscriptions outside supported range");
        }
        properties.setMaxCatalogSubscriptions(256);
        for (int invalid : new int[] {0, 10001}) {
            properties.setMaxPendingCatalogNotifications(invalid);
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                    .isThrownBy(properties::toOptions)
                    .withMessage("maxPendingCatalogNotifications outside supported range");
        }
    }

    @Test
    void validatesAlarmSubscriptionCapacityAndMapsItToCoreOptions() {
        var properties = new Gb28181Properties();
        properties.setServerId("34020000002000000001");
        properties.setRealm("example.test");
        assertThat(properties.getMaxAlarmSubscriptions()).isEqualTo(256);
        for (int valid : new int[] {1, 37, 10000}) {
            properties.setMaxAlarmSubscriptions(valid);
            assertThat(properties.toOptions().maxAlarmSubscriptions()).isEqualTo(valid);
        }
        for (int invalid : new int[] {0, 10001}) {
            properties.setMaxAlarmSubscriptions(invalid);
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                    .isThrownBy(properties::toOptions)
                    .withMessage("maxAlarmSubscriptions outside supported range");
        }
    }

    @Test
    void validatesAlarmCapacityAndMapsItToCoreOptions() {
        var properties = new Gb28181Properties();
        properties.setServerId("34020000002000000001");
        properties.setRealm("example.test");
        assertThat(properties.getMaxPendingAlarms()).isEqualTo(256);
        assertThat(properties.getMaxAlarmSubscriptions()).isEqualTo(256);
        for (int valid : new int[] {1, 37, 10000}) {
            properties.setMaxPendingAlarms(valid);
            assertThat(properties.toOptions().maxPendingAlarms()).isEqualTo(valid);
        }
        for (int invalid : new int[] {0, 10001}) {
            properties.setMaxPendingAlarms(invalid);
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                    .isThrownBy(properties::toOptions)
                    .withMessage("maxPendingAlarms outside supported range");
        }
    }

    @Test
    void validatesRecordCapacityAndMapsItToCoreOptions() {
        var properties = new Gb28181Properties();
        properties.setServerId("34020000002000000001");
        properties.setRealm("example.test");
        assertThat(properties.toOptions().maxRecordItems()).isEqualTo(10000);
        for (int valid : new int[] {1, 37, 100000}) {
            properties.setMaxRecordItems(valid);
            assertThat(properties.toOptions().maxRecordItems()).isEqualTo(valid);
        }
        for (int invalid : new int[] {0, 100001}) {
            properties.setMaxRecordItems(invalid);
            org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                    .isThrownBy(properties::toOptions)
                    .withMessage("maxRecordItems outside supported range");
        }
    }

    @Test
    void bindsAndValidatesPlaybackLimits() {
        var properties = new Gb28181Properties();
        properties.setServerId("34020000002000000001");
        properties.setRealm("example.test");
        properties.setInviteTimeout(Duration.ofSeconds(12));
        properties.setStopTimeout(Duration.ofSeconds(3));
        properties.setMaxPlaySessions(17);
        assertThat(properties.toOptions().inviteTimeout()).isEqualTo(Duration.ofSeconds(12));
        assertThat(properties.toOptions().stopTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.toOptions().maxPlaySessions()).isEqualTo(17);
        properties.setMaxPlaySessions(0);
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException().isThrownBy(properties::toOptions);
    }

    @Test
    void shouldUseClosedLoopbackAndBoundedDefaults() {
        Gb28181Properties properties = new Gb28181Properties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getServerId()).isNull();
        assertThat(properties.getRealm()).isNull();
        assertThat(properties.getBindAddress()).isEqualTo("127.0.0.1");
        assertThat(properties.getAdvertisedAddress()).isEqualTo("127.0.0.1");
        assertThat(properties.getPort()).isEqualTo(5060);
        assertThat(properties.getRegistrationTtl()).isEqualTo(Duration.ofDays(1));
        assertThat(properties.getHeartbeatInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.getHeartbeatMisses()).isEqualTo(3);
        assertThat(properties.getQueryTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.getMaxDevices()).isEqualTo(1000);
        assertThat(properties.getMaxPendingQueries()).isEqualTo(256);
        assertThat(properties.getMaxPendingAlarms()).isEqualTo(256);
        assertThat(properties.getMaxCatalogItems()).isEqualTo(10000);
        assertThat(properties.getMaxRecordItems()).isEqualTo(10000);
        assertThat(properties.getMaxMessageBytes()).isEqualTo(65536);
    }

    @Test
    void configuredPropertiesShouldBuildEquivalentCoreOptions() {
        Gb28181Properties properties = new Gb28181Properties();
        properties.setServerId("34020000002000000001");
        properties.setRealm("example.test");
        properties.setBindAddress("127.0.0.2");
        properties.setAdvertisedAddress("127.0.0.3");
        properties.setPort(15060);
        properties.setRegistrationTtl(Duration.ofHours(12));
        properties.setHeartbeatInterval(Duration.ofSeconds(30));
        properties.setHeartbeatMisses(5);
        properties.setQueryTimeout(Duration.ofSeconds(8));
        properties.setMaxDevices(500);
        properties.setMaxPendingQueries(64);
        properties.setMaxPendingAlarms(23);
        properties.setMaxAlarmSubscriptions(29);
        properties.setMaxCatalogItems(2000);
        properties.setMaxMessageBytes(32768);

        GbServerOptions options = properties.toOptions();

        assertThat(options).isEqualTo(new GbServerOptions(
                "34020000002000000001",
                "example.test",
                "127.0.0.2",
                "127.0.0.3",
                15060,
                Duration.ofHours(12),
                Duration.ofSeconds(30),
                5,
                Duration.ofSeconds(8),
                500,
                64,
                2000,
                32768, Duration.ofSeconds(10), Duration.ofSeconds(5), 256, 10000, 23, 29));
    }
}
