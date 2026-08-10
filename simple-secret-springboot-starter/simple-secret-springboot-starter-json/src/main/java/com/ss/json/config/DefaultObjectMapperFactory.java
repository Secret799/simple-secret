package com.ss.json.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ss.json.jackson.SimpleSecretJsonModule;

import java.util.TimeZone;

/**
 * 创建应用默认的、无 Spring 依赖的 Jackson {@link ObjectMapper}。
 */
public final class DefaultObjectMapperFactory {
    private DefaultObjectMapperFactory() {
    }

    /**
     * 创建使用 Simple Secret JSON 规则的新 mapper。
     *
     * @return 独立且已完成配置的 mapper
     */
    public static ObjectMapper create() {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .addModule(new SimpleSecretJsonModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        mapper.setTimeZone(TimeZone.getDefault());
        return mapper;
    }
}
