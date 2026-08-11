package com.ss.auth.config;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpLogic;
import com.ss.auth.service.ClientService;
import com.ss.auth.strategy.AuthStrategyRegistry;
import com.ss.auth.support.LoginHelper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 Auth starter 自动配置的启用条件和消费者覆盖边界。 */
class SimpleSecretAuthAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleSecretAuthAutoConfiguration.class));

    @Test
    void shouldRemainDisabledByDefault() {
        runner.run(context -> assertThat(context)
                .doesNotHaveBean(AuthProperties.class)
                .doesNotHaveBean(StpLogic.class)
                .doesNotHaveBean(LoginHelper.class)
                .doesNotHaveBean(StpInterface.class)
                .doesNotHaveBean(AuthStrategyRegistry.class));
    }

    @Test
    void shouldCreateDefaultRuntimeBeansOnlyWhenEnabled() {
        runner.withPropertyValues("simple-secret.auth.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(AuthProperties.class)
                        .hasSingleBean(StpLogic.class)
                        .hasSingleBean(LoginHelper.class)
                        .hasSingleBean(StpInterface.class)
                        .doesNotHaveBean(AuthStrategyRegistry.class));
    }

    @Test
    void shouldCreateStrategyRegistryWhenClientServiceIsProvided() {
        runner.withUserConfiguration(ClientServiceConfiguration.class)
                .withPropertyValues("simple-secret.auth.enabled=true")
                .run(context -> assertThat(context).hasSingleBean(AuthStrategyRegistry.class));
    }

    @Test
    void shouldBackOffForConsumerBeansOfEachDefaultType() {
        runner.withUserConfiguration(ConsumerBeansConfiguration.class)
                .withPropertyValues("simple-secret.auth.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(StpLogic.class);
                    assertThat(context).hasSingleBean(LoginHelper.class);
                    assertThat(context).hasSingleBean(StpInterface.class);
                    assertThat(context).hasSingleBean(AuthStrategyRegistry.class);
                    assertThat(context.getBean(StpLogic.class)).isSameAs(ConsumerBeansConfiguration.STP_LOGIC);
                    assertThat(context.getBean(LoginHelper.class)).isSameAs(ConsumerBeansConfiguration.LOGIN_HELPER);
                    assertThat(context.getBean(StpInterface.class)).isSameAs(ConsumerBeansConfiguration.STP_INTERFACE);
                    assertThat(context.getBean(AuthStrategyRegistry.class))
                            .isSameAs(ConsumerBeansConfiguration.AUTH_STRATEGY_REGISTRY);
                });
    }

    @Test
    void shouldNotCreateRuntimeBeansWhenBroadComponentScanRunsWhileDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(AuthComponentScanConfiguration.class)
                .run(context -> assertThat(context)
                        .doesNotHaveBean(StpLogic.class)
                        .doesNotHaveBean(LoginHelper.class)
                        .doesNotHaveBean(StpInterface.class)
                        .doesNotHaveBean(AuthStrategyRegistry.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class ClientServiceConfiguration {

        @Bean
        ClientService clientService() {
            return clientId -> null;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerBeansConfiguration {
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
        AuthStrategyRegistry consumerAuthStrategyRegistry() {
            return AUTH_STRATEGY_REGISTRY;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(
            basePackages = "com.ss.auth",
            excludeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = {
                            ClientServiceConfiguration.class,
                            ConsumerBeansConfiguration.class,
                            AuthComponentScanConfiguration.class
                    }))
    static class AuthComponentScanConfiguration {
    }
}
