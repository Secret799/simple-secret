package com.ss.consumer.security;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpLogic;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ss.auth.web.SimpleSecretAuthExceptionHandler;
import com.ss.security.config.SecurityProperties;
import com.ss.security.web.LoginRequiredInterceptor;
import com.ss.security.web.SecurityWebMvcConfigurer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** 验证第三方 Servlet 应用通过 BOM 组合 Security 与 Auth starter。 */
class SecurityStarterConsumerTest {
    private static final String TOKEN_MARKER = "consumer-security-token-marker";
    private static final String EXCEPTION_MARKER = "consumer-security-exception-marker";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @Test
    void shouldUseBomManagedDependenciesAndRemainDisabledByDefault() throws Exception {
        Element consumerProject = parsePom(Path.of("pom.xml"));
        Element parentProject = parsePom(Path.of("../pom.xml"));

        assertVersionManagedDependency(consumerProject,
                "org.springframework.boot", "spring-boot-starter-web");
        assertVersionManagedDependency(consumerProject,
                "cn.dev33", "sa-token-spring-boot3-starter");
        assertVersionManagedDependency(consumerProject,
                "com.ss", "simple-secret-springboot-starter-auth");
        assertVersionManagedDependency(consumerProject,
                "com.ss", "simple-secret-springboot-starter-security");
        Element bom = dependency(parentProject, "com.ss", "simple-secret-common-bom");
        assertThat(text(bom, "type")).isEqualTo("pom");
        assertThat(text(bom, "scope")).isEqualTo("import");

        runner.run(context -> assertThat(context)
                .doesNotHaveBean(SecurityProperties.class)
                .doesNotHaveBean(LoginRequiredInterceptor.class)
                .doesNotHaveBean(SecurityWebMvcConfigurer.class)
                .doesNotHaveBean(SimpleSecretAuthExceptionHandler.class));
    }

    @Test
    void shouldProtectOnlyIncludedRouteAndPreserveNotLoginExceptionWithoutAuthAdvice() {
        runner.withBean(StpLogic.class, FailingStpLogic::new)
                .withPropertyValues(
                        "simple-secret.security.enabled=true",
                        "simple-secret.security.path-patterns[0]=/consumer-security/**",
                        "simple-secret.security.exclude-path-patterns[0]=/consumer-security/public")
                .run(context -> {
                    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

                    assertThat(mockMvc.perform(get("/consumer-security/public"))
                            .andReturn().getResponse().getStatus()).isEqualTo(200);
                    assertThatThrownBy(() -> mockMvc.perform(get("/consumer-security/private")))
                            .hasRootCauseInstanceOf(NotLoginException.class);
                });
    }

    @Test
    void shouldReturnFixedUnauthorizedJsonWhenAuthAdviceIsExplicitlyEnabled() throws Exception {
        try (ConfigurableApplicationContext context = startWebApplication(
                "--simple-secret.security.enabled=true",
                "--simple-secret.security.path-patterns[0]=/consumer-security/**",
                "--simple-secret.security.exclude-path-patterns[0]=/consumer-security/public",
                "--simple-secret.auth.enabled=true",
                "--simple-secret.auth.exception-handler.enabled=true")) {
            HttpResponse<String> response = httpGet(context, "/consumer-security/private");
            JsonNode body = new ObjectMapper().readTree(response.body());

            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(body.path("code").asInt()).isEqualTo(401);
            assertThat(body.path("message").asText()).isEqualTo("认证失败，无法访问系统资源");
            assertThat(response.body())
                    .doesNotContain("/consumer-security/private")
                    .doesNotContain(TOKEN_MARKER)
                    .doesNotContain("loginType")
                    .doesNotContain(EXCEPTION_MARKER);
        }
    }

    @Test
    void shouldBackOffForAllConsumerSecurityBeans() {
        runner.withUserConfiguration(ConsumerOverrides.class)
                .withPropertyValues("simple-secret.security.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(StpLogic.class);
                    assertThat(context).hasSingleBean(LoginRequiredInterceptor.class);
                    assertThat(context).hasSingleBean(SecurityWebMvcConfigurer.class);
                    assertThat(context.getBean(StpLogic.class)).isSameAs(ConsumerOverrides.STP_LOGIC);
                    assertThat(context.getBean(LoginRequiredInterceptor.class))
                            .isSameAs(ConsumerOverrides.INTERCEPTOR);
                    assertThat(context.getBean(SecurityWebMvcConfigurer.class))
                            .isSameAs(ConsumerOverrides.CONFIGURER);
                });
    }

    private static ConfigurableApplicationContext startWebApplication(String... properties) {
        SpringApplication application = new SpringApplication(ConsumerApplication.class);
        application.setWebApplicationType(WebApplicationType.SERVLET);
        application.setDefaultProperties(Map.of(
                "server.port", "0",
                "spring.main.banner-mode", "off",
                "logging.level.root", "OFF"));
        return application.run(properties);
    }

    private static HttpResponse<String> httpGet(ConfigurableApplicationContext context, String path)
            throws IOException, InterruptedException {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .header("satoken", TOKEN_MARKER)
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static Element parsePom(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(path.toFile()).getDocumentElement();
    }

    private static void assertVersionManagedDependency(
            Element project, String groupId, String artifactId) {
        assertThat(text(dependency(project, groupId, artifactId), "version")).isNull();
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
        ConsumerController consumerController() {
            return new ConsumerController();
        }
    }

    @RestController
    static class ConsumerController {

        @GetMapping({"/consumer-security/private", "/consumer-security/public"})
        String data() {
            return "ok";
        }
    }

    static final class FailingStpLogic extends StpLogic {
        private final NotLoginException failure = new NotLoginException(
                EXCEPTION_MARKER, "consumer", NotLoginException.INVALID_TOKEN);

        FailingStpLogic() {
            super("consumer");
        }

        @Override
        public void checkLogin() {
            throw failure;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerOverrides {
        private static final StpLogic STP_LOGIC = new StpLogic("consumer");
        private static final LoginRequiredInterceptor INTERCEPTOR =
                new LoginRequiredInterceptor(STP_LOGIC);
        private static final SecurityWebMvcConfigurer CONFIGURER =
                new SecurityWebMvcConfigurer(INTERCEPTOR, new SecurityProperties());

        @Bean
        StpLogic consumerStpLogic() {
            return STP_LOGIC;
        }

        @Bean
        LoginRequiredInterceptor consumerLoginRequiredInterceptor() {
            return INTERCEPTOR;
        }

        @Bean
        SecurityWebMvcConfigurer consumerSecurityWebMvcConfigurer() {
            return CONFIGURER;
        }
    }
}
