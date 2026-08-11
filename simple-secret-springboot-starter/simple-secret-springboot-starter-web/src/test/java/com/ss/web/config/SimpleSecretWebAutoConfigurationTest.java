package com.ss.web.config;

import com.ss.web.error.SimpleSecretExceptionHandler;
import com.ss.web.error.SimpleSecretValidationExceptionHandler;
import com.ss.web.observability.RequestTimingInterceptor;
import com.ss.web.observability.RequestTimingWebMvcConfigurer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证 WebMVC 自动配置的属性绑定与默认关闭边界。 */
class SimpleSecretWebAutoConfigurationTest {

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleSecretWebAutoConfiguration.class));

    private final WebApplicationContextRunner broadComponentScanRunner =
            new WebApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(SimpleSecretWebAutoConfiguration.class))
                    .withUserConfiguration(BroadComponentScanConfiguration.class);

    private final ApplicationContextRunner nonWebRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleSecretWebAutoConfiguration.class));

    private final ApplicationContextRunner nonServletBroadComponentScanRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(NonServletBroadComponentScanConfiguration.class);

    private final ApplicationContextRunner propertiesRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void shouldUseDisabledDefaults() {
        propertiesRunner.run(context -> {
            WebProperties properties = context.getBean(WebProperties.class);

            assertThat(properties.isEnabled()).isFalse();
            assertThat(properties.getExceptionHandler().isEnabled()).isFalse();
            assertThat(properties.getCors().isEnabled()).isFalse();
            assertThat(properties.getCors().getPath()).isEqualTo("/**");
            assertThat(properties.getCors().getAllowedOrigins()).isEmpty();
            assertThat(properties.getCors().getAllowedOriginPatterns()).isEmpty();
            assertThat(properties.getCors().getAllowedMethods())
                    .containsExactly("GET", "HEAD", "POST");
            assertThat(properties.getCors().getAllowedHeaders()).isEmpty();
            assertThat(properties.getCors().getExposedHeaders()).isEmpty();
            assertThat(properties.getCors().isAllowCredentials()).isFalse();
            assertThat(properties.getCors().getMaxAge()).isEqualTo(Duration.ofMinutes(30));
            assertThat(properties.getRequestTiming().isEnabled()).isFalse();
            assertThat(properties.getRequestTiming().getSlowRequestThreshold())
                    .isEqualTo(Duration.ofSeconds(1));
        });
    }

    @Test
    void shouldBindWebFeatureProperties() {
        propertiesRunner.withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.exception-handler.enabled=true",
                        "simple-secret.web.cors.enabled=true",
                        "simple-secret.web.cors.path=/api/**",
                        "simple-secret.web.cors.allowed-origins[0]=https://app.example.com",
                        "simple-secret.web.cors.allowed-origin-patterns[0]=https://*.internal.example.com",
                        "simple-secret.web.cors.allowed-methods[0]=GET",
                        "simple-secret.web.cors.allowed-methods[1]=PATCH",
                        "simple-secret.web.cors.allowed-headers[0]=Authorization",
                        "simple-secret.web.cors.exposed-headers[0]=X-Request-Id",
                        "simple-secret.web.cors.allow-credentials=true",
                        "simple-secret.web.cors.max-age=45m",
                        "simple-secret.web.request-timing.enabled=true",
                        "simple-secret.web.request-timing.slow-request-threshold=1500ms")
                .run(context -> {
                    WebProperties properties = context.getBean(WebProperties.class);

                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getExceptionHandler().isEnabled()).isTrue();
                    assertThat(properties.getCors().isEnabled()).isTrue();
                    assertThat(properties.getCors().getPath()).isEqualTo("/api/**");
                    assertThat(properties.getCors().getAllowedOrigins())
                            .containsExactly("https://app.example.com");
                    assertThat(properties.getCors().getAllowedOriginPatterns())
                            .containsExactly("https://*.internal.example.com");
                    assertThat(properties.getCors().getAllowedMethods())
                            .containsExactly("GET", "PATCH");
                    assertThat(properties.getCors().getAllowedHeaders())
                            .containsExactly("Authorization");
                    assertThat(properties.getCors().getExposedHeaders())
                            .containsExactly("X-Request-Id");
                    assertThat(properties.getCors().isAllowCredentials()).isTrue();
                    assertThat(properties.getCors().getMaxAge()).isEqualTo(Duration.ofMinutes(45));
                    assertThat(properties.getRequestTiming().isEnabled()).isTrue();
                    assertThat(properties.getRequestTiming().getSlowRequestThreshold())
                            .isEqualTo(Duration.ofMillis(1500));
                });
    }

    @Test
    void shouldHaveNoRuntimeWebSideEffectsByDefault() {
        webRunner.run(this::assertNoRuntimeWebBeans);
    }

    @Test
    void shouldHaveNoRuntimeWebSideEffectsWhenOnlyMasterSwitchIsEnabled() {
        webRunner.withPropertyValues("simple-secret.web.enabled=true")
                .run(this::assertNoRuntimeWebBeans);
    }

    @Test
    void shouldNotRegisterAdviceFromBroadComponentScanByDefault() {
        broadComponentScanRunner.run(this::assertNoDefaultAdvice);
    }

    @Test
    void shouldNotRegisterAdviceFromBroadComponentScanWhenOnlyMasterSwitchIsEnabled() {
        broadComponentScanRunner.withPropertyValues("simple-secret.web.enabled=true")
                .run(this::assertNoDefaultAdvice);
    }

    @Test
    void shouldNotRegisterStarterRuntimeBeansWhenOnlyExceptionHandlerSwitchIsEnabledInBroadScan() {
        broadComponentScanRunner.withPropertyValues(
                        "simple-secret.web.exception-handler.enabled=true")
                .run(this::assertNoStarterRuntimeWebBeans);
    }

    @Test
    void shouldNotRegisterStarterRuntimeBeansWhenOnlyCorsSwitchIsEnabledInBroadScan() {
        broadComponentScanRunner.withPropertyValues(
                        "simple-secret.web.cors.enabled=true")
                .run(this::assertNoStarterRuntimeWebBeans);
    }

    @Test
    void shouldNotRegisterStarterRuntimeBeansWhenOnlyRequestTimingSwitchIsEnabledInBroadScan() {
        broadComponentScanRunner.withPropertyValues(
                        "simple-secret.web.request-timing.enabled=true")
                .run(this::assertNoStarterRuntimeWebBeans);
    }

    @Test
    void shouldNotRegisterStarterRuntimeBeansInNonServletBroadScanWhenAllChildSwitchesAreEnabled() {
        nonServletBroadComponentScanRunner.withPropertyValues(
                        "simple-secret.web.exception-handler.enabled=true",
                        "simple-secret.web.cors.enabled=true",
                        "simple-secret.web.cors.allowed-origins[0]=https://app.example.com",
                        "simple-secret.web.request-timing.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SimpleSecretExceptionHandler.class);
                    assertThat(context).doesNotHaveBean(SimpleSecretValidationExceptionHandler.class);
                    assertThat(context).doesNotHaveBean(CorsConfigurationSource.class);
                    assertThat(context).doesNotHaveBean(RequestTimingInterceptor.class);
                    assertThat(context).doesNotHaveBean(RequestTimingWebMvcConfigurer.class);
                });
    }

    @Test
    void shouldRegisterFactoryAdviceFromAutoConfigurationAfterBroadComponentScan() throws Exception {
        broadComponentScanRunner.withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.exception-handler.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(SimpleSecretExceptionHandler.class);
                    assertThat(context).hasSingleBean(SimpleSecretValidationExceptionHandler.class);
                    assertThat(context.getBeanFactory().getBeanDefinition("simpleSecretExceptionHandler")
                            .getFactoryMethodName()).isEqualTo("simpleSecretExceptionHandler");
                    assertThat(context.getBeanFactory()
                            .getBeanDefinition("simpleSecretValidationExceptionHandler")
                            .getFactoryMethodName())
                            .isEqualTo("simpleSecretValidationExceptionHandler");
                    assertThat(context.getBeansWithAnnotation(ControllerAdvice.class))
                            .containsKeys("simpleSecretExceptionHandler",
                                    "simpleSecretValidationExceptionHandler");

                    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
                    mockMvc.perform(get("/component-scan/advice-failure"))
                            .andExpect(status().isInternalServerError());
                });
    }

    @Test
    void shouldStartBroadComponentScanWithoutJakartaValidation() {
        broadComponentScanRunner.withClassLoader(new FilteredClassLoader("jakarta.validation"))
                .withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.exception-handler.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SimpleSecretExceptionHandler.class);
                    assertThat(context).doesNotHaveBean(SimpleSecretValidationExceptionHandler.class);
                });
    }

    @Test
    void shouldPreferConsumerAdviceBeansAfterBroadComponentScan() {
        SimpleSecretExceptionHandler consumerExceptionHandler =
                SimpleSecretExceptionHandler.create(new StaticMessageSource());
        SimpleSecretValidationExceptionHandler consumerValidationExceptionHandler =
                SimpleSecretValidationExceptionHandler.create();

        broadComponentScanRunner
                .withBean("consumerExceptionHandler", SimpleSecretExceptionHandler.class,
                        () -> consumerExceptionHandler)
                .withBean("consumerValidationExceptionHandler",
                        SimpleSecretValidationExceptionHandler.class,
                        () -> consumerValidationExceptionHandler)
                .withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.exception-handler.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("simpleSecretExceptionHandler");
                    assertThat(context).doesNotHaveBean("simpleSecretValidationExceptionHandler");
                    assertThat(context).hasSingleBean(SimpleSecretExceptionHandler.class);
                    assertThat(context).hasSingleBean(SimpleSecretValidationExceptionHandler.class);
                    assertThat(context.getBean(SimpleSecretExceptionHandler.class))
                            .isSameAs(consumerExceptionHandler);
                    assertThat(context.getBean(SimpleSecretValidationExceptionHandler.class))
                            .isSameAs(consumerValidationExceptionHandler);
                });
    }

    @Test
    void shouldRegisterExceptionHandlersWhenFeatureIsEnabled() {
        webRunner.withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.exception-handler.enabled=true")
                .run(context -> {
                    assertThat(context).hasBean("simpleSecretExceptionHandler");
                    assertThat(context.getBean("simpleSecretExceptionHandler").getClass())
                            .isNotEqualTo(SimpleSecretExceptionHandler.class);
                    assertThat(context).hasBean("simpleSecretValidationExceptionHandler");
                    assertThat(context.getBean("simpleSecretValidationExceptionHandler").getClass())
                            .isNotEqualTo(SimpleSecretValidationExceptionHandler.class);
                });
    }

    @Test
    void shouldBackOffWhenConsumerProvidesExceptionHandler() {
        SimpleSecretExceptionHandler consumerHandler =
                SimpleSecretExceptionHandler.create(new StaticMessageSource());

        withConsumerHandler("consumerExceptionHandler", SimpleSecretExceptionHandler.class, consumerHandler)
                .withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.exception-handler.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("simpleSecretExceptionHandler");
                    assertThat(context).hasBean("consumerExceptionHandler");
                    assertThat(context.getBean("consumerExceptionHandler"))
                            .isSameAs(consumerHandler);
                    assertThat(context).hasBean("simpleSecretValidationExceptionHandler");
                });
    }

    @Test
    void shouldBackOffWhenConsumerProvidesValidationExceptionHandler() {
        SimpleSecretValidationExceptionHandler consumerHandler =
                SimpleSecretValidationExceptionHandler.create();

        withConsumerHandler("consumerValidationExceptionHandler",
                SimpleSecretValidationExceptionHandler.class, consumerHandler)
                .withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.exception-handler.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("simpleSecretValidationExceptionHandler");
                    assertThat(context).hasBean("consumerValidationExceptionHandler");
                    assertThat(context.getBean("consumerValidationExceptionHandler"))
                            .isSameAs(consumerHandler);
                    assertThat(context).hasBean("simpleSecretExceptionHandler");
                });
    }

    @Test
    void shouldLoadBaseAdviceWithoutJakartaValidation() {
        webRunner.withClassLoader(new FilteredClassLoader("jakarta.validation"))
                .withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.exception-handler.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("simpleSecretExceptionHandler");
                    assertThat(context).doesNotHaveBean("simpleSecretValidationExceptionHandler");
                });
    }

    @Test
    void shouldBackOffOutsideServletWebApplication() {
        nonWebRunner.withPropertyValues("simple-secret.web.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(WebProperties.class));
    }

    @Test
    void shouldRegisterRequestTimingInterceptorAndConfigurerWhenFeatureIsEnabled() {
        webRunner.withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.request-timing.enabled=true")
                .run(context -> {
                    assertThat(context).hasBean("simpleSecretRequestTimingInterceptor");
                    assertThat(context).hasBean("requestTimingWebMvcConfigurer");
                    assertThat(context).hasSingleBean(RequestTimingInterceptor.class);
                    assertThat(context).hasSingleBean(RequestTimingWebMvcConfigurer.class);
                });
    }

    @Test
    void shouldRegisterConsumerRequestTimingInterceptorWithStarterConfigurer() {
        RequestTimingInterceptor consumerInterceptor =
                new RequestTimingInterceptor(Duration.ofSeconds(2));

        webRunner.withBean("consumerRequestTimingInterceptor", RequestTimingInterceptor.class,
                        () -> consumerInterceptor)
                .withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.request-timing.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("simpleSecretRequestTimingInterceptor");
                    assertThat(context).hasBean("requestTimingWebMvcConfigurer");
                    assertThat(context.getBean(RequestTimingInterceptor.class))
                            .isSameAs(consumerInterceptor);

                    CapturingInterceptorRegistry registry = new CapturingInterceptorRegistry();
                    context.getBean(RequestTimingWebMvcConfigurer.class).addInterceptors(registry);
                    assertThat(registry.interceptors()).containsExactly(consumerInterceptor);
                });
    }

    @Test
    void shouldBackOffRequestTimingConfigurerWhenConsumerProvidesOne() {
        RequestTimingWebMvcConfigurer consumerConfigurer = new RequestTimingWebMvcConfigurer(
                new RequestTimingInterceptor(Duration.ofSeconds(2)));

        webRunner.withBean("consumerRequestTimingWebMvcConfigurer",
                        RequestTimingWebMvcConfigurer.class, () -> consumerConfigurer)
                .withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.request-timing.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("requestTimingWebMvcConfigurer");
                    assertThat(context.getBean(RequestTimingWebMvcConfigurer.class))
                            .isSameAs(consumerConfigurer);
                });
    }

    @Test
    void shouldBackOffRequestTimingUnitWhenConsumerProvidesConfigurerWithNegativeThreshold() {
        RequestTimingWebMvcConfigurer consumerConfigurer = new RequestTimingWebMvcConfigurer(
                new RequestTimingInterceptor(Duration.ofSeconds(2)));

        webRunner.withBean("consumerRequestTimingWebMvcConfigurer",
                        RequestTimingWebMvcConfigurer.class, () -> consumerConfigurer)
                .withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.request-timing.enabled=true",
                        "simple-secret.web.request-timing.slow-request-threshold=-1ms")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("simpleSecretRequestTimingInterceptor");
                    assertThat(context).doesNotHaveBean("requestTimingWebMvcConfigurer");
                    assertThat(context.getBean(RequestTimingWebMvcConfigurer.class))
                            .isSameAs(consumerConfigurer);
                });
    }

    @Test
    void shouldFailStartupForNegativeRequestTimingThreshold() {
        webRunner.withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.request-timing.enabled=true",
                        "simple-secret.web.request-timing.slow-request-threshold=-1ms")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("non-negative");
                });
    }

    @Test
    void shouldFailStartupWhenEnabledCorsHasNoAllowedOrigin() {
        webRunner.withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.cors.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("allowed origins");
                });
    }

    @Test
    void shouldRejectWildcardAllowedOriginWithCredentialsWithoutEchoingValue() {
        String configuredOrigin = "https://*.example.com";

        webRunner.withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.cors.enabled=true",
                        "simple-secret.web.cors.allow-credentials=true",
                        "simple-secret.web.cors.allowed-origins[0]=" + configuredOrigin)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("allowed origins")
                            .hasMessageNotContaining(configuredOrigin);
                });
    }

    @Test
    void shouldRejectWildcardAllowedOriginPatternWithCredentialsWithoutEchoingValue() {
        String configuredPattern = "https://*.example.com";

        webRunner.withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.cors.enabled=true",
                        "simple-secret.web.cors.allow-credentials=true",
                        "simple-secret.web.cors.allowed-origin-patterns[0]=" + configuredPattern)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("allowed origin patterns")
                            .hasMessageNotContaining(configuredPattern);
                });
    }

    @Test
    void shouldRegisterCorsSourceAndAcceptConfiguredOriginPreflight() throws Exception {
        webRunner.withUserConfiguration(CorsTestConfiguration.class)
                .withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.cors.enabled=true",
                        "simple-secret.web.cors.allowed-origins[0]=https://app.example.com")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("simpleSecretCorsConfigurationSource");
                    assertThat(context).hasBean("webCorsWebMvcConfigurer");

                    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
                    mockMvc.perform(options("/api/messages")
                                    .header(HttpHeaders.ORIGIN, "https://app.example.com")
                                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()))
                            .andExpect(status().isOk())
                            .andExpect(header().string(
                                    HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                    "https://app.example.com"));
                });
    }

    @Test
    void shouldRejectForeignOriginPreflight() throws Exception {
        webRunner.withUserConfiguration(CorsTestConfiguration.class)
                .withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.cors.enabled=true",
                        "simple-secret.web.cors.allowed-origins[0]=https://app.example.com")
                .run(context -> {
                    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
                    MvcResult result = mockMvc.perform(options("/api/messages")
                                    .header(HttpHeaders.ORIGIN, "https://foreign.example.com")
                                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()))
                            .andReturn();

                    assertThat(result.getResponse().getStatus()).isEqualTo(403);
                    assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                            .isNull();
                });
    }

    @Test
    void shouldAcceptConfiguredOriginPatternPreflight() throws Exception {
        webRunner.withUserConfiguration(CorsTestConfiguration.class)
                .withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.cors.enabled=true",
                        "simple-secret.web.cors.allowed-origin-patterns[0]=https://*.example.com")
                .run(context -> {
                    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
                    mockMvc.perform(options("/api/messages")
                                    .header(HttpHeaders.ORIGIN, "https://api.example.com")
                                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name()))
                            .andExpect(status().isOk())
                            .andExpect(header().string(
                                    HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                    "https://api.example.com"));
                });
    }

    @Test
    void shouldBackOffCorsSourceAndConfigurerWhenConsumerProvidesCorsSource() {
        CorsConfigurationSource consumerSource = request -> null;

        webRunner.withBean("consumerCorsConfigurationSource", CorsConfigurationSource.class,
                        () -> consumerSource)
                .withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.cors.enabled=true",
                        "simple-secret.web.cors.allowed-origins[0]=https://app.example.com")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("simpleSecretCorsConfigurationSource");
                    assertThat(context).doesNotHaveBean("webCorsWebMvcConfigurer");
                    assertThat(context).hasBean("consumerCorsConfigurationSource");
                    assertThat(context.getBean(CorsConfigurationSource.class)).isSameAs(consumerSource);
                });
    }

    @Test
    void shouldBackOffCorsConfigurerWhenConsumerUsesStarterSourceBeanName() {
        CorsConfigurationSource consumerSource = request -> null;

        webRunner.withBean("simpleSecretCorsConfigurationSource", CorsConfigurationSource.class,
                        () -> consumerSource)
                .withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.cors.enabled=true",
                        "simple-secret.web.cors.allowed-origins[0]=https://app.example.com")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("webCorsWebMvcConfigurer");
                    assertThat(context.getBean(CorsConfigurationSource.class)).isSameAs(consumerSource);
                });
    }

    @Test
    void shouldBackOffCorsConfigurerWhenConsumerUsesStarterSourceBeanNameAndType() {
        UrlBasedCorsConfigurationSource consumerSource = new UrlBasedCorsConfigurationSource();

        webRunner.withBean("simpleSecretCorsConfigurationSource",
                        UrlBasedCorsConfigurationSource.class, () -> consumerSource)
                .withPropertyValues(
                        "simple-secret.web.enabled=true",
                        "simple-secret.web.cors.enabled=true",
                        "simple-secret.web.cors.allowed-origins[0]=https://app.example.com")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("webCorsWebMvcConfigurer");
                    assertThat(context.getBean(CorsConfigurationSource.class)).isSameAs(consumerSource);
                });
    }

    private void assertNoRuntimeWebBeans(
            org.springframework.boot.test.context.assertj.AssertableWebApplicationContext context) {
        assertThat(context).doesNotHaveBean(CorsConfigurationSource.class);
        assertThat(context).doesNotHaveBean(HandlerInterceptor.class);
        assertThat(context).doesNotHaveBean(RequestTimingWebMvcConfigurer.class);
        assertThat(context.getBeansWithAnnotation(ControllerAdvice.class)).isEmpty();
    }

    private void assertNoDefaultAdvice(
            org.springframework.boot.test.context.assertj.AssertableWebApplicationContext context) {
        assertThat(context).doesNotHaveBean(SimpleSecretExceptionHandler.class);
        assertThat(context).doesNotHaveBean(SimpleSecretValidationExceptionHandler.class);
        assertThat(context.getBeansWithAnnotation(ControllerAdvice.class)).isEmpty();
    }

    private void assertNoStarterRuntimeWebBeans(
            org.springframework.boot.test.context.assertj.AssertableWebApplicationContext context) {
        assertThat(context).hasNotFailed();
        assertThat(context).doesNotHaveBean("simpleSecretCorsConfigurationSource");
        assertThat(context).doesNotHaveBean("webCorsWebMvcConfigurer");
        assertThat(context).doesNotHaveBean("simpleSecretRequestTimingInterceptor");
        assertThat(context).doesNotHaveBean("requestTimingWebMvcConfigurer");
        assertThat(context).doesNotHaveBean(SimpleSecretExceptionHandler.class);
        assertThat(context).doesNotHaveBean(SimpleSecretValidationExceptionHandler.class);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private WebApplicationContextRunner withConsumerHandler(
            String beanName, Class<?> handlerType, Object handler) {
        return webRunner.withBean(
                beanName, (Class) handlerType, () -> handler);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(WebProperties.class)
    static class PropertiesConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class CorsTestConfiguration {

        @RestController
        static class CorsTestController {

            @GetMapping("/api/messages")
            String getMessages() {
                return "ok";
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ComponentScan(
            basePackages = "com.ss.web",
            excludeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = "com\\.ss\\.web\\..*Test.*"))
    static class BroadComponentScanConfiguration {

        @RestController
        static class AdviceProbeController {

            @GetMapping("/component-scan/advice-failure")
            String adviceFailure() {
                throw new com.ss.core.exception.ServiceException("internal-component-scan-marker");
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackages = "com.ss.web",
            excludeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = "com\\.ss\\.web\\..*Test.*"))
    static class NonServletBroadComponentScanConfiguration {
    }

    static class CapturingInterceptorRegistry extends InterceptorRegistry {

        List<Object> interceptors() {
            return getInterceptors();
        }
    }
}
