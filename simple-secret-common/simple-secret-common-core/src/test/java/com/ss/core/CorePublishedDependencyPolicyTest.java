package com.ss.core;

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

/** 验证 core 对第三方消费者发布的依赖与聚合边界。 */
class CorePublishedDependencyPolicyTest {

    @Test
    void shouldPublishNoProductionDependenciesAndBeManagedByProject() throws Exception {
        Document module = parse(Path.of("pom.xml"));
        Element dependencies = directChild(module.getDocumentElement(), "dependencies");
        List<Element> productionDependencies = directChildren(dependencies, "dependency").stream()
                .filter(element -> !"test".equals(optionalText(element, "scope")))
                .toList();

        assertThat(productionDependencies).isEmpty();
        assertThat(read(Path.of("../pom.xml"))).contains("<module>simple-secret-common-core</module>");
        assertThat(read(Path.of("../simple-secret-common-bom/pom.xml")))
                .contains("<artifactId>simple-secret-common-core</artifactId>");
        assertThat(read(Path.of("../../pom.xml")))
                .contains("<artifactId>simple-secret-common-core</artifactId>");
    }

    private static Document parse(Path path) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(path.toFile());
    }

    private static String read(Path path) throws Exception {
        return java.nio.file.Files.readString(path);
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
