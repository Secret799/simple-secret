package com.ss.gb28181.autoconfigure;

import com.ss.gb28181.DeviceCredentials;
import com.ss.gb28181.Gb28181Server;
import com.ss.gb28181.GbAlarmListener;
import com.ss.gb28181.GbAlarmSubscriptionRequest;
import com.ss.gb28181.GbCatalogListener;
import com.ss.gb28181.GbCatalogSubscriptionRequest;
import com.ss.gb28181.GbMobilePositionListener;
import com.ss.gb28181.GbMobilePositionSubscriptionRequest;
import com.ss.gb28181.GbServerOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.net.BindException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleSecretGb28181AutoConfigurationTest {
    private static final String SERVER_ID = "34020000002000000001";
    private static final String REALM = "example.test";
    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleSecretGb28181AutoConfiguration.class));

    @Test
    void shouldRemainDisabledByDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(Gb28181Properties.class);
            assertThat(context).doesNotHaveBean(Gb28181Server.class);
        });
    }

    @Test
    void enabledServerShouldFailWhenDeviceCredentialsAreMissing() {
        runner.withPropertyValues(validProperties(freePort()))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(NoSuchBeanDefinitionException.class);
                });
    }

    @Test
    void enabledServerShouldRejectInvalidCoreOptions() {
        runner.withUserConfiguration(CredentialsConfiguration.class)
                .withPropertyValues(
                        "simple-secret.gb28181.enabled=true",
                        "simple-secret.gb28181.server-id=" + SERVER_ID,
                        "simple-secret.gb28181.realm=" + REALM,
                        "simple-secret.gb28181.port=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasRootCauseMessage("port outside supported range");
                });
    }

    @Test
    @Timeout(15)
    void enabledServerShouldBindTcpAndUdpAndReleaseBothOnContextClose() {
        int port = freePort();

        runner.withUserConfiguration(CredentialsConfiguration.class)
                .withPropertyValues(validProperties(port))
                .withPropertyValues("simple-secret.gb28181.max-record-items=37")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(Gb28181Server.class);
                    assertThat(context.getBean(Gb28181Properties.class).toOptions().maxRecordItems()).isEqualTo(37);
                    assertTcpUnavailable(port);
                    assertUdpUnavailable(port);
                });

        assertPortAvailable(port);
    }

    @Test
    @Timeout(15)
    void enabledServerShouldAcceptSingleOptionalAlarmListener() {
        int port = freePort();

        runner.withUserConfiguration(CredentialsConfiguration.class, AlarmListenerConfiguration.class)
                .withPropertyValues(validProperties(port))
                .withPropertyValues("simple-secret.gb28181.max-pending-alarms=17")
                .withPropertyValues("simple-secret.gb28181.max-alarm-subscriptions=19")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(Gb28181Server.class);
                    assertThat(context.getBean(Gb28181Properties.class).toOptions().maxPendingAlarms()).isEqualTo(17);
                    assertThat(context.getBean(Gb28181Properties.class).toOptions().maxAlarmSubscriptions()).isEqualTo(19);
                    assertThatThrownBy(() -> context.getBean(Gb28181Server.class).subscribeAlarms(
                            SERVER_ID, SERVER_ID, GbAlarmSubscriptionRequest.all(Duration.ofMinutes(5))))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("Device is not registered and online");
                });

        assertPortAvailable(port);
    }

    @Test
    @Timeout(15)
    void enabledServerShouldAcceptSingleOptionalCatalogListener() {
        int port = freePort();

        runner.withUserConfiguration(CredentialsConfiguration.class, CatalogListenerConfiguration.class)
                .withPropertyValues(validProperties(port))
                .withPropertyValues("simple-secret.gb28181.max-catalog-subscriptions=23")
                .withPropertyValues("simple-secret.gb28181.max-pending-catalog-notifications=29")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(Gb28181Properties.class).toOptions().maxCatalogSubscriptions()).isEqualTo(23);
                    assertThat(context.getBean(Gb28181Properties.class).toOptions().maxPendingCatalogNotifications()).isEqualTo(29);
                    assertThatThrownBy(() -> context.getBean(Gb28181Server.class).subscribeCatalog(
                            SERVER_ID, GbCatalogSubscriptionRequest.all(Duration.ofMinutes(5))))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("Device is not registered and online");
                });

        assertPortAvailable(port);
    }

    @Test
    @Timeout(15)
    void enabledServerShouldAcceptSingleOptionalMobilePositionListener() {
        int port = freePort();

        runner.withUserConfiguration(CredentialsConfiguration.class, MobilePositionListenerConfiguration.class)
                .withPropertyValues(validProperties(port))
                .withPropertyValues("simple-secret.gb28181.max-mobile-position-subscriptions=31")
                .withPropertyValues("simple-secret.gb28181.max-pending-mobile-position-notifications=37")
                .withPropertyValues("simple-secret.gb28181.max-mobile-position-items=43")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var options = context.getBean(Gb28181Properties.class).toOptions();
                    assertThat(options.maxMobilePositionSubscriptions()).isEqualTo(31);
                    assertThat(options.maxPendingMobilePositionNotifications()).isEqualTo(37);
                    assertThat(options.maxMobilePositionItems()).isEqualTo(43);
                    assertThatThrownBy(() -> context.getBean(Gb28181Server.class).subscribeMobilePosition(
                            SERVER_ID, new GbMobilePositionSubscriptionRequest(Duration.ofMinutes(5))))
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("Device is not registered and online");
                });

        assertPortAvailable(port);
    }

    @Test
    @Timeout(15)
    void enabledServerWithoutMobilePositionListenerShouldRejectPositionSubscriptions() {
        int port = freePort();

        runner.withUserConfiguration(CredentialsConfiguration.class)
                .withPropertyValues(validProperties(port))
                .run(context -> assertThatThrownBy(() -> context.getBean(Gb28181Server.class)
                        .subscribeMobilePosition(SERVER_ID,
                                new GbMobilePositionSubscriptionRequest(Duration.ofMinutes(5))))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("MobilePosition subscription requires a MobilePosition listener"));

        assertPortAvailable(port);
    }

    @Test
    @Timeout(15)
    void enabledServerShouldWireAlarmAndCatalogListenersTogether() {
        int port = freePort();

        runner.withUserConfiguration(CredentialsConfiguration.class, AlarmListenerConfiguration.class,
                        CatalogListenerConfiguration.class)
                .withPropertyValues(validProperties(port))
                .run(context -> {
                    Gb28181Server server = context.getBean(Gb28181Server.class);
                    assertThatThrownBy(() -> server.subscribeAlarms(
                            SERVER_ID, SERVER_ID, GbAlarmSubscriptionRequest.all(Duration.ofMinutes(5))))
                            .hasMessage("Device is not registered and online");
                    assertThatThrownBy(() -> server.subscribeCatalog(
                            SERVER_ID, GbCatalogSubscriptionRequest.all(Duration.ofMinutes(5))))
                            .hasMessage("Device is not registered and online");
                });

        assertPortAvailable(port);
    }

    @Test
    @Timeout(15)
    void enabledServerShouldWireAllThreeListenersTogether() {
        int port = freePort();

        runner.withUserConfiguration(CredentialsConfiguration.class, AlarmListenerConfiguration.class,
                        CatalogListenerConfiguration.class, MobilePositionListenerConfiguration.class)
                .withPropertyValues(validProperties(port))
                .run(context -> {
                    Gb28181Server server = context.getBean(Gb28181Server.class);
                    assertThatThrownBy(() -> server.subscribeAlarms(
                            SERVER_ID, SERVER_ID, GbAlarmSubscriptionRequest.all(Duration.ofMinutes(5))))
                            .hasMessage("Device is not registered and online");
                    assertThatThrownBy(() -> server.subscribeCatalog(
                            SERVER_ID, GbCatalogSubscriptionRequest.all(Duration.ofMinutes(5))))
                            .hasMessage("Device is not registered and online");
                    assertThatThrownBy(() -> server.subscribeMobilePosition(
                            SERVER_ID, new GbMobilePositionSubscriptionRequest(Duration.ofMinutes(5))))
                            .hasMessage("Device is not registered and online");
                });

        assertPortAvailable(port);
    }

    @Test
    void enabledServerShouldRejectInvalidAlarmSubscriptionCapacity() {
        runner.withUserConfiguration(CredentialsConfiguration.class)
                .withPropertyValues(validProperties(freePort()))
                .withPropertyValues("simple-secret.gb28181.max-alarm-subscriptions=10001")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasStackTraceContaining("maxAlarmSubscriptions outside supported range");
                });
    }

    @Test
    void enabledServerShouldFailForAmbiguousAlarmListeners() {
        runner.withUserConfiguration(CredentialsConfiguration.class, AmbiguousAlarmListenersConfiguration.class)
                .withPropertyValues(validProperties(freePort()))
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(
                            org.springframework.beans.factory.NoUniqueBeanDefinitionException.class);
                });
    }

    @Test
    void enabledServerShouldRejectInvalidRecordCapacity() {
        runner.withUserConfiguration(CredentialsConfiguration.class)
                .withPropertyValues(validProperties(freePort()))
                .withPropertyValues("simple-secret.gb28181.max-record-items=100001")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalArgumentException.class)
                            .hasRootCauseMessage("maxRecordItems outside supported range");
                });
    }

    @Test
    @Timeout(15)
    void customServerShouldBackOffWithoutCredentialsOrValidAutoConfigurationOptions() {
        int port = freePort();

        runner.withUserConfiguration(CustomServerConfiguration.class)
                .withPropertyValues(
                        "simple-secret.gb28181.enabled=true",
                        "simple-secret.gb28181.server-id=invalid",
                        "test.gb28181.port=" + port)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(Gb28181Server.class);
                    assertThat(context.getBean(Gb28181Server.class))
                            .isSameAs(context.getBean("customGb28181Server"));
                });

        assertPortAvailable(port);
    }

    private static String[] validProperties(int port) {
        return new String[] {
                "simple-secret.gb28181.enabled=true",
                "simple-secret.gb28181.server-id=" + SERVER_ID,
                "simple-secret.gb28181.realm=" + REALM,
                "simple-secret.gb28181.bind-address=127.0.0.1",
                "simple-secret.gb28181.advertised-address=127.0.0.1",
                "simple-secret.gb28181.port=" + port
        };
    }

    private static int freePort() {
        try (ServerSocket tcp = new ServerSocket()) {
            tcp.bind(new InetSocketAddress(LOOPBACK, 0));
            int port = tcp.getLocalPort();
            try (DatagramSocket udp = new DatagramSocket(null)) {
                udp.bind(new InetSocketAddress(LOOPBACK, port));
            }
            return port;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to find a free TCP/UDP port", exception);
        }
    }

    private static void assertTcpUnavailable(int port) {
        assertThatThrownBy(() -> {
            try (ServerSocket socket = new ServerSocket()) {
                socket.bind(new InetSocketAddress(LOOPBACK, port));
            }
        }).isInstanceOf(BindException.class);
    }

    private static void assertUdpUnavailable(int port) {
        assertThatThrownBy(() -> {
            try (DatagramSocket socket = new DatagramSocket(null)) {
                socket.bind(new InetSocketAddress(LOOPBACK, port));
            }
        }).isInstanceOf(BindException.class);
    }

    private static void assertPortAvailable(int port) {
        try (ServerSocket tcp = new ServerSocket(); DatagramSocket udp = new DatagramSocket(null)) {
            tcp.bind(new InetSocketAddress(LOOPBACK, port));
            udp.bind(new InetSocketAddress(LOOPBACK, port));
        } catch (IOException exception) {
            throw new AssertionError("GB28181 TCP/UDP port was not released: " + port, exception);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CredentialsConfiguration {
        @Bean
        DeviceCredentials deviceCredentials() {
            return deviceId -> Optional.of("test-only-secret");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AlarmListenerConfiguration {
        @Bean
        GbAlarmListener alarmListener() {
            return event -> { };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CatalogListenerConfiguration {
        @Bean
        GbCatalogListener catalogListener() {
            return event -> { };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MobilePositionListenerConfiguration {
        @Bean
        GbMobilePositionListener mobilePositionListener() {
            return event -> { };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AmbiguousAlarmListenersConfiguration {
        @Bean
        GbAlarmListener firstAlarmListener() {
            return event -> { };
        }

        @Bean
        GbAlarmListener secondAlarmListener() {
            return event -> { };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomServerConfiguration {
        @Bean(destroyMethod = "close")
        Gb28181Server customGb28181Server(Environment environment) {
            int port = environment.getRequiredProperty("test.gb28181.port", Integer.class);
            GbServerOptions options = GbServerOptions.builder()
                    .serverId(SERVER_ID)
                    .realm(REALM)
                    .bindAddress("127.0.0.1")
                    .advertisedAddress("127.0.0.1")
                    .port(port)
                    .build();
            return Gb28181Server.open(options, deviceId -> Optional.empty());
        }
    }
}
