package com.ss.json.config;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublishedDependencyPolicyTest {
    private static final Path SOURCE_POM = Path.of(System.getProperty("basedir"), "pom.xml");
    private static final Path PUBLISHED_POM = Path.of(System.getProperty("basedir"), ".flattened-pom.xml");

    @Test
    void springWebShouldNotBeForcedOnJsonConsumers() throws Exception {
        assertOptionalInSourceAndPublishedPom("org.springframework", "spring-web");
    }

    @Test
    void springBootAutoconfigureShouldNotBeForcedOnStandaloneJsonConsumers() throws Exception {
        assertOptionalInSourceAndPublishedPom("org.springframework.boot", "spring-boot-autoconfigure");
    }

    private static void assertOptionalInSourceAndPublishedPom(String groupId, String artifactId) throws Exception {
        assertEquals("true", dependencyOptionalValue(SOURCE_POM, groupId, artifactId));
        assertEquals("true", dependencyOptionalValue(PUBLISHED_POM, groupId, artifactId));
    }

    private static String dependencyOptionalValue(Path pom, String groupId, String artifactId) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        Element project = factory.newDocumentBuilder().parse(pom.toFile()).getDocumentElement();
        Element dependencies = child(project, "dependencies");
        if (dependencies == null) {
            return null;
        }
        NodeList children = dependencies.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (!(node instanceof Element dependency) || !"dependency".equals(dependency.getTagName())) {
                continue;
            }
            if (groupId.equals(text(dependency, "groupId"))
                    && artifactId.equals(text(dependency, "artifactId"))) {
                return text(dependency, "optional");
            }
        }
        return null;
    }

    private static String text(Element parent, String tagName) {
        Element value = child(parent, tagName);
        return value == null ? null : value.getTextContent().trim();
    }

    private static Element child(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element element && tagName.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }
}
