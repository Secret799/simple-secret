package com.ss.json.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ss.json.jackson.SimpleSecretJsonModule;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.TimeZone;

/**
 * 将 Simple Secret JSON 规则增量应用到 Spring Boot 管理的 Jackson mapper。
 */
@AutoConfiguration(before = JacksonAutoConfiguration.class)
@ConditionalOnClass({ObjectMapper.class, Jackson2ObjectMapperBuilder.class})
@ConditionalOnProperty(prefix = "simple-secret.json", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SimpleSecretJacksonAutoConfiguration {
    /**
     * 将 Simple Secret 序列化规则交给 Spring Boot 的模块发现机制安装。
     *
     * @return Simple Secret Jackson 模块
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnMissingBean(SimpleSecretJsonModule.class)
    @ConditionalOnProperty(prefix = "simple-secret.json", name = "jackson-customization-enabled",
            havingValue = "true")
    SimpleSecretJsonModule simpleSecretJsonModule() {
        return new SimpleSecretJsonModule();
    }

    /**
     * 将默认模块放在宿主模块之前，确保同类型的宿主序列化器拥有最终决定权。
     *
     * @param module Simple Secret Jackson 模块
     * @return 模块顺序定制器
     */
    @Bean
    @Order(1)
    @ConditionalOnProperty(prefix = "simple-secret.json", name = "jackson-customization-enabled",
            havingValue = "true")
    Jackson2ObjectMapperBuilderCustomizer simpleSecretJacksonModuleCustomizer(SimpleSecretJsonModule module) {
        return builder -> builder.modulesToInstall(modules -> {
            modules.remove(module);
            modules.add(0, module);
        });
    }

    /**
     * 创建保留用户模块的 Jackson builder 定制器。
     *
     * @return Jackson builder 定制器
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @ConditionalOnProperty(prefix = "simple-secret.json", name = "jackson-customization-enabled",
            havingValue = "true")
    Jackson2ObjectMapperBuilderCustomizer simpleSecretJacksonCustomizer() {
        return builder -> {
            builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                    DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE,
                    SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            builder.timeZone(TimeZone.getDefault());
        };
    }
}
