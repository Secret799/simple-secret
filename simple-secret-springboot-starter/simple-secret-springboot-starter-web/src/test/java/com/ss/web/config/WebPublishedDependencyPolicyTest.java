package com.ss.web.config;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 Web starter 发布给消费者的依赖边界。 */
class WebPublishedDependencyPolicyTest {

    @Test
    void shouldPublishOnlyMinimalWebDependencies() throws Exception {
        Document module = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(Path.of("pom.xml").toFile());
        Element dependencies = directChild(module.getDocumentElement(), "dependencies");
        List<Element> production = directChildren(dependencies, "dependency").stream()
                .filter(element -> !"test".equals(optionalText(element, "scope")))
                .toList();

        assertThat(coordinates(production)).containsExactlyInAnyOrder(
                "com.ss:simple-secret-common-core",
                "org.springframework.boot:spring-boot-autoconfigure",
                "org.springframework:spring-webmvc",
                "jakarta.servlet:jakarta.servlet-api",
                "jakarta.validation:jakarta.validation-api");
        assertThat(production)
                .filteredOn(element -> "jakarta.servlet:jakarta.servlet-api".equals(coordinate(element)))
                .allMatch(element -> "provided".equals(optionalText(element, "scope")));
        assertThat(production)
                .filteredOn(element -> "jakarta.validation:jakarta.validation-api"
                        .equals(coordinate(element)))
                .allMatch(element -> "true".equals(optionalText(element, "optional")));
        assertThat(coordinates(production)).doesNotContain(
                "org.hibernate.validator:hibernate-validator",
                "org.springframework.boot:spring-boot-starter-validation");

        String pom = Files.readString(Path.of("pom.xml"));
        for (String forbiddenDependency : List.of(
                "spring-boot-starter-web", "spring-boot-starter-actuator",
                "tomcat", "jetty", "undertow", "simple-secret-springboot-starter-json",
                "simple-secret-springboot-starter-core", "simple-secret-common-toolbox",
                "hutool", "transmittable-thread-local", "captcha", "lombok", "com.secret")) {
            assertThat(pom).doesNotContain(forbiddenDependency);
        }
    }

    private static List<String> coordinates(List<Element> elements) {
        return elements.stream().map(WebPublishedDependencyPolicyTest::coordinate).toList();
    }

    private static String coordinate(Element element) {
        return optionalText(element, "groupId") + ":" + optionalText(element, "artifactId");
    }

    private static Element directChild(Element parent, String name) {
        return directChildren(parent, name).stream().findFirst().orElseThrow();
    }

    private static List<Element> directChildren(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        List<Element> matches = new ArrayList<>();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && name.equals(element.getTagName())) {
                matches.add(element);
            }
        }
        return matches;
    }

    private static String optionalText(Element parent, String name) {
        return directChildren(parent, name).stream()
                .findFirst()
                .map(element -> element.getTextContent().trim())
                .orElse("");
    }
}
