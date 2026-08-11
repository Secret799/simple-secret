package com.ss.tenant.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.ss.mybatis.config.MybatisPlusInterceptorCustomizer;
import com.ss.mybatis.config.SimpleSecretMybatisAutoConfiguration;
import com.ss.tenant.context.TenantContext;
import com.ss.tenant.context.TenantContextProvider;
import com.ss.tenant.handler.SimpleSecretTenantLineHandler;
import com.ss.tenant.interceptor.SimpleSecretTenantLineInnerInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Simple Secret MyBatis-Plus 行级租户自动配置。 */
@AutoConfiguration(
        before = SimpleSecretMybatisAutoConfiguration.class,
        afterName = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@ConditionalOnClass({
        TenantLineInnerInterceptor.class,
        MybatisPlusInterceptorCustomizer.class,
        SqlSessionFactory.class
})
@ConditionalOnBean(SqlSessionFactory.class)
@ConditionalOnProperty(
        prefix = "simple-secret.tenant",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(TenantProperties.class)
public class SimpleSecretTenantAutoConfiguration {

    /**
     * 提供默认空租户来源，使缺失租户在 SQL 执行时失败关闭。
     *
     * @return 空租户 provider
     */
    @Bean
    @ConditionalOnMissingBean
    TenantContextProvider tenantContextProvider() {
        return TenantContextProvider.empty();
    }

    /**
     * 创建实例化租户上下文。
     *
     * @param provider 租户来源
     * @return 租户上下文
     */
    @Bean
    @ConditionalOnMissingBean
    TenantContext tenantContext(TenantContextProvider provider) {
        return new TenantContext(provider);
    }

    /**
     * 创建租户 SQL handler。
     *
     * @param context 租户上下文
     * @param properties 租户配置
     * @return 租户 SQL handler
     */
    @Bean
    @ConditionalOnMissingBean(TenantLineHandler.class)
    SimpleSecretTenantLineHandler tenantLineHandler(
            TenantContext context,
            TenantProperties properties) {
        return new SimpleSecretTenantLineHandler(context, properties);
    }

    /**
     * 创建 MyBatis-Plus 租户拦截器。
     *
     * @param handler 租户 SQL handler
     * @return 租户拦截器
     */
    @Bean
    @ConditionalOnMissingBean
    TenantLineInnerInterceptor tenantLineInnerInterceptor(TenantLineHandler handler) {
        return new SimpleSecretTenantLineInnerInterceptor(handler);
    }

    /**
     * 创建负责插件顺序的租户 customizer。
     *
     * @param tenantInterceptor 租户拦截器
     * @return 租户 customizer
     */
    @Bean
    @ConditionalOnMissingBean
    TenantMybatisPlusInterceptorCustomizer tenantMybatisPlusInterceptorCustomizer(
            TenantLineInnerInterceptor tenantInterceptor) {
        return new TenantMybatisPlusInterceptorCustomizer(tenantInterceptor);
    }

    /**
     * 验证最终 MyBatis-Plus 插件链没有绕过租户隔离。
     *
     * @param interceptors 实际生效的 MyBatis-Plus 拦截器容器
     * @param tenantInterceptor 当前自动配置的租户拦截器
     * @return 启动期租户隔离验证器
     */
    @Bean
    TenantIsolationVerifier tenantIsolationVerifier(
            ObjectProvider<MybatisPlusInterceptor> interceptors,
            TenantLineInnerInterceptor tenantInterceptor) {
        return new TenantIsolationVerifier(interceptors, tenantInterceptor);
    }
}
