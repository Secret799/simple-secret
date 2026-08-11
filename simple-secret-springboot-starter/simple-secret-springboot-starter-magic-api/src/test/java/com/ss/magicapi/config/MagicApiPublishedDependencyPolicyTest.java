package com.ss.magicapi.config;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 Magic API starter 对第三方消费者发布的依赖边界。 */
class MagicApiPublishedDependencyPolicyTest {

    @Test
    void shouldPublishOnlyCoreMagicApiIntegrationDependencies() throws Exception {
        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(Path.of("pom.xml").toFile());
        Element dependencies = directChild(document.getDocumentElement(), "dependencies");
        List<Element> dependencyElements = directChildren(dependencies, "dependency");

        assertThat(coordinates(dependencyElements)).contains(
                "org.ssssssss:magic-api-spring-boot-starter",
                "org.springframework.boot:spring-boot-autoconfigure",
                "org.apache.commons:commons-lang3",
                "org.apache.commons:commons-compress");
        assertThat(coordinates(dependencyElements)).noneMatch(coordinate ->
                coordinate.contains("honeybee")
                        || coordinate.contains("mybatis-plus")
                        || coordinate.contains("magic-api-plugin-task")
                        || coordinate.contains("magic-api-plugin-springdoc")
                        || coordinate.contains("simple-secret-springboot-starter-json"));

        Element magicApiDependency = dependencyElements.stream()
                .filter(element -> "magic-api-spring-boot-starter".equals(text(element, "artifactId")))
                .findFirst()
                .orElseThrow();
        Element exclusions = directChild(magicApiDependency, "exclusions");

        assertThat(coordinates(directChildren(exclusions, "exclusion"))).contains(
                "org.ssssssss:magic-api-servlet-javaee",
                "commons-beanutils:commons-beanutils",
                "commons-io:commons-io",
                "org.apache.commons:commons-text");
    }

    private static List<String> coordinates(List<Element> elements) {
        return elements.stream()
                .map(element -> text(element, "groupId") + ":" + text(element, "artifactId"))
                .toList();
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

    private static String text(Element parent, String name) {
        return directChild(parent, name).getTextContent().trim();
    }
}
