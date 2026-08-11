package com.ss.tenant.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.ss.mybatis.config.SimpleSecretMybatisAutoConfiguration;
import com.ss.tenant.context.TenantContext;
import com.ss.tenant.context.TenantContextProvider;
import com.ss.tenant.handler.SimpleSecretTenantLineHandler;
import com.ss.tenant.interceptor.SimpleSecretTenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.StringValue;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证租户自动配置条件、插件顺序和消费者覆盖。 */
class SimpleSecretTenantAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SimpleSecretTenantAutoConfiguration.class,
                    SimpleSecretMybatisAutoConfiguration.class));

    @Test
    void shouldRemainInactiveWithoutSqlSessionFactory() {
        runner.run(context -> assertThat(context)
                .doesNotHaveBean(TenantProperties.class)
                .doesNotHaveBean(TenantContextProvider.class)
                .doesNotHaveBean(TenantContext.class)
                .doesNotHaveBean(TenantLineHandler.class)
                .doesNotHaveBean(TenantLineInnerInterceptor.class)
                .doesNotHaveBean(TenantMybatisPlusInterceptorCustomizer.class));
    }

    @Test
    void shouldCreateFailClosedTenantPluginBeforeBuiltIns() {
        runner.withBean(SqlSessionFactory.class,
                        SimpleSecretTenantAutoConfigurationTest::sqlSessionFactory)
                .withPropertyValues(
                        "simple-secret.tenant.column=account_id",
                        "simple-secret.tenant.excluded-tables[0]=audit_log")
                .run(context -> {
                    assertThat(context).hasSingleBean(TenantProperties.class);
                    assertThat(context).hasSingleBean(TenantContextProvider.class);
                    assertThat(context).hasSingleBean(TenantContext.class);
                    assertThat(context).hasSingleBean(SimpleSecretTenantLineHandler.class);
                    assertThat(context).hasSingleBean(TenantLineInnerInterceptor.class);
                    assertThat(context)
                            .hasSingleBean(TenantMybatisPlusInterceptorCustomizer.class);

                    TenantProperties properties = context.getBean(TenantProperties.class);
                    assertThat(properties.getColumn()).isEqualTo("account_id");
                    assertThat(properties.isExcludedTable("AUDIT_LOG")).isTrue();

                    assertThat(context.getBean(MybatisPlusInterceptor.class).getInterceptors())
                            .hasExactlyElementsOfTypes(
                                    SimpleSecretTenantLineInnerInterceptor.class,
                                    PaginationInnerInterceptor.class,
                                    OptimisticLockerInnerInterceptor.class);
                });
    }

    @Test
    void shouldHonorFeatureSwitch() {
        runner.withBean(SqlSessionFactory.class,
                        SimpleSecretTenantAutoConfigurationTest::sqlSessionFactory)
                .withPropertyValues("simple-secret.tenant.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(TenantProperties.class)
                        .doesNotHaveBean(TenantContextProvider.class)
                        .doesNotHaveBean(TenantContext.class)
                        .doesNotHaveBean(TenantLineInnerInterceptor.class)
                        .doesNotHaveBean(TenantMybatisPlusInterceptorCustomizer.class));
    }

    @Test
    void shouldFailStartupWhenBaseMybatisEnhancementsAreDisabled() {
        runner.withBean(SqlSessionFactory.class,
                        SimpleSecretTenantAutoConfigurationTest::sqlSessionFactory)
                .withPropertyValues("simple-secret.mybatis.enabled=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage(
                                    "Tenant isolation requires exactly one MybatisPlusInterceptor");
                });
    }

    @Test
    void shouldFailStartupWhenConsumerInterceptorOmitsTenantPlugin() {
        runner.withBean(SqlSessionFactory.class,
                        SimpleSecretTenantAutoConfigurationTest::sqlSessionFactory)
                .withUserConfiguration(InterceptorWithoutTenantConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage(
                                    "MybatisPlusInterceptor must contain the configured tenant interceptor exactly once");
                });
    }

    @Test
    void shouldFailStartupWhenTenantPluginRunsAfterPagination() {
        runner.withBean(SqlSessionFactory.class,
                        SimpleSecretTenantAutoConfigurationTest::sqlSessionFactory)
                .withUserConfiguration(InterceptorWithUnsafeOrderConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage(
                                    "Tenant interceptor must run before pagination and optimistic locking");
                });
    }

    @Test
    void shouldFailStartupWhenConsumerTenantInterceptorHasNoWriteGuard() {
        runner.withBean(SqlSessionFactory.class,
                        SimpleSecretTenantAutoConfigurationTest::sqlSessionFactory)
                .withUserConfiguration(InterceptorWithoutWriteGuardConfiguration.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage("Tenant interceptor must enforce protected tenant writes");
                });
    }

    @Test
    void shouldBackOffForConsumerBeans() {
        runner.withBean(SqlSessionFactory.class,
                        SimpleSecretTenantAutoConfigurationTest::sqlSessionFactory)
                .withUserConfiguration(ConsumerTenantConfiguration.class)
                .run(context -> {
                    assertThat(context.getBean(TenantContextProvider.class))
                            .isSameAs(ConsumerTenantConfiguration.PROVIDER);
                    assertThat(context.getBean(TenantContext.class))
                            .isSameAs(ConsumerTenantConfiguration.CONTEXT);
                    assertThat(context.getBean(TenantLineHandler.class))
                            .isSameAs(ConsumerTenantConfiguration.HANDLER);
                    assertThat(context.getBean(TenantLineInnerInterceptor.class))
                            .isSameAs(ConsumerTenantConfiguration.INTERCEPTOR);
                    assertThat(context.getBean(TenantMybatisPlusInterceptorCustomizer.class))
                            .isSameAs(ConsumerTenantConfiguration.CUSTOMIZER);
                });
    }

    private static SqlSessionFactory sqlSessionFactory() {
        return (SqlSessionFactory) Proxy.newProxyInstance(
                SqlSessionFactory.class.getClassLoader(),
                new Class<?>[]{SqlSessionFactory.class},
                (proxy, method, args) -> null);
    }

    @Configuration(proxyBeanMethods = false)
    static class ConsumerTenantConfiguration {
        private static final TenantContextProvider PROVIDER = () -> "consumer";
        private static final TenantContext CONTEXT = new TenantContext(PROVIDER);
        private static final TenantLineHandler HANDLER = () -> new StringValue("consumer");
        private static final TenantLineInnerInterceptor INTERCEPTOR =
                new SimpleSecretTenantLineInnerInterceptor(HANDLER);
        private static final TenantMybatisPlusInterceptorCustomizer CUSTOMIZER =
                new TenantMybatisPlusInterceptorCustomizer(INTERCEPTOR);

        @Bean
        TenantContextProvider consumerTenantContextProvider() {
            return PROVIDER;
        }

        @Bean
        TenantContext consumerTenantContext() {
            return CONTEXT;
        }

        @Bean
        TenantLineHandler consumerTenantLineHandler() {
            return HANDLER;
        }

        @Bean
        TenantLineInnerInterceptor consumerTenantLineInnerInterceptor() {
            return INTERCEPTOR;
        }

        @Bean
        TenantMybatisPlusInterceptorCustomizer consumerTenantCustomizer() {
            return CUSTOMIZER;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InterceptorWithoutTenantConfiguration {

        @Bean
        MybatisPlusInterceptor applicationMybatisPlusInterceptor() {
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
            return interceptor;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InterceptorWithUnsafeOrderConfiguration {

        @Bean
        MybatisPlusInterceptor applicationMybatisPlusInterceptor(
                TenantLineInnerInterceptor tenantInterceptor) {
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor());
            interceptor.addInnerInterceptor(tenantInterceptor);
            return interceptor;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InterceptorWithoutWriteGuardConfiguration {

        @Bean
        TenantLineInnerInterceptor consumerTenantLineInnerInterceptor(
                TenantLineHandler handler) {
            return new TenantLineInnerInterceptor(handler);
        }

        @Bean
        MybatisPlusInterceptor consumerMybatisPlusInterceptor(
                TenantLineInnerInterceptor tenantInterceptor) {
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(tenantInterceptor);
            return interceptor;
        }
    }
}
