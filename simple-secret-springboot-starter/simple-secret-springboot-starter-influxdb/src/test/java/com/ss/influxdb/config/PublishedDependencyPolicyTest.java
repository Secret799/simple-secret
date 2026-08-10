package com.ss.influxdb.config;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PublishedDependencyPolicyTest {
    private static final Path SOURCE_POM = Path.of(System.getProperty("basedir"), "pom.xml");

    @Test
    void productionDependenciesShouldContainOnlyRequiredLibraries() throws Exception {
        assertThat(productionDependencies(SOURCE_POM)).containsExactlyInAnyOrder(
                "com.ss:simple-secret-common-toolbox",
                "org.influxdb:influxdb-java",
                "org.msgpack:msgpack-core",
                "com.squareup.okhttp3:okhttp",
                "org.slf4j:slf4j-api",
                "org.springframework.boot:spring-boot-autoconfigure");
    }

    @Test
    void influxdbVersionShouldMatchTheSpringBootConsumerVersion() throws Exception {
        DocumentBuilderFactory factory = documentBuilderFactory();
        Element project = factory.newDocumentBuilder().parse(SOURCE_POM.toFile()).getDocumentElement();

        assertThat(text(child(project, "properties"), "influxdb-java.version")).isEqualTo("2.25");
    }

    private static List<String> productionDependencies(Path pom) throws Exception {
        DocumentBuilderFactory factory = documentBuilderFactory();
        Element dependencies = child(factory.newDocumentBuilder().parse(pom.toFile())
                .getDocumentElement(), "dependencies");
        List<String> result = new ArrayList<>();
        NodeList children = dependencies.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node instanceof Element dependency && "dependency".equals(dependency.getTagName())
                    && !"test".equals(text(dependency, "scope"))) {
                result.add(text(dependency, "groupId") + ":" + text(dependency, "artifactId"));
            }
        }
        return result;
    }

    private static DocumentBuilderFactory documentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
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
