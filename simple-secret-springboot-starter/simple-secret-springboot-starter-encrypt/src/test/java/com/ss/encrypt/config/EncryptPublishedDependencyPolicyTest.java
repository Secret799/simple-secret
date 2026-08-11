package com.ss.encrypt.config;

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

/** 验证 encrypt starter 发布给第三方应用的最小依赖边界。 */
class EncryptPublishedDependencyPolicyTest {

    private static final String ARTIFACT_ID = "simple-secret-springboot-starter-encrypt";

    @Test
    void shouldPublishOnlyRequiredCryptoAndConditionalFrameworkContracts() throws Exception {
        Document module = parsePom(Path.of("pom.xml"));
        List<Element> production = directChildren(
                directChild(module.getDocumentElement(), "dependencies"), "dependency").stream()
                .filter(element -> !"test".equals(text(element, "scope")))
                .toList();

        assertThat(coordinates(production)).containsExactlyInAnyOrder(
                "org.bouncycastle:bcprov-lts8on",
                "org.springframework.boot:spring-boot",
                "org.springframework.boot:spring-boot-autoconfigure",
                "org.springframework:spring-beans",
                "org.springframework:spring-context",
                "org.springframework:spring-core",
                "jakarta.servlet:jakarta.servlet-api",
                "org.springframework:spring-web",
                "org.springframework:spring-webmvc",
                "org.mybatis:mybatis");
        assertThat(production).allSatisfy(dependency ->
                assertThat(text(dependency, "version")).isEmpty());
        assertThat(production).filteredOn(dependency ->
                        isOptionalCoordinate(coordinate(dependency)))
                .allSatisfy(dependency ->
                        assertThat(text(dependency, "optional")).isEqualTo("true"));
        assertThat(coordinates(production))
                .noneMatch(EncryptPublishedDependencyPolicyTest::isForbidden);

        assertModule(parsePom(Path.of("../pom.xml")), ARTIFACT_ID);
        Document root = parsePom(Path.of("../../pom.xml"));
        assertManagedModule(root, ARTIFACT_ID);
        assertThat(text(directChild(root.getDocumentElement(), "properties"),
                "bouncycastle.version"))
                .isEqualTo("2.73.12");
        assertManagedDependency(root, "org.bouncycastle:bcprov-lts8on",
                "${bouncycastle.version}");

        Document bom = parsePom(Path.of(
                "../../simple-secret-common/simple-secret-common-bom/pom.xml"));
        assertManagedModule(bom, ARTIFACT_ID);
        assertManagedDependency(bom, "org.bouncycastle:bcprov-lts8on",
                "${bouncycastle.version}");

        Document nats = parsePom(Path.of(
                "../simple-secret-springboot-starter-nats/pom.xml"));
        Element natsDependency = dependency(
                directChildren(directChild(nats.getDocumentElement(), "dependencies"),
                        "dependency"),
                "org.bouncycastle:bcprov-lts8on");
        assertThat(text(natsDependency, "version")).isEmpty();
    }

    private static boolean isOptionalCoordinate(String coordinate) {
        return coordinate.startsWith("jakarta.servlet:")
                || coordinate.equals("org.springframework:spring-web")
                || coordinate.equals("org.springframework:spring-webmvc")
                || coordinate.equals("org.mybatis:mybatis");
    }

    private static boolean isForbidden(String coordinate) {
        String normalized = coordinate.toLowerCase(Locale.ROOT);
        return normalized.contains("simple-secret-springboot-starter-")
                || normalized.contains("honeybee")
                || normalized.contains("hutool")
                || normalized.contains("lombok")
                || normalized.contains("jackson")
                || normalized.contains("mybatis-plus")
                || normalized.contains("redisson")
                || normalized.contains("sa-token");
    }

    private static void assertModule(Document pom, String module) {
        Element modules = directChild(pom.getDocumentElement(), "modules");
        assertThat(directChildren(modules, "module").stream()
                .map(element -> element.getTextContent().trim()))
                .contains(module);
    }

    private static void assertManagedModule(Document pom, String artifactId) {
        assertManagedDependency(pom, "com.ss:" + artifactId, "${revision}");
    }

    private static void assertManagedDependency(
            Document pom, String coordinate, String version) {
        Element dependencyManagement = directChild(
                pom.getDocumentElement(), "dependencyManagement");
        Element dependencies = directChild(dependencyManagement, "dependencies");
        Element managed = dependency(
                directChildren(dependencies, "dependency"), coordinate);
        assertThat(text(managed, "version")).isEqualTo(version);
    }

    private static Document parsePom(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(path.toFile());
    }

    private static List<String> coordinates(List<Element> elements) {
        return elements.stream()
                .map(EncryptPublishedDependencyPolicyTest::coordinate)
                .toList();
    }

    private static Element dependency(List<Element> elements, String coordinate) {
        return elements.stream()
                .filter(element -> coordinate.equals(coordinate(element)))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Missing dependency: " + coordinate));
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
