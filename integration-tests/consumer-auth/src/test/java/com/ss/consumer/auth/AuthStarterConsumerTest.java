package com.ss.consumer.auth;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpLogic;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ss.auth.domain.BaseClientDomain;
import com.ss.auth.domain.BaseLoginBody;
import com.ss.auth.domain.ClientStatus;
import com.ss.auth.domain.LoginUser;
import com.ss.auth.service.AuthStrategy;
import com.ss.auth.service.ClientService;
import com.ss.auth.strategy.AuthStrategyRegistry;
import com.ss.auth.support.LoginHelper;
import com.ss.auth.web.SimpleSecretAuthExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证第三方 Servlet 应用通过 BOM 使用 auth starter 的行为。 */
class AuthStarterConsumerTest {

    private static final String SECRET_MARKER = "consumer-auth-secret-marker";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @Test
    void shouldUseBomManagedAuthStarterAndRemainDisabledByDefault() throws Exception {
        Element consumerProject = parsePom(Path.of("pom.xml"));
        Element parentProject = parsePom(Path.of("../pom.xml"));

        assertVersionManagedDependency(consumerProject,
                "org.springframework.boot", "spring-boot-starter-web");
        assertVersionManagedDependency(consumerProject,
                "cn.dev33", "sa-token-spring-boot3-starter");
        assertVersionManagedDependency(consumerProject,
                "com.ss", "simple-secret-springboot-starter-auth");
        Element bom = dependency(parentProject, "com.ss", "simple-secret-common-bom");
        assertThat(text(bom, "type")).isEqualTo("pom");
        assertThat(text(bom, "scope")).isEqualTo("import");

        runner.run(context -> assertThat(context)
                .doesNotHaveBean(LoginHelper.class)
                .doesNotHaveBean(StpInterface.class)
                .doesNotHaveBean(AuthStrategyRegistry.class)
                .doesNotHaveBean(SimpleSecretAuthExceptionHandler.class));
    }

    @Test
    void shouldCreateCoreBeansAndDispatchConsumerPasswordStrategyWhenEnabled() {
        runner.withUserConfiguration(StrategyConfiguration.class)
                .withPropertyValues("simple-secret.auth.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(StpLogic.class)
                            .hasSingleBean(LoginHelper.class)
                            .hasSingleBean(StpInterface.class)
                            .hasSingleBean(AuthStrategyRegistry.class);

                    BaseLoginBody request = new BaseLoginBody();
                    request.setGrantType("password");
                    request.setClientId("consumer-client");

                    assertThat(context.getBean(AuthStrategyRegistry.class).login(request))
                            .isEqualTo(StrategyConfiguration.LOGIN_USER);
                });
    }

    @Test
    void shouldBackOffForAllConsumerAuthBeans() {
        runner.withUserConfiguration(ConsumerOverrides.class)
                .withPropertyValues(
                        "simple-secret.auth.enabled=true",
                        "simple-secret.auth.exception-handler.enabled=true")
                .run(context -> {
                    assertThat(context.getBean(StpLogic.class))
                            .isSameAs(ConsumerOverrides.STP_LOGIC);
                    assertThat(context.getBean(LoginHelper.class))
                            .isSameAs(ConsumerOverrides.LOGIN_HELPER);
                    assertThat(context.getBean(StpInterface.class))
                            .isSameAs(ConsumerOverrides.STP_INTERFACE);
                    assertThat(context.getBean(AuthStrategyRegistry.class))
                            .isSameAs(ConsumerOverrides.AUTH_STRATEGY_REGISTRY);
                    assertThat(context.getBean(SimpleSecretAuthExceptionHandler.class))
                            .isSameAs(ConsumerOverrides.EXCEPTION_HANDLER);
                });
    }

    @Test
    void shouldReturnFixedSafeUnauthorizedResponseThroughHttp() throws Exception {
        try (ConfigurableApplicationContext context = startWebApplication(
                "--simple-secret.auth.enabled=true",
                "--simple-secret.auth.exception-handler.enabled=true")) {
            HttpResponse<String> response = get(context, "/consumer-auth/not-login");
            JsonNode body = new ObjectMapper().readTree(response.body());

            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(body.path("code").asInt()).isEqualTo(401);
            assertThat(body.path("message").asText()).isEqualTo("认证失败，无法访问系统资源");
            assertThat(response.body()).doesNotContain(SECRET_MARKER);
        }
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

    private static HttpResponse<String> get(ConfigurableApplicationContext context, String path)
            throws IOException, InterruptedException {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
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
        FailureController failureController() {
            return new FailureController();
        }
    }

    @RestController
    static class FailureController {

        @GetMapping("/consumer-auth/not-login")
        void notLogin() {
            throw NotLoginException.newInstance(
                    SECRET_MARKER, SECRET_MARKER, SECRET_MARKER, SECRET_MARKER);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class StrategyConfiguration {
        private static final LoginUser LOGIN_USER = new LoginUser(
                "consumer-user", "consumer", Set.of(), Set.of(), Map.of());

        @Bean
        ClientService clientService() {
            return clientId -> {
                BaseClientDomain client = new BaseClientDomain();
                client.setClientId(clientId);
                client.setStatus(ClientStatus.NORMAL);
                client.setGrantTypeList(List.of("password"));
                return client;
            };
        }

        @Bean
        AuthStrategy passwordAuthStrategy() {
            return new AuthStrategy() {
                @Override
                public String grantType() {
                    return "password";
                }

                @Override
                public LoginUser login(BaseLoginBody body, BaseClientDomain client) {
                    return LOGIN_USER;
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerOverrides {
        private static final StpLogic STP_LOGIC = new StpLogic("consumer");
        private static final LoginHelper LOGIN_HELPER = new LoginHelper(STP_LOGIC);
        private static final StpInterface STP_INTERFACE = new StpInterface() {
            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                return List.of();
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                return List.of();
            }
        };
        private static final AuthStrategyRegistry AUTH_STRATEGY_REGISTRY = new AuthStrategyRegistry(
                clientId -> null, List.of());
        private static final SimpleSecretAuthExceptionHandler EXCEPTION_HANDLER =
                SimpleSecretAuthExceptionHandler.create();

        @Bean
        StpLogic consumerStpLogic() {
            return STP_LOGIC;
        }

        @Bean
        LoginHelper consumerLoginHelper() {
            return LOGIN_HELPER;
        }

        @Bean
        StpInterface consumerStpInterface() {
            return STP_INTERFACE;
        }

        @Bean
        ClientService consumerClientService() {
            return clientId -> null;
        }

        @Bean
        AuthStrategyRegistry consumerAuthStrategyRegistry() {
            return AUTH_STRATEGY_REGISTRY;
        }

        @Bean
        SimpleSecretAuthExceptionHandler consumerAuthExceptionHandler() {
            return EXCEPTION_HANDLER;
        }
    }
}
