package com.ss.idempotent.config;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ss.idempotent.aspect.RepeatSubmitAspect;
import com.ss.idempotent.key.DefaultIdempotencyKeyGenerator;
import com.ss.idempotent.key.DefaultRequestIdentityResolver;
import com.ss.idempotent.key.IdempotencyKeyGenerator;
import com.ss.idempotent.key.RequestIdentityResolver;
import com.ss.idempotent.store.IdempotencyStore;
import com.ss.idempotent.store.RedissonIdempotencyStore;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/** Simple Secret Servlet 重复提交保护自动配置。 */
@AutoConfiguration
@ConditionalOnClass({Aspect.class, HttpServletRequest.class, JsonMapper.class})
@ConditionalOnProperty(
        prefix = "simple-secret.idempotent",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(IdempotentProperties.class)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class SimpleSecretIdempotentAutoConfiguration {

    /**
     * 创建默认请求身份 resolver。
     *
     * @param properties starter 配置
     * @return 默认身份 resolver
     */
    @Bean
    @ConditionalOnMissingBean
    RequestIdentityResolver requestIdentityResolver(IdempotentProperties properties) {
        return new DefaultRequestIdentityResolver(properties.getIdentityHeader());
    }

    /**
     * 创建默认 SHA-256 key generator。
     *
     * @param identityResolver 请求身份 resolver
     * @param properties starter 配置
     * @return 默认 key generator
     */
    @Bean
    @ConditionalOnMissingBean
    IdempotencyKeyGenerator idempotencyKeyGenerator(
            RequestIdentityResolver identityResolver,
            IdempotentProperties properties) {
        return new DefaultIdempotencyKeyGenerator(
                identityResolver, properties.getKeyPrefix());
    }

    /**
     * 创建切面；缺少 Store 时依赖注入会阻止应用启动。
     *
     * @param store 原子幂等存储
     * @param keyGenerator key generator
     * @param messageSource Spring 消息源
     * @return 重复提交切面
     */
    @Bean
    @ConditionalOnMissingBean
    RepeatSubmitAspect repeatSubmitAspect(
            IdempotencyStore store,
            IdempotencyKeyGenerator keyGenerator,
            MessageSource messageSource) {
        return new RepeatSubmitAspect(store, keyGenerator, messageSource);
    }

    /** 在应用已提供 RedissonClient 时创建默认 Store。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RedissonClient.class)
    static class RedissonStoreConfiguration {

        /**
         * 创建 Redisson Store。
         *
         * @param client 应用管理的 Redisson 客户端
         * @return Redisson 幂等存储
         */
        @Bean
        @ConditionalOnBean(RedissonClient.class)
        @ConditionalOnMissingBean(IdempotencyStore.class)
        RedissonIdempotencyStore redissonIdempotencyStore(RedissonClient client) {
            return new RedissonIdempotencyStore(client);
        }
    }
}
