package com.ss.consumer.gb28181;

import com.ss.gb28181.CatalogItem;
import com.ss.gb28181.DeviceCredentials;
import com.ss.gb28181.Gb28181Server;
import com.ss.gb28181.GbAlarm;
import com.ss.gb28181.GbAlarmResetCommand;
import com.ss.gb28181.GbAlarmEvent;
import com.ss.gb28181.GbAlarmListener;
import com.ss.gb28181.GbAlarmSubscription;
import com.ss.gb28181.GbAlarmSubscriptionRequest;
import com.ss.gb28181.GbCatalogEvent;
import com.ss.gb28181.GbCatalogListener;
import com.ss.gb28181.GbCatalogNotification;
import com.ss.gb28181.GbCatalogSubscription;
import com.ss.gb28181.GbCatalogSubscriptionRequest;
import com.ss.gb28181.GbDownloadRequest;
import com.ss.gb28181.GbMobilePosition;
import com.ss.gb28181.GbMobilePositionEvent;
import com.ss.gb28181.GbMobilePositionListener;
import com.ss.gb28181.GbMobilePositionNotification;
import com.ss.gb28181.GbMobilePositionSubscription;
import com.ss.gb28181.GbMobilePositionSubscriptionRequest;
import com.ss.gb28181.GbPlaybackControl;
import com.ss.gb28181.GbPlaybackRange;
import com.ss.gb28181.GbPlaySession;
import com.ss.gb28181.GbRtpTarget;
import com.ss.gb28181.GbServerOptions;
import com.ss.gb28181.GbDragZoomCommand;
import com.ss.gb28181.RegisteredDevice;
import com.ss.gb28181.RecordItem;
import com.ss.gb28181.RecordQuery;
import com.ss.gb28181.autoconfigure.Gb28181Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证仓库外应用只依赖 GB28181 starter 即可使用公开 API 和自动配置。 */
class Gb28181StarterConsumerTest {
    @Test
    void shouldExposeDragZoomControlsThroughStarterDependency() throws Exception {
        assertThat(Gb28181Server.class.getMethod("dragZoomIn", String.class, String.class, GbDragZoomCommand.class)).isNotNull();
        assertThat(Gb28181Server.class.getMethod("dragZoomOut", String.class, String.class, GbDragZoomCommand.class)).isNotNull();
    }
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @Test
    void shouldExposeAlarmResetAndKeyFrameControlsThroughStarterDependency() throws Exception {
        GbAlarmResetCommand all = GbAlarmResetCommand.all();
        var source = new java.util.HashSet<>(Set.of(6));
        GbAlarmResetCommand filtered = new GbAlarmResetCommand(source, 2);
        source.clear();

        assertThat(all.methods()).isEmpty();
        assertThat(all.type()).isNull();
        assertThat(filtered.methods()).containsExactly(6);
        assertThat(filtered.type()).isEqualTo(2);
        assertThatThrownBy(() -> filtered.methods().add(1)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(Gb28181Server.class.getMethod("resetAlarm", String.class, String.class,
                GbAlarmResetCommand.class).getGenericReturnType().getTypeName())
                .isEqualTo(CompletableFuture.class.getName() + "<" + Void.class.getName() + ">");
        assertThat(Gb28181Server.class.getMethod("requestKeyFrame", String.class, String.class)
                .getGenericReturnType().getTypeName())
                .isEqualTo(CompletableFuture.class.getName() + "<" + Void.class.getName() + ">");
    }

    @Test
    void shouldExposeMobilePositionApiAndStarterProperties() throws Exception {
        GbMobilePositionSubscriptionRequest request =
                new GbMobilePositionSubscriptionRequest(Duration.ofMinutes(5));
        GbMobilePosition position = new GbMobilePosition("34020000001320000001",
                "2026-09-15T10:30:00+08:00", 121.5, 31.2, 12.5, 90.0, 8.0, 3.0);
        GbMobilePositionNotification notification = new GbMobilePositionNotification(
                SERVER_ID, 7, "2026-09-15T10:30:01+08:00", 1, List.of(position));
        GbMobilePositionEvent event = new GbMobilePositionEvent(SERVER_ID, "position-call",
                Instant.parse("2026-09-15T02:30:02Z"), notification);
        AtomicReference<GbMobilePositionEvent> received = new AtomicReference<>();
        GbMobilePositionListener listener = received::set;
        Gb28181Properties properties = new Gb28181Properties();

        listener.onMobilePosition(event);

        assertThat(received.get()).isSameAs(event);
        assertThat(request.interval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(event.notification().positions()).containsExactly(position);
        assertThat(properties.getMaxMobilePositionSubscriptions()).isEqualTo(256);
        assertThat(properties.getMaxPendingMobilePositionNotifications()).isEqualTo(256);
        assertThat(properties.getMaxMobilePositionItems()).isEqualTo(1000);
        assertThat(Gb28181Server.class.getMethod("subscribeMobilePosition", String.class,
                GbMobilePositionSubscriptionRequest.class).getGenericReturnType().getTypeName())
                .isEqualTo(CompletableFuture.class.getName() + "<" + GbMobilePositionSubscription.class.getName() + ">");
        assertThat(GbMobilePositionSubscription.class.getMethod("stop").getGenericReturnType().getTypeName())
                .isEqualTo(CompletableFuture.class.getName() + "<" + Void.class.getName() + ">");
        assertThat(GbMobilePositionSubscription.class.getMethod("deviceId").getReturnType())
                .isEqualTo(String.class);
        assertThat(GbMobilePositionSubscription.class.getMethod("callId").getReturnType())
                .isEqualTo(String.class);
        assertThat(GbMobilePositionSubscription.class.getMethod("request").getReturnType())
                .isEqualTo(GbMobilePositionSubscriptionRequest.class);
        assertThat(GbMobilePositionSubscription.class.getMethod("completion").getGenericReturnType().getTypeName())
                .isEqualTo(CompletionStage.class.getName() + "<" + Void.class.getName() + ">");
        assertThat(AutoCloseable.class).isAssignableFrom(GbMobilePositionSubscription.class);
        assertThat(Gb28181Server.class.getMethod("open", GbServerOptions.class, DeviceCredentials.class,
                GbAlarmListener.class, GbCatalogListener.class, GbMobilePositionListener.class)).isNotNull();
    }

    @Test
    void shouldExposeCatalogSubscriptionApiThroughStarterDependency() throws Exception {
        GbCatalogSubscriptionRequest request = GbCatalogSubscriptionRequest.all(Duration.ofMinutes(5));
        CatalogItem item = new CatalogItem("34020000001320000001", "camera", SERVER_ID, "ON");
        GbCatalogNotification notification = new GbCatalogNotification(SERVER_ID, 7, 1,
                List.of(new GbCatalogNotification.Entry(item, GbCatalogNotification.Type.ADD)));
        GbCatalogEvent event = new GbCatalogEvent(SERVER_ID, "catalog-call",
                Instant.parse("2026-09-15T02:30:00Z"), notification);
        GbCatalogListener catalogListener = received -> { };

        assertThat(event.notification().entries().get(0).type()).isEqualTo(GbCatalogNotification.Type.ADD);
        assertThat(event.callId()).isEqualTo("catalog-call");
        assertThat(GbServerOptions.builder().serverId(SERVER_ID).realm("example.test")
                .maxCatalogSubscriptions(31).maxPendingCatalogNotifications(37).build()
                .maxPendingCatalogNotifications()).isEqualTo(37);
        assertThat(Gb28181Server.class.getMethod("subscribeCatalog", String.class,
                GbCatalogSubscriptionRequest.class).getGenericReturnType().getTypeName())
                .isEqualTo(CompletableFuture.class.getName() + "<" + GbCatalogSubscription.class.getName() + ">");
        assertThat(GbCatalogSubscription.class.getMethod("stop").getGenericReturnType().getTypeName())
                .isEqualTo(CompletableFuture.class.getName() + "<" + Void.class.getName() + ">");
        assertThat(AutoCloseable.class).isAssignableFrom(GbCatalogSubscription.class);
        assertThat(Gb28181Server.class.getMethod("open", GbServerOptions.class,
                DeviceCredentials.class, GbAlarmListener.class, GbCatalogListener.class)).isNotNull();
        assertThat(catalogListener).isNotNull();

        GbServerOptions oldOptions = new GbServerOptions(SERVER_ID, "example.test", "127.0.0.1", "127.0.0.1",
                5060, Duration.ofDays(1), Duration.ofSeconds(60), 3, Duration.ofSeconds(10), 1000, 256,
                10000, 65536, Duration.ofSeconds(10), Duration.ofSeconds(5), 256, 10000, 256, 256);
        assertThat(oldOptions.maxCatalogSubscriptions()).isEqualTo(256);
        assertThat(oldOptions.maxPendingCatalogNotifications()).isEqualTo(256);
    }

    @Test
    void shouldExposeAlarmSubscriptionApiThroughStarterDependency() throws Exception {
        GbAlarmSubscriptionRequest request = GbAlarmSubscriptionRequest.all(Duration.ofMinutes(5));

        assertThat(request.methods()).isEqualTo(Set.of());
        assertThat(GbServerOptions.builder().serverId(SERVER_ID).realm("example.test")
                .maxAlarmSubscriptions(31).build().maxAlarmSubscriptions()).isEqualTo(31);
        assertThat(Gb28181Server.class.getMethod("subscribeAlarms", String.class, String.class,
                GbAlarmSubscriptionRequest.class).getGenericReturnType().getTypeName())
                .isEqualTo(CompletableFuture.class.getName() + "<" + GbAlarmSubscription.class.getName() + ">");
        assertThat(GbAlarmSubscription.class.getMethod("stop").getGenericReturnType().getTypeName())
                .isEqualTo(CompletableFuture.class.getName() + "<" + Void.class.getName() + ">");
        assertThat(AutoCloseable.class).isAssignableFrom(GbAlarmSubscription.class);
    }

    @Test
    void exposesDownloadRequestAndSessionMetadata() throws Exception {
        Instant start = Instant.parse("2026-09-15T00:00:00Z");
        GbPlaybackRange range = new GbPlaybackRange(start, start.plusSeconds(3600));
        assertThat(GbDownloadRequest.normal(range).range()).isSameAs(range);
        assertThat(GbDownloadRequest.normal(range).speed()).isEqualTo(1);
        assertThat(new GbDownloadRequest(range, 128).speed()).isEqualTo(128);
        assertThatThrownBy(() -> GbDownloadRequest.normal(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GbDownloadRequest(range, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GbDownloadRequest(range, 129)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Gb28181Server.class.getMethod("download", String.class, String.class,
                GbRtpTarget.class, GbDownloadRequest.class).getGenericReturnType().getTypeName())
                .isEqualTo(CompletableFuture.class.getName() + "<" + GbPlaySession.class.getName() + ">");
        assertThat(GbPlaySession.class.getMethod("downloadRequest").getGenericReturnType().getTypeName())
                .isEqualTo(Optional.class.getName() + "<" + GbDownloadRequest.class.getName() + ">");
        assertThat(GbPlaySession.class.getMethod("downloadFileSize").getReturnType()).isEqualTo(OptionalLong.class);
    }

    @Test
    void shouldUseBomManagedStarterWithoutExplicitVersion() throws Exception {
        Element project = parsePom(Path.of("pom.xml"));
        Element dependency = dependency(project, "com.ss",
                "simple-secret-springboot-starter-gb28181");

        assertThat(text(dependency, "version")).isNull();
    }

    @Test
    void shouldExposeCorePublicApiThroughStarterDependency() throws Exception {
        DeviceCredentials credentials = deviceId -> Optional.empty();
        GbServerOptions options = GbServerOptions.builder()
                .serverId("34020000002000000001")
                .realm("example.test")
                .build();
        CatalogItem item = new CatalogItem("34020000001320000001", "camera", SERVER_ID, "ON");

        assertThat(credentials.passwordFor("unknown-device")).isEmpty();
        assertThat(options.queryTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(options.maxRecordItems()).isEqualTo(10000);
        assertThat(options.maxPendingAlarms()).isEqualTo(256);
        assertThat(options.maxAlarmSubscriptions()).isEqualTo(256);
        assertThat(item.status()).isEqualTo("ON");
        assertThat(Modifier.isPublic(RegisteredDevice.class.getModifiers())).isTrue();
        assertThat(Gb28181Server.class.getMethod("devices").getGenericReturnType().getTypeName())
                .contains(List.class.getName())
                .contains(RegisteredDevice.class.getName());
        assertThat(Gb28181Server.class.getMethod("queryCatalog", String.class)
                .getGenericReturnType().getTypeName())
                .contains(CompletableFuture.class.getName())
                .contains(CatalogItem.class.getName());
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 0, 0);
        assertThat(RecordQuery.all(start, start.plusHours(1)).type()).isEqualTo(RecordQuery.Type.ALL);
        assertThat(Gb28181Server.class.getMethod("queryRecordInfo", String.class, String.class, RecordQuery.class)
                .getGenericReturnType().getTypeName())
                .isEqualTo(CompletableFuture.class.getName() + "<" + List.class.getName()
                        + "<" + RecordItem.class.getName() + ">>");
        GbAlarm alarm = new GbAlarm("34020000001320000001", 7, 1, 2,
                "2026-09-15T10:30:00+08:00", null, null, null, null, null);
        GbAlarmEvent event = new GbAlarmEvent(SERVER_ID, Instant.parse("2026-09-15T02:30:00Z"), alarm);
        GbAlarmListener listener = received -> { };
        assertThat(event.alarm()).isSameAs(alarm);
        assertThat(listener).isNotNull();
        assertThat(Gb28181Server.class.getMethod("open", GbServerOptions.class,
                DeviceCredentials.class, GbAlarmListener.class)).isNotNull();
    }

    @Test
    void shouldExposeHistoryPlaybackThroughStarterDependency() throws Exception {
        Instant start = Instant.parse("2026-09-15T00:00:00Z");
        GbPlaybackRange range = new GbPlaybackRange(start, start.plusSeconds(3600));
        assertThat(range.endTime()).isEqualTo(Instant.parse("2026-09-15T01:00:00Z"));
        assertThat(GbPlaybackControl.pause().type()).isEqualTo(GbPlaybackControl.Type.PAUSE);
        assertThat(GbPlaybackControl.resume().type()).isEqualTo(GbPlaybackControl.Type.RESUME);
        assertThat(GbPlaybackControl.seek(Duration.ofSeconds(30)).position()).isEqualTo(Duration.ofSeconds(30));
        assertThat(GbPlaybackControl.speed(0.5).scale()).isEqualTo(0.5);
        assertThat(Gb28181Server.class.getMethod("playback", String.class, String.class,
                GbRtpTarget.class, GbPlaybackRange.class).getGenericReturnType().getTypeName())
                .isEqualTo(CompletableFuture.class.getName() + "<" + GbPlaySession.class.getName() + ">");
        assertThat(GbPlaySession.class.getMethod("playbackRange").getGenericReturnType().getTypeName())
                .isEqualTo(Optional.class.getName() + "<" + GbPlaybackRange.class.getName() + ">");
        assertThat(GbPlaySession.class.getMethod("control", GbPlaybackControl.class).getGenericReturnType().getTypeName())
                .isEqualTo(CompletableFuture.class.getName() + "<" + Void.class.getName() + ">");
    }

    @Test
    void shouldPublishImportsAndConfigurationMetadata() throws Exception {
        try (InputStream imports = resource(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(new String(imports.readAllBytes(), StandardCharsets.UTF_8))
                    .contains("com.ss.gb28181.autoconfigure.SimpleSecretGb28181AutoConfiguration");
        }
        String metadata = gb28181Metadata();
        assertThat(metadata)
                .contains("simple-secret.gb28181.enabled")
                .contains("simple-secret.gb28181.server-id")
                .contains("simple-secret.gb28181.max-record-items")
                .contains("simple-secret.gb28181.max-pending-alarms")
                .contains("simple-secret.gb28181.max-alarm-subscriptions")
                .contains("simple-secret.gb28181.max-catalog-subscriptions")
                .contains("simple-secret.gb28181.max-pending-catalog-notifications")
                .contains("simple-secret.gb28181.max-mobile-position-subscriptions")
                .contains("simple-secret.gb28181.max-pending-mobile-position-notifications")
                .contains("simple-secret.gb28181.max-mobile-position-items")
                .contains("simple-secret.gb28181.max-message-bytes");
    }

    @Test
    void shouldRemainDisabledWithoutExplicitOptIn() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(Gb28181Server.class);
        });
    }

    @Test
    void shouldNotBringZlmOrSpringWebOntoConsumerClasspath() {
        assertThatThrownBy(() -> Class.forName("com.ss.zlm4j.config.SimpleSecretZlmAutoConfiguration"))
                .isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("org.springframework.web.client.RestClient"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    private static InputStream resource(String path) {
        InputStream input = Gb28181StarterConsumerTest.class.getClassLoader().getResourceAsStream(path);
        assertThat(input).as(path).isNotNull();
        return input;
    }

    private static String gb28181Metadata() throws Exception {
        Enumeration<java.net.URL> resources = Gb28181StarterConsumerTest.class.getClassLoader()
                .getResources("META-INF/spring-configuration-metadata.json");
        while (resources.hasMoreElements()) {
            try (InputStream input = resources.nextElement().openStream()) {
                String metadata = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                if (metadata.contains("simple-secret.gb28181.enabled")) {
                    return metadata;
                }
            }
        }
        throw new AssertionError("Missing GB28181 configuration metadata");
    }

    private static Element parsePom(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
    }

    private static Element dependency(Element project, String groupId, String artifactId) {
        NodeList nodes = project.getElementsByTagName("dependency");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element dependency = (Element) nodes.item(index);
            if (groupId.equals(text(dependency, "groupId"))
                    && artifactId.equals(text(dependency, "artifactId"))) {
                return dependency;
            }
        }
        throw new AssertionError("Missing dependency: " + groupId + ':' + artifactId);
    }

    private static String text(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && tagName.equals(element.getTagName())) {
                return element.getTextContent().trim();
            }
        }
        return null;
    }

    private static final String SERVER_ID = "34020000002000000001";

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {
    }
}
