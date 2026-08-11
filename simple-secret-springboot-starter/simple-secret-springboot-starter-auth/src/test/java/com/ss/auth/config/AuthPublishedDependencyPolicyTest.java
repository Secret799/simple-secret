package com.ss.auth.config;

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

/** 验证 Auth starter 发布给消费者的依赖边界。 */
class AuthPublishedDependencyPolicyTest {

    @Test
    void shouldPublishOnlyMinimalAuthAndSpringDependencies() throws Exception {
        Document module = parsePom(Path.of("pom.xml"));
        Element dependencies = directChild(module.getDocumentElement(), "dependencies");
        List<Element> production = directChildren(dependencies, "dependency").stream()
                .filter(element -> !"test".equals(optionalText(element, "scope")))
                .toList();

        assertThat(coordinates(production)).containsExactlyInAnyOrder(
                "com.ss:simple-secret-common-core",
                "cn.dev33:sa-token-core",
                "org.springframework.boot:spring-boot-autoconfigure",
                "org.springframework:spring-context",
                "org.springframework:spring-webmvc");
        assertThat(production.stream()
                .filter(element -> coordinate(element).equals("org.springframework:spring-webmvc"))
                .allMatch(element -> "true".equals(optionalText(element, "optional"))))
                .isTrue();
        assertThat(coordinates(production)).noneMatch(coordinate ->
                coordinate.contains("redis")
                        || coordinate.contains("redisson")
                        || coordinate.contains("caffeine")
                        || coordinate.contains("jwt")
                        || coordinate.contains("hutool")
                        || coordinate.contains("lombok")
                        || coordinate.contains("validation")
                        || coordinate.contains("jackson")
                        || coordinate.contains("honeybee")
                        || coordinate.contains("sa-token-spring-boot3-starter"));

        assertThat(Files.readString(Path.of("../pom.xml")))
                .contains("<module>simple-secret-springboot-starter-auth</module>");
        assertThat(Files.readString(Path.of("../../simple-secret-common/simple-secret-common-bom/pom.xml")))
                .contains("<artifactId>simple-secret-springboot-starter-auth</artifactId>")
                .contains("<artifactId>sa-token-core</artifactId>")
                .contains("<artifactId>sa-token-spring-boot3-starter</artifactId>");
        assertThat(Files.readString(Path.of("../../pom.xml")))
                .contains("<sa-token.version>1.45.0</sa-token.version>")
                .contains("<artifactId>simple-secret-springboot-starter-auth</artifactId>")
                .contains("<artifactId>sa-token-core</artifactId>")
                .contains("<artifactId>sa-token-spring-boot3-starter</artifactId>");
        assertThat(Files.readString(Path.of("../../integration-tests/pom.xml")))
                .contains("<module>consumer-auth</module>");
    }

    private static Document parsePom(Path path) throws Exception {
        return DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(path.toFile());
    }

    private static List<String> coordinates(List<Element> elements) {
        return elements.stream().map(AuthPublishedDependencyPolicyTest::coordinate).toList();
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
