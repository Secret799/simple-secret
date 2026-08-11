package com.ss.consumer.sensitive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ss.sensitive.annotation.Sensitive;
import com.ss.sensitive.core.SensitiveService;
import com.ss.sensitive.core.SensitiveStrategy;
import com.ss.sensitive.jackson.SimpleSecretSensitiveModule;
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

import static org.assertj.core.api.Assertions.assertThat;

/** 验证仓库外 Spring Boot 应用可通过 BOM 无版本使用 sensitive starter。 */
class SensitiveStarterConsumerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @Test
    void shouldUseBomManagedStarterWithoutExplicitVersion() throws Exception {
        Element project = parsePom(Path.of("pom.xml"));
        Element dependency = dependency(project, "com.ss",
                "simple-secret-springboot-starter-sensitive");

        assertThat(text(dependency, "version")).isNull();
    }

    @Test
    void shouldDiscoverAutoConfigurationAndMaskByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(SensitiveService.class);
            assertThat(context).hasSingleBean(SimpleSecretSensitiveModule.class);
            JsonNode json = context.getBean(ObjectMapper.class)
                    .valueToTree(new CustomerView("18049531999"));

            assertThat(json.get("phone").asText()).isEqualTo("180****1999");
        });
    }

    @Test
    void shouldHonorConsumerSensitiveDecision() {
        runner.withUserConfiguration(PlainTextPolicyConfiguration.class)
                .run(context -> {
                    JsonNode json = context.getBean(ObjectMapper.class)
                            .valueToTree(new CustomerView("18049531999"));

                    assertThat(json.get("phone").asText()).isEqualTo("18049531999");
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
    }

    @Configuration(proxyBeanMethods = false)
    static class PlainTextPolicyConfiguration {

        @Bean
        SensitiveService sensitiveService() {
            return (roleKey, perms) -> false;
        }
    }

    private record CustomerView(
            @Sensitive(strategy = SensitiveStrategy.PHONE) String phone) {
    }
}
