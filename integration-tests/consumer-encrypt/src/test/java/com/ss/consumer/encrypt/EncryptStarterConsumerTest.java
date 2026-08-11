package com.ss.consumer.encrypt;

import com.ss.encrypt.core.CipherEncoding;
import com.ss.encrypt.core.EncryptionAlgorithm;
import com.ss.encrypt.core.EncryptionRequest;
import com.ss.encrypt.core.EncryptionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证仓库外应用只通过 BOM 即可按需使用 Encrypt 核心服务。 */
class EncryptStarterConsumerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @Test
    void shouldUseBomManagedStarterWithoutExplicitVersion() throws Exception {
        Element project = parsePom(Path.of("pom.xml"));
        Element dependency = dependency(project, "com.ss",
                "simple-secret-springboot-starter-encrypt");

        assertThat(text(dependency, "version")).isNull();
    }

    @Test
    void shouldRemainDisabledUntilConsumerOptsIn() {
        runner.run(context ->
                assertThat(context).doesNotHaveBean(EncryptionService.class));
    }

    @Test
    void shouldEncryptWithoutWebMybatisOrAnotherSimpleSecretModule() {
        runner.withPropertyValues(
                        "simple-secret.encrypt.enabled=true",
                        "simple-secret.encrypt.keys.primary.secret-key="
                                + "AAECAwQFBgcICQoLDA0ODw==")
                .run(context -> {
                    assertThat(context).hasSingleBean(EncryptionService.class);
                    EncryptionService service = context.getBean(EncryptionService.class);
                    EncryptionRequest request = new EncryptionRequest(
                            EncryptionAlgorithm.AES_GCM,
                            CipherEncoding.BASE64, "primary");

                    String encrypted = service.encrypt("consumer", request);

                    assertThat(service.decrypt(encrypted, request))
                            .isEqualTo("consumer");
                });
    }

    private static Element parsePom(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
    }

    private static Element dependency(
            Element project, String groupId, String artifactId) {
        NodeList nodes = project.getElementsByTagName("dependency");
        for (int index = 0; index < nodes.getLength(); index++) {
            Element dependency = (Element) nodes.item(index);
            if (groupId.equals(text(dependency, "groupId"))
                    && artifactId.equals(text(dependency, "artifactId"))) {
                return dependency;
            }
        }
        throw new AssertionError("Missing dependency: " + groupId + ":" + artifactId);
    }

    private static String text(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element element
                    && tagName.equals(element.getTagName())) {
                return element.getTextContent().trim();
            }
        }
        return null;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {
    }
}
