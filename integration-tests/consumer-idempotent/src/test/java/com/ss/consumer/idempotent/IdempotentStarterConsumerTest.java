package com.ss.consumer.idempotent;

import com.ss.idempotent.annotation.RepeatSubmit;
import com.ss.idempotent.aspect.RepeatSubmitAspect;
import com.ss.idempotent.exception.RepeatSubmitException;
import com.ss.idempotent.store.IdempotencyStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证仓库外 Spring Boot 应用可通过 BOM 无版本使用 idempotent starter。 */
class IdempotentStarterConsumerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @AfterEach
    void resetRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldUseBomManagedStarterWithoutExplicitVersion() throws Exception {
        Element project = parsePom(Path.of("pom.xml"));
        Element dependency = dependency(project, "com.ss",
                "simple-secret-springboot-starter-idempotent");

        assertThat(text(dependency, "version")).isNull();
    }

    @Test
    void shouldDiscoverAutoConfigurationAndRejectSecondInvocation() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RepeatSubmitAspect.class);
            SubmitService service = context.getBean(SubmitService.class);

            bindRequest();
            assertThat(service.submit("order-7")).isEqualTo(1);
            bindRequest();
            assertThatThrownBy(() -> service.submit("order-7"))
                    .isInstanceOf(RepeatSubmitException.class);
            assertThat(service.invocations()).isEqualTo(1);
        });
    }

    private static void bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/orders");
        request.setRemoteAddr("192.0.2.90");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static Element parsePom(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
    }

    private static Element dependency(Element project, String groupId, String artifactId) {
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
            if (child instanceof Element element && tagName.equals(element.getTagName())) {
                return element.getTextContent().trim();
            }
        }
        return null;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {

        @Bean
        IdempotencyStore idempotencyStore() {
            return new InMemoryTestStore();
        }

        @Bean
        SubmitService submitService() {
            return new SubmitService();
        }
    }

    static class SubmitService {

        private final AtomicInteger invocations = new AtomicInteger();

        @RepeatSubmit
        public int submit(String orderId) {
            return invocations.incrementAndGet();
        }

        int invocations() {
            return invocations.get();
        }
    }

    static class InMemoryTestStore implements IdempotencyStore {

        private final Map<String, String> leases = new ConcurrentHashMap<>();

        @Override
        public boolean tryAcquire(String key, String owner, Duration ttl) {
            return leases.putIfAbsent(key, owner) == null;
        }

        @Override
        public boolean release(String key, String owner) {
            return leases.remove(key, owner);
        }
    }
}
