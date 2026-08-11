package com.ss.mybatis.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.ss.mybatis.audit.AuditContextProvider;
import com.ss.mybatis.audit.SimpleSecretMetaObjectHandler;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Simple Secret MyBatis-Plus 基础增强自动配置。 */
@AutoConfiguration(afterName = "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration")
@ConditionalOnClass({MybatisPlusInterceptor.class, SqlSessionFactory.class})
@ConditionalOnBean(SqlSessionFactory.class)
@ConditionalOnProperty(
        prefix = "simple-secret.mybatis",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(MybatisStarterProperties.class)
public class SimpleSecretMybatisAutoConfiguration {

    /**
     * 提供不绑定认证系统的默认审计上下文。
     *
     * @return 空审计上下文 provider
     */
    @Bean
    @ConditionalOnMissingBean
    AuditContextProvider auditContextProvider() {
        return AuditContextProvider.empty();
    }

    /**
     * 创建基础实体审计字段处理器。
     *
     * @param contextProvider 审计上下文 provider
     * @return MyBatis-Plus 元对象处理器
     */
    @Bean
    @ConditionalOnMissingBean(MetaObjectHandler.class)
    SimpleSecretMetaObjectHandler simpleSecretMetaObjectHandler(
            AuditContextProvider contextProvider) {
        return new SimpleSecretMetaObjectHandler(contextProvider);
    }

    /**
     * 按固定顺序创建分页和乐观锁拦截器。
     *
     * @param properties starter 配置
     * @return MyBatis-Plus 拦截器容器
     */
    @Bean
    @ConditionalOnMissingBean
    MybatisPlusInterceptor mybatisPlusInterceptor(
            MybatisStarterProperties properties,
            ObjectProvider<MybatisPlusInterceptorCustomizer> customizers) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        customizers.orderedStream().forEach(customizer -> customizer.customize(interceptor));
        if (properties.isPaginationEnabled()) {
            PaginationInnerInterceptor pagination = new PaginationInnerInterceptor();
            pagination.setMaxLimit(properties.getMaxPageSize());
            pagination.setOverflow(properties.isOverflow());
            interceptor.addInnerInterceptor(pagination);
        }
        if (properties.isOptimisticLockerEnabled()) {
            interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        }
        return interceptor;
    }
}
