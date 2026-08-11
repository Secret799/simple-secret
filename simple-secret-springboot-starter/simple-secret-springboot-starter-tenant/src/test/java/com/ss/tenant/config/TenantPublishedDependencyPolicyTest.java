package com.ss.tenant.config;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 tenant starter 发布给第三方消费者的最小依赖边界。 */
class TenantPublishedDependencyPolicyTest {

    @Test
    void shouldPublishOnlySqlTenantAndAutoConfigurationContracts() throws Exception {
        Document module = parsePom(Path.of("pom.xml"));
        List<Element> production = directChildren(
                directChild(module.getDocumentElement(), "dependencies"), "dependency").stream()
                .filter(element -> !"test".equals(text(element, "scope")))
                .toList();

        assertThat(coordinates(production)).containsExactlyInAnyOrder(
                "com.ss:simple-secret-springboot-starter-mybatis-plus",
                "com.baomidou:mybatis-plus-extension",
                "com.baomidou:mybatis-plus-annotation",
                "com.baomidou:mybatis-plus-jsqlparser",
                "com.github.jsqlparser:jsqlparser",
                "org.mybatis:mybatis",
                "org.springframework.boot:spring-boot",
                "org.springframework.boot:spring-boot-autoconfigure",
                "org.springframework:spring-beans",
                "org.springframework:spring-context",
                "org.springframework:spring-core");
        assertThat(production).allSatisfy(dependency ->
                assertThat(text(dependency, "version")).isEmpty());
        assertThat(coordinates(production))
                .noneMatch(TenantPublishedDependencyPolicyTest::isForbidden);

        assertModule(parsePom(Path.of("../pom.xml")),
                "simple-secret-springboot-starter-tenant");
        assertManagedModule(parsePom(Path.of("../../pom.xml")),
                "simple-secret-springboot-starter-tenant");
        assertManagedModule(parsePom(Path.of(
                        "../../simple-secret-common/simple-secret-common-bom/pom.xml")),
                "simple-secret-springboot-starter-tenant");
    }

    private static boolean isForbidden(String coordinate) {
        String normalized = coordinate.toLowerCase(Locale.ROOT);
        return normalized.contains("redis")
                || normalized.contains("redisson")
                || normalized.contains("sa-token")
                || normalized.contains("auth")
                || normalized.contains("web")
                || normalized.contains("json")
                || normalized.contains("cache")
                || normalized.contains("toolbox")
                || normalized.contains("hutool")
                || normalized.contains("lombok")
                || normalized.contains("transmittable")
                || normalized.contains("honeybee");
    }

    private static void assertModule(Document pom, String module) {
        Element modules = directChild(pom.getDocumentElement(), "modules");
        assertThat(directChildren(modules, "module").stream()
                .map(element -> element.getTextContent().trim()))
                .contains(module);
    }

    private static void assertManagedModule(Document pom, String artifactId) {
        Element dependencyManagement = directChild(pom.getDocumentElement(), "dependencyManagement");
        Element dependencies = directChild(dependencyManagement, "dependencies");
        Element managed = dependency(directChildren(dependencies, "dependency"),
                "com.ss:" + artifactId);
        assertThat(text(managed, "version")).isEqualTo("${revision}");
    }

    private static Document parsePom(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private static List<String> coordinates(List<Element> elements) {
        return elements.stream().map(TenantPublishedDependencyPolicyTest::coordinate).toList();
    }

    private static Element dependency(List<Element> elements, String coordinate) {
        return elements.stream()
                .filter(element -> coordinate.equals(coordinate(element)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing dependency: " + coordinate));
    }

    private static String coordinate(Element element) {
        return text(element, "groupId") + ":" + text(element, "artifactId");
    }

    private static Element directChild(Element parent, String name) {
        return directChildren(parent, name).stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing element: " + name));
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
        return directChildren(parent, name).stream()
                .findFirst()
                .map(element -> element.getTextContent().trim())
                .orElse("");
    }
}
