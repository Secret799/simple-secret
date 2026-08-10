package com.ss.geo;

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

/**
 * 校验 Geo 插件发布依赖，防止无意引入运行时框架。
 */
class PublishedDependencyPolicyTest {

    private static final Path SOURCE_POM = Path.of(System.getProperty("basedir"), "pom.xml");

    @Test
    void productionDependenciesShouldBeEmpty() throws Exception {
        assertThat(productionDependencies(SOURCE_POM)).isEmpty();
    }

    private static List<String> productionDependencies(Path pom) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        Element project = factory.newDocumentBuilder().parse(pom.toFile()).getDocumentElement();
        Element dependencies = child(project, "dependencies");
        List<String> result = new ArrayList<>();
        if (dependencies == null) {
            return result;
        }
        NodeList children = dependencies.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (!(node instanceof Element dependency) || !"dependency".equals(dependency.getTagName())) {
                continue;
            }
            if (!"test".equals(text(dependency, "scope"))) {
                result.add(text(dependency, "groupId") + ":" + text(dependency, "artifactId"));
            }
        }
        return result;
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
