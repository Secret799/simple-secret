package com.ss.security.config;

import cn.dev33.satoken.stp.StpLogic;
import com.ss.security.web.LoginRequiredInterceptor;
import com.ss.security.web.SecurityWebMvcConfigurer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证 Security 自动配置条件、消费者覆盖和真实路由行为。 */
class SimpleSecretSecurityAutoConfigurationTest {

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleSecretSecurityAutoConfiguration.class));

    private final ApplicationContextRunner nonWebRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleSecretSecurityAutoConfiguration.class));

    @Test
    void shouldRemainDisabledByDefault() {
        webRunner.run(context -> assertThat(context)
                .doesNotHaveBean(SecurityProperties.class)
                .doesNotHaveBean(StpLogic.class)
                .doesNotHaveBean(LoginRequiredInterceptor.class)
                .doesNotHaveBean(SecurityWebMvcConfigurer.class));
    }

    @Test
    void shouldCreateDefaultBeansOnlyWhenEnabledInServletApplication() {
        webRunner.withPropertyValues("simple-secret.security.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(SecurityProperties.class)
                        .hasSingleBean(StpLogic.class)
                        .hasSingleBean(LoginRequiredInterceptor.class)
                        .hasSingleBean(SecurityWebMvcConfigurer.class));
    }

    @Test
    void shouldRemainDisabledOutsideServletApplication() {
        nonWebRunner.withPropertyValues("simple-secret.security.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(SecurityProperties.class)
                        .doesNotHaveBean(StpLogic.class)
                        .doesNotHaveBean(LoginRequiredInterceptor.class)
                        .doesNotHaveBean(SecurityWebMvcConfigurer.class));
    }

    @Test
    void shouldRemainDisabledWhenDispatcherServletIsUnavailable() {
        webRunner.withClassLoader(new FilteredClassLoader(
                        "org.springframework.web.servlet.DispatcherServlet"))
                .withPropertyValues("simple-secret.security.enabled=true")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(SecurityProperties.class)
                        .doesNotHaveBean(StpLogic.class)
                        .doesNotHaveBean(LoginRequiredInterceptor.class)
                        .doesNotHaveBean(SecurityWebMvcConfigurer.class));
    }

    @Test
    void shouldBackOffForConsumerStpLogic() {
        webRunner.withBean(StpLogic.class, () -> ConsumerBeans.STP_LOGIC)
                .withPropertyValues("simple-secret.security.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(StpLogic.class);
                    assertThat(context.getBean(StpLogic.class)).isSameAs(ConsumerBeans.STP_LOGIC);
                    assertThat(context).hasSingleBean(LoginRequiredInterceptor.class);
                    assertThat(context).hasSingleBean(SecurityWebMvcConfigurer.class);
                });
    }

    @Test
    void shouldBackOffForConsumerInterceptor() {
        webRunner.withBean(LoginRequiredInterceptor.class, () -> ConsumerBeans.INTERCEPTOR)
                .withPropertyValues("simple-secret.security.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(LoginRequiredInterceptor.class);
                    assertThat(context.getBean(LoginRequiredInterceptor.class))
                            .isSameAs(ConsumerBeans.INTERCEPTOR);
                    assertThat(context).hasSingleBean(StpLogic.class);
                    assertThat(context).hasSingleBean(SecurityWebMvcConfigurer.class);
                });
    }

    @Test
    void shouldBackOffForConsumerConfigurer() {
        webRunner.withBean(SecurityWebMvcConfigurer.class, () -> ConsumerBeans.CONFIGURER)
                .withPropertyValues("simple-secret.security.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(SecurityWebMvcConfigurer.class);
                    assertThat(context.getBean(SecurityWebMvcConfigurer.class))
                            .isSameAs(ConsumerBeans.CONFIGURER);
                    assertThat(context).hasSingleBean(StpLogic.class);
                    assertThat(context).hasSingleBean(LoginRequiredInterceptor.class);
                });
    }

    @Test
    void shouldNotBypassDisabledSwitchThroughBroadComponentScan() {
        new WebApplicationContextRunner()
                .withUserConfiguration(BroadComponentScanConfiguration.class)
                .run(context -> assertThat(context)
                        .doesNotHaveBean(SecurityProperties.class)
                        .doesNotHaveBean(StpLogic.class)
                        .doesNotHaveBean(LoginRequiredInterceptor.class)
                        .doesNotHaveBean(SecurityWebMvcConfigurer.class));
    }

    @Test
    void shouldApplyOnlyConfiguredProtectedRoute() {
        webRunner.withUserConfiguration(RouteConfiguration.class)
                .withPropertyValues(
                        "simple-secret.security.enabled=true",
                        "simple-secret.security.path-patterns[0]=/private/**",
                        "simple-secret.security.exclude-path-patterns[0]=/private/public",
                        "simple-secret.security.order=25")
                .run(context -> {
                    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
                    CountingStpLogic stpLogic = context.getBean(CountingStpLogic.class);

                    mockMvc.perform(get("/private/data")).andExpect(status().isOk());
                    assertThat(stpLogic.checkCount()).isOne();

                    mockMvc.perform(get("/private/public")).andExpect(status().isOk());
                    mockMvc.perform(get("/outside")).andExpect(status().isOk());
                    assertThat(stpLogic.checkCount()).isOne();
                });
    }

    @Test
    void shouldLeaveRoutesUnprotectedWhenIncludePatternsAreEmpty() {
        new WebApplicationContextRunner()
                .withUserConfiguration(EmptyPathRouteConfiguration.class)
                .run(context -> {
                    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
                    CountingStpLogic stpLogic = context.getBean(CountingStpLogic.class);

                    mockMvc.perform(get("/private/data")).andExpect(status().isOk());

                    assertThat(stpLogic.checkCount()).isZero();
                });
    }

    private static final class ConsumerBeans {
        private static final StpLogic STP_LOGIC = new StpLogic("consumer");
        private static final LoginRequiredInterceptor INTERCEPTOR =
                new LoginRequiredInterceptor(STP_LOGIC);
        private static final SecurityWebMvcConfigurer CONFIGURER =
                new SecurityWebMvcConfigurer(INTERCEPTOR, new SecurityProperties());
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackages = "com.ss.security",
            excludeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {
                            SimpleSecretSecurityAutoConfigurationTest.class,
                            BroadComponentScanConfiguration.class,
                            RouteConfiguration.class,
                            EmptyPathRouteConfiguration.class
                    }))
    static class BroadComponentScanConfiguration {
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class RouteConfiguration {

        @Bean
        CountingStpLogic countingStpLogic() {
            return new CountingStpLogic();
        }

        @Bean
        RouteController routeController() {
            return new RouteController();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class EmptyPathRouteConfiguration {

        @Bean
        CountingStpLogic countingStpLogic() {
            return new CountingStpLogic();
        }

        @Bean
        RouteController routeController() {
            return new RouteController();
        }

        @Bean
        SecurityWebMvcConfigurer securityWebMvcConfigurer(CountingStpLogic stpLogic) {
            SecurityProperties properties = new SecurityProperties();
            properties.setPathPatterns(java.util.List.of());
            return new SecurityWebMvcConfigurer(
                    new LoginRequiredInterceptor(stpLogic), properties);
        }
    }

    @RestController
    static class RouteController {

        @GetMapping({"/private/data", "/private/public", "/outside"})
        String data() {
            return "ok";
        }
    }

    static final class CountingStpLogic extends StpLogic {
        private int checkCount;

        CountingStpLogic() {
            super("test");
        }

        @Override
        public void checkLogin() {
            checkCount++;
        }

        int checkCount() {
            return checkCount;
        }
    }
}
