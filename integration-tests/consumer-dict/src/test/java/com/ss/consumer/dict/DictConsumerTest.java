package com.ss.consumer.dict;

import com.ss.dict.DictionaryParser;
import com.ss.dict.DictionaryRegistry;
import com.ss.dict.annotation.DictField;
import com.ss.dict.model.DictElement;
import com.ss.dict.model.DictScope;
import com.ss.dict.model.DictValue;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证仓库外 Java 17 项目只依赖 dict artifact 即可使用完整字典能力。 */
class DictConsumerTest {

    @Test
    void shouldUseBomManagedDictWithoutDeclaringToolbox() throws Exception {
        Element project = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().parse(Path.of("pom.xml").toFile())
                .getDocumentElement();
        Element dependency = dependency(project, "com.ss", "simple-secret-common-dict");

        assertThat(text(dependency, "version")).isNull();
        assertThat(findDependency(project, "com.ss", "simple-secret-common-toolbox")).isNull();
    }

    @Test
    void shouldRegisterQueryCacheAndParseWithoutSpring() {
        AtomicInteger loads = new AtomicInteger();
        try (DictionaryRegistry registry = new DictionaryRegistry(Duration.ofMinutes(1))) {
            registry.registerEnum("sex", Sex.class);
            registry.register("status", () -> {
                loads.incrementAndGet();
                return List.of(new DictElement(
                        DictScope.GLOBAL, "global", "1", "启用", "default"));
            });

            assertThat(registry.queryCached("status").get(0).label()).isEqualTo("启用");
            assertThat(registry.queryCached("status").get(0).label()).isEqualTo("启用");
            assertThat(loads).hasValue(1);
            registry.invalidate("status");
            registry.queryCached("status");
            assertThat(loads).hasValue(2);

            UserView user = new UserView("1");
            new DictionaryParser(registry).parse(user);
            assertThat(user.sexDisplayLabel).isEqualTo("男");
        }
    }

    private static Element dependency(Element project, String groupId, String artifactId) {
        Element dependency = findDependency(project, groupId, artifactId);
        if (dependency == null) {
            throw new AssertionError("Missing dependency: " + groupId + ":" + artifactId);
        }
        return dependency;
    }

    private static Element findDependency(Element project, String groupId, String artifactId) {
        NodeList nodes = project.getElementsByTagName("dependency");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element dependency = (Element) nodes.item(index);
            if (groupId.equals(text(dependency, "groupId"))
                    && artifactId.equals(text(dependency, "artifactId"))) {
                return dependency;
            }
        }
        return null;
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

    private enum Sex implements DictValue {
        MALE("1", "男"),
        FEMALE("0", "女");

        private final String code;
        private final String label;

        Sex(String code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public String getDictCode() {
            return code;
        }

        @Override
        public String getDictLabel() {
            return label;
        }
    }

    private static final class UserView {
        @DictField("sex")
        private final String sex;
        private String sexDisplayLabel;

        private UserView(String sex) {
            this.sex = sex;
        }
    }
}
