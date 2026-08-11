package com.ss.mybatis.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.ss.mybatis.audit.AuditContext;
import com.ss.mybatis.audit.AuditContextProvider;
import com.ss.mybatis.audit.SimpleSecretMetaObjectHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 MyBatis-Plus 自动配置条件、插件顺序和消费者覆盖。 */
class SimpleSecretMybatisAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleSecretMybatisAutoConfiguration.class));

    @Test
    void shouldRemainInactiveWithoutSqlSessionFactory() {
        runner.run(context -> assertThat(context)
                .doesNotHaveBean(MybatisStarterProperties.class)
                .doesNotHaveBean(AuditContextProvider.class)
                .doesNotHaveBean(MetaObjectHandler.class)
                .doesNotHaveBean(MybatisPlusInterceptor.class));
    }

    @Test
    void shouldCreateSafeDefaultEnhancements() {
        runner.withBean(SqlSessionFactory.class,
                        SimpleSecretMybatisAutoConfigurationTest::sqlSessionFactory)
                .run(context -> {
                    assertThat(context).hasSingleBean(MybatisStarterProperties.class);
                    assertThat(context).hasSingleBean(AuditContextProvider.class);
                    assertThat(context).hasSingleBean(SimpleSecretMetaObjectHandler.class);
                    assertThat(context).hasSingleBean(MybatisPlusInterceptor.class);

                    MybatisPlusInterceptor interceptor = context.getBean(MybatisPlusInterceptor.class);
                    assertThat(interceptor.getInterceptors())
                            .hasExactlyElementsOfTypes(
                                    PaginationInnerInterceptor.class,
                                    OptimisticLockerInnerInterceptor.class);
                    PaginationInnerInterceptor pagination =
                            (PaginationInnerInterceptor) interceptor.getInterceptors().get(0);
                    assertThat(pagination.getMaxLimit()).isEqualTo(500L);
                    assertThat(pagination.isOverflow()).isFalse();
                });
    }

    @Test
    void shouldHonorFeatureSwitches() {
        runner.withBean(SqlSessionFactory.class,
                        SimpleSecretMybatisAutoConfigurationTest::sqlSessionFactory)
                .withPropertyValues(
                        "simple-secret.mybatis.pagination-enabled=false",
                        "simple-secret.mybatis.optimistic-locker-enabled=false")
                .run(context -> assertThat(context.getBean(MybatisPlusInterceptor.class)
                        .getInterceptors()).isEmpty());

        runner.withBean(SqlSessionFactory.class,
                        SimpleSecretMybatisAutoConfigurationTest::sqlSessionFactory)
                .withPropertyValues("simple-secret.mybatis.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(MybatisStarterProperties.class)
                        .doesNotHaveBean(AuditContextProvider.class)
                        .doesNotHaveBean(MetaObjectHandler.class)
                        .doesNotHaveBean(MybatisPlusInterceptor.class));
    }

    @Test
    void shouldApplyOrderedCustomizersBeforeBuiltInInterceptors() {
        runner.withBean(SqlSessionFactory.class,
                        SimpleSecretMybatisAutoConfigurationTest::sqlSessionFactory)
                .withUserConfiguration(OrderedCustomizerConfiguration.class)
                .run(context -> assertThat(context.getBean(MybatisPlusInterceptor.class)
                        .getInterceptors()).hasExactlyElementsOfTypes(
                                FirstInnerInterceptor.class,
                                SecondInnerInterceptor.class,
                                PaginationInnerInterceptor.class,
                                OptimisticLockerInnerInterceptor.class));
    }

    @Test
    void shouldBackOffForConsumerBeans() {
        AuditContextProvider provider = () -> new AuditContext(9L, 2L);
        MetaObjectHandler handler = new SimpleSecretMetaObjectHandler(provider);
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        runner.withBean(SqlSessionFactory.class,
                        SimpleSecretMybatisAutoConfigurationTest::sqlSessionFactory)
                .withBean(AuditContextProvider.class, () -> provider)
                .withBean(MetaObjectHandler.class, () -> handler)
                .withBean(MybatisPlusInterceptor.class, () -> interceptor)
                .run(context -> {
                    assertThat(context.getBean(AuditContextProvider.class)).isSameAs(provider);
                    assertThat(context.getBean(MetaObjectHandler.class)).isSameAs(handler);
                    assertThat(context.getBean(MybatisPlusInterceptor.class)).isSameAs(interceptor);
                });
    }

    @Test
    void shouldRemainInactiveWithoutMybatisPlusClasses() {
        runner.withClassLoader(new FilteredClassLoader(MybatisPlusInterceptor.class))
                .withBean(SqlSessionFactory.class,
                        SimpleSecretMybatisAutoConfigurationTest::sqlSessionFactory)
                .run(context -> assertThat(context)
                        .doesNotHaveBean(MybatisStarterProperties.class)
                        .doesNotHaveBean(MybatisPlusInterceptor.class));
    }

    private static SqlSessionFactory sqlSessionFactory() {
        return (SqlSessionFactory) Proxy.newProxyInstance(
                SqlSessionFactory.class.getClassLoader(),
                new Class<?>[]{SqlSessionFactory.class},
                (proxy, method, args) -> {
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType().isPrimitive()) {
                        return 0;
                    }
                    return null;
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class OrderedCustomizerConfiguration {

        @Bean
        @Order(20)
        MybatisPlusInterceptorCustomizer secondCustomizer() {
            return interceptor -> interceptor.addInnerInterceptor(
                    new SecondInnerInterceptor());
        }

        @Bean
        @Order(10)
        MybatisPlusInterceptorCustomizer firstCustomizer() {
            return interceptor -> interceptor.addInnerInterceptor(
                    new FirstInnerInterceptor());
        }
    }

    private static final class FirstInnerInterceptor implements InnerInterceptor {
    }

    private static final class SecondInnerInterceptor implements InnerInterceptor {
    }
}
