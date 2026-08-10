package com.ss.json.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ss.json.JsonCodec;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * 优先使用宿主的唯一或 primary {@link ObjectMapper} 提供 {@link JsonCodec}，
 * 没有 mapper Bean 时回退到独立默认 mapper。
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnClass({ObjectMapper.class, JsonCodec.class})
@ConditionalOnProperty(prefix = "simple-secret.json", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SimpleSecretJsonCodecAutoConfiguration {
    /**
     * 在用户未提供 codec 时创建默认 Bean。
     *
     * @param objectMapper Spring Boot 管理的 mapper
     * @return JSON codec
     */
    @Bean
    @ConditionalOnMissingBean(JsonCodec.class)
    @ConditionalOnSingleCandidate(ObjectMapper.class)
    JsonCodec simpleSecretJsonCodec(ObjectMapper objectMapper) {
        return new JsonCodec(objectMapper);
    }

    /**
     * 在非 Web Boot 环境没有 mapper Bean 时创建独立 codec。
     *
     * @return 使用默认 mapper 的 JSON codec
     */
    @Bean
    @ConditionalOnMissingBean({JsonCodec.class, ObjectMapper.class})
    JsonCodec simpleSecretStandaloneJsonCodec() {
        return new JsonCodec(DefaultObjectMapperFactory.create());
    }
}
