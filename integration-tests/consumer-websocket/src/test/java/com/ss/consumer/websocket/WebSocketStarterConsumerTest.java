package com.ss.consumer.websocket;

import com.ss.websocket.handler.AbstractAnonymousWebSocketHandler;
import com.ss.websocket.message.WebSocketMessenger;
import com.ss.websocket.session.WebSocketSessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证仓库外 Servlet 应用可通过 BOM 无版本使用 websocket starter。 */
class WebSocketStarterConsumerTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @Test
    void shouldUseBomManagedStarterWithoutExplicitVersion() throws Exception {
        Element project = parsePom(Path.of("pom.xml"));
        Element dependency = dependency(project, "com.ss",
                "simple-secret-springboot-starter-websocket");

        assertThat(text(dependency, "version")).isNull();
    }

    @Test
    void shouldRemainDisabledUntilConsumerOptsIn() {
        runner.run(context ->
                assertThat(context).doesNotHaveBean(WebSocketSessionRegistry.class));
    }

    @Test
    void shouldDiscoverAutoConfigurationAndRegisterConsumerHandler() {
        runner.withPropertyValues("simple-secret.websocket.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(WebSocketSessionRegistry.class);
                    assertThat(context).hasSingleBean(WebSocketMessenger.class);
                    assertThat(context).hasSingleBean(AbstractAnonymousWebSocketHandler.class);
                });
    }

    private static Element parsePom(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
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
        throw new AssertionError("Missing dependency: " + groupId + ":" + artifactId);
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
        AbstractAnonymousWebSocketHandler eventHandler() {
            return new AbstractAnonymousWebSocketHandler("/events") { };
        }
    }
}
