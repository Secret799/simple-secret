package com.ss.netty.config;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NettyWebSocketPublishedDependencyPolicyTest {

    private static final Set<String> REQUIRED_NETTY = Set.of(
            "netty-common", "netty-buffer", "netty-transport", "netty-codec",
            "netty-codec-http");

    @Test
    void shouldDeclareOnlyComponentLevelNettyAndNoPrivateRuntimeModules() throws Exception {
        Element project = parsePom(Path.of("pom.xml"));
        List<String> coordinates = directDependencies(project);

        assertThat(coordinates).containsExactlyInAnyOrder(
                "io.netty:netty-common",
                "io.netty:netty-buffer",
                "io.netty:netty-transport",
                "io.netty:netty-codec",
                "io.netty:netty-codec-http",
                "org.springframework.boot:spring-boot",
                "org.springframework.boot:spring-boot-autoconfigure",
                "org.springframework:spring-beans",
                "org.springframework:spring-context");
        assertThat(coordinates.stream()
                .filter(value -> value.startsWith("io.netty:"))
                .map(value -> value.substring("io.netty:".length())))
                .containsExactlyInAnyOrderElementsOf(REQUIRED_NETTY);
        assertThat(coordinates).noneMatch(value -> value.equals("io.netty:netty-all"));
        assertThat(coordinates).noneMatch(value -> value.startsWith("com.ss:"));
        assertThat(coordinates).noneMatch(value -> value.startsWith("cn.hutool:"));
        assertThat(coordinates).noneMatch(value -> value.contains("lombok"));
        assertThat(coordinates).noneMatch(value -> value.startsWith("com.fasterxml.jackson:"));
        assertThat(directProductionDependencies(project)).allSatisfy(dependency ->
                assertThat(text(dependency, "version")).isNull());
        assertThat(directTestDependencies(project)).containsExactlyInAnyOrder(
                "org.springframework.boot:spring-boot-test",
                "org.junit.jupiter:junit-jupiter-api",
                "org.assertj:assertj-core");

        Element starterParent = parsePom(Path.of("../pom.xml"));
        assertThat(directModuleNames(starterParent))
                .contains("simple-secret-springboot-starter-netty-websocket");
        Element root = parsePom(Path.of("../../pom.xml"));
        assertManaged(root, "com.ss", "simple-secret-springboot-starter-netty-websocket",
                "${revision}");
        Element publicBom = parsePom(Path.of(
                "../../simple-secret-common/simple-secret-common-bom/pom.xml"));
        assertManaged(publicBom, "com.ss", "simple-secret-springboot-starter-netty-websocket",
                "${revision}");
        assertManaged(publicBom, "io.netty", "netty-bom", "${netty.version}");
    }

    private static Element parsePom(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
    }

    private static List<String> directDependencies(Element project) {
        return directProductionDependencies(project).stream()
                .map(dependency -> text(dependency, "groupId") + ':'
                        + text(dependency, "artifactId"))
                .toList();
    }

    private static List<Element> directProductionDependencies(Element project) {
        Element dependencies = child(project, "dependencies");
        NodeList children = dependencies.getChildNodes();
        java.util.ArrayList<Element> values = new java.util.ArrayList<>();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element dependency && "dependency".equals(dependency.getTagName())
                    && !"test".equals(text(dependency, "scope"))) {
                values.add(dependency);
            }
        }
        return List.copyOf(values);
    }

    private static List<String> directTestDependencies(Element project) {
        Element dependencies = child(project, "dependencies");
        NodeList children = dependencies.getChildNodes();
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element dependency && "dependency".equals(dependency.getTagName())
                    && "test".equals(text(dependency, "scope"))) {
                values.add(text(dependency, "groupId") + ':'
                        + text(dependency, "artifactId"));
            }
        }
        return List.copyOf(values);
    }

    private static List<String> directModuleNames(Element project) {
        Element modules = child(project, "modules");
        NodeList children = modules.getChildNodes();
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element module && "module".equals(module.getTagName())) {
                values.add(module.getTextContent().trim());
            }
        }
        return List.copyOf(values);
    }

    private static void assertManaged(Element project, String groupId,
                                      String artifactId, String version) {
        Element dependencyManagement = child(project, "dependencyManagement");
        Element dependencies = child(dependencyManagement, "dependencies");
        NodeList children = dependencies.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element dependency
                    && "dependency".equals(dependency.getTagName())
                    && groupId.equals(text(dependency, "groupId"))
                    && artifactId.equals(text(dependency, "artifactId"))) {
                assertThat(text(dependency, "version")).isEqualTo(version);
                return;
            }
        }
        throw new AssertionError("Missing managed dependency: " + groupId + ':' + artifactId);
    }

    private static Element child(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element && tagName.equals(element.getTagName())) {
                return element;
            }
        }
        throw new AssertionError("Missing element: " + tagName);
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
}
