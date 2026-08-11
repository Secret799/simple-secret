package com.ss.core.config;

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

/** 验证 core starter 的发布依赖边界。 */
class CoreStarterPublishedDependencyPolicyTest {

    @Test
    void shouldPublishOnlyMinimalCoreAndSpringDependencies() throws Exception {
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
                "org.springframework:spring-context",
                "jakarta.validation:jakarta.validation-api",
                "org.hibernate.validator:hibernate-validator");
        assertThat(production.stream()
                .filter(element -> optionalText(element, "groupId").equals("jakarta.validation")
                        || optionalText(element, "groupId").equals("org.hibernate.validator")))
                .allMatch(element -> "true".equals(optionalText(element, "optional")));
        assertThat(coordinates(production)).noneMatch(coordinate ->
                coordinate.contains("honeybee")
                        || coordinate.contains("spring-web")
                        || coordinate.contains("servlet")
                        || coordinate.contains("spring-boot-starter-aop")
                        || coordinate.contains("hutool")
                        || coordinate.contains("lombok")
                        || coordinate.contains("jackson")
                        || coordinate.contains("transmittable-thread-local"));

        assertThat(Files.readString(Path.of("../pom.xml")))
                .contains("<module>simple-secret-springboot-starter-core</module>");
        assertThat(Files.readString(Path.of("../../simple-secret-common/simple-secret-common-bom/pom.xml")))
                .contains("<artifactId>simple-secret-springboot-starter-core</artifactId>");
        assertThat(Files.readString(Path.of("../../pom.xml")))
                .contains("<artifactId>simple-secret-springboot-starter-core</artifactId>");
    }

    private static List<String> coordinates(List<Element> elements) {
        return elements.stream()
                .map(element -> optionalText(element, "groupId") + ":" + optionalText(element, "artifactId"))
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

    private static String optionalText(Element parent, String name) {
        return directChildren(parent, name).stream()
                .findFirst()
                .map(element -> element.getTextContent().trim())
                .orElse("");
    }
}
