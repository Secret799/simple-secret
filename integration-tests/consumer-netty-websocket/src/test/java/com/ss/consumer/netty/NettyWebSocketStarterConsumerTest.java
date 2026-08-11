package com.ss.consumer.netty;

import com.ss.netty.auth.NettyWebSocketAuthenticator;
import com.ss.netty.auth.NettyWebSocketPrincipal;
import com.ss.netty.handler.NettyWebSocketMessageHandler;
import com.ss.netty.message.NettyWebSocketMessage;
import com.ss.netty.server.NettyWebSocketEndpointRegistry;
import com.ss.netty.server.NettyWebSocketServer;
import com.ss.netty.session.NettyWebSocketChannelRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证仓库外应用可通过 BOM 无版本使用 Netty WebSocket starter。 */
class NettyWebSocketStarterConsumerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @Test
    void shouldUseBomManagedStarterWithoutExplicitVersion() throws Exception {
        Element project = parsePom(Path.of("pom.xml"));
        Element dependency = dependency(project, "com.ss",
                "simple-secret-springboot-starter-netty-websocket");

        assertThat(text(dependency, "version")).isNull();
    }

    @Test
    void shouldRemainDisabledUntilConsumerOptsIn() {
        runner.run(context -> {
            assertThat(context).doesNotHaveBean(NettyWebSocketServer.class);
            assertThat(context).doesNotHaveBean(NettyWebSocketChannelRegistry.class);
        });
    }

    @Test
    void shouldDiscoverConsumerHandlerAndAuthenticatorWithoutServletStack() {
        runner.withPropertyValues(
                        "simple-secret.netty.websocket.enabled=true",
                        "simple-secret.netty.websocket.auto-startup=false",
                        "simple-secret.netty.websocket.endpoints.events.path=/events")
                .run(context -> {
                    assertThat(context).hasSingleBean(NettyWebSocketEndpointRegistry.class);
                    assertThat(context).hasSingleBean(NettyWebSocketChannelRegistry.class);
                    assertThat(context).hasSingleBean(NettyWebSocketServer.class);
                    assertThat(context).hasSingleBean(NettyWebSocketMessageHandler.class);
                    assertThat(context).hasSingleBean(NettyWebSocketAuthenticator.class);
                    assertThat(context.getBean(NettyWebSocketServer.class).isRunning()).isFalse();
                });
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

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {

        @Bean
        NettyWebSocketAuthenticator nettyWebSocketAuthenticator() {
            return request -> request.firstHeader("authorization")
                    .filter("Bearer test-token"::equals)
                    .map(ignored -> new NettyWebSocketPrincipal(
                            "user-42", "Alice", Map.of("tenantId", 7L)));
        }

        @Bean
        NettyWebSocketMessageHandler eventsHandler() {
            return new NettyWebSocketMessageHandler() {
                @Override
                public String path() {
                    return "/events";
                }

                @Override
                public void handle(NettyWebSocketMessage message) {
                    Optional<NettyWebSocketPrincipal> principal = message.principal();
                    assertThat(principal).isPresent();
                }
            };
        }
    }
}
