package com.ss.json.config;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.ss.json.JsonCodec;
import com.ss.json.utils.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleSecretJsonAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SimpleSecretJacksonAutoConfiguration.class,
                    JacksonAutoConfiguration.class,
                    SimpleSecretJsonCodecAutoConfiguration.class));

    @Test
    void createsCodecWithoutCustomizingHostMapperByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(JsonCodec.class);
            assertThat(context).doesNotHaveBean("simpleSecretJacksonCustomizer");
            JsonCodec codec = context.getBean(JsonCodec.class);
            assertThat(codec.toJsonString(new BigInteger("9007199254740992")))
                    .isEqualTo("9007199254740992");
        });
    }

    @Test
    void appliesDateTimeAndDeserializationPolicies() {
        runner.withPropertyValues("simple-secret.json.jackson-customization-enabled=true").run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            JsonCodec codec = context.getBean(JsonCodec.class);
            LocalDateTime value = LocalDateTime.of(2026, 7, 27, 14, 5, 9);

            assertThat(codec.toJsonString(value)).isEqualTo("\"2026-07-27T14:05:09\"");
            assertThat(codec.parseObject("\"2026-07-27T14:05:09\"", LocalDateTime.class))
                    .isEqualTo(value);
            assertThat(codec.parseObject("{\"name\":\"Ada\",\"unknown\":true}", Person.class))
                    .isEqualTo(new Person("Ada"));
            assertThat(codec.toJsonString(new BigInteger("9007199254740992")))
                    .isEqualTo("\"9007199254740992\"");
            assertThat(mapper.getSerializationConfig().getTimeZone())
                    .isEqualTo(TimeZone.getDefault());
            assertThat(mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
                    .isFalse();
        });
    }

    @Test
    void canBeDisabled() {
        runner.withPropertyValues("simple-secret.json.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(JsonCodec.class);
                    assertThat(context).doesNotHaveBean("simpleSecretJacksonCustomizer");
                });
    }

    @Test
    void doesNotCreateCodecWhenObjectMapperCandidateIsAmbiguous() {
        runner.withUserConfiguration(TwoMapperConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(JsonCodec.class);
                });
    }

    @Test
    void usesPrimaryObjectMapperWhenMultipleCandidatesExist() {
        runner.withUserConfiguration(PrimaryMapperConfiguration.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(JsonCodec.class);
                    assertThat(context.getBean(JsonCodec.class).getObjectMapper())
                            .isSameAs(context.getBean("primaryMapper", ObjectMapper.class));
                });
    }

    @Test
    void createsStandaloneCodecWhenSpringWebBuilderIsUnavailable() {
        runner.withClassLoader(new FilteredClassLoader(Jackson2ObjectMapperBuilder.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(ObjectMapper.class);
                    assertThat(context).hasSingleBean(JsonCodec.class);
                    assertThat(context.getBean(JsonCodec.class)
                            .toJsonString(new BigInteger("9007199254740992")))
                            .isEqualTo("\"9007199254740992\"");
                });
    }

    @Test
    void usesObjectMapperCreatedByLaterAutoConfiguration() {
        runner.withClassLoader(new FilteredClassLoader(Jackson2ObjectMapperBuilder.class))
                .withConfiguration(AutoConfigurations.of(LateObjectMapperAutoConfiguration.class))
                .run(context -> {
                    ObjectMapper mapper = context.getBean("lateObjectMapper", ObjectMapper.class);
                    assertThat(context).hasSingleBean(JsonCodec.class);
                    assertThat(context.getBean(JsonCodec.class).getObjectMapper()).isSameAs(mapper);
                });
    }

    @Test
    void hostCustomizerWinsWhenCustomizationIsExplicitlyEnabled() {
        runner.withPropertyValues("simple-secret.json.jackson-customization-enabled=true")
                .withUserConfiguration(UserCustomizerConfiguration.class)
                .run(context -> {
                    ObjectMapper mapper = context.getBean(ObjectMapper.class);
                    assertThat(mapper.getSerializationConfig().getTimeZone())
                            .isEqualTo(TimeZone.getTimeZone("UTC"));
                    assertThat(mapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)).isTrue();
                });
    }

    @Test
    void backsOffForUserCodec() {
        JsonCodec custom = new JsonCodec(new ObjectMapper());
        runner.withBean(JsonCodec.class, () -> custom).run(context -> {
            assertThat(context).hasSingleBean(JsonCodec.class);
            assertThat(context.getBean(JsonCodec.class)).isSameAs(custom);
        });
    }

    @Test
    void preservesUserJacksonModules() {
        runner.withPropertyValues("simple-secret.json.jackson-customization-enabled=true")
                .withUserConfiguration(UserModuleConfiguration.class).run(context -> {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            assertThat(mapper.writeValueAsString(new Marker("plain"))).isEqualTo("\"custom\"");
            assertThat(context.getBean(JsonCodec.class).getObjectMapper()).isSameAs(mapper);
            assertThat(JsonUtils.toJsonString(new Marker("plain")))
                    .isEqualTo("{\"value\":\"plain\"}");
                });
    }

    @Test
    void hostModuleCanOverrideSimpleSecretNumberSerializer() {
        runner.withPropertyValues("simple-secret.json.jackson-customization-enabled=true")
                .withUserConfiguration(UserNumberModuleConfiguration.class)
                .run(context -> assertThat(context.getBean(ObjectMapper.class)
                        .writeValueAsString(new BigInteger("9007199254740992")))
                        .isEqualTo("\"host-number\""));
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoMapperConfiguration {
        @Bean
        ObjectMapper firstMapper() {
            return new ObjectMapper();
        }

        @Bean
        ObjectMapper secondMapper() {
            return new ObjectMapper();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PrimaryMapperConfiguration {
        @Bean
        @Primary
        ObjectMapper primaryMapper() {
            return new ObjectMapper();
        }

        @Bean
        ObjectMapper secondaryMapper() {
            return new ObjectMapper();
        }
    }

    @AutoConfiguration
    @AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE - 1)
    static class LateObjectMapperAutoConfiguration {
        @Bean
        ObjectMapper lateObjectMapper() {
            return new ObjectMapper();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserCustomizerConfiguration {
        @Bean
        @Order(Ordered.LOWEST_PRECEDENCE)
        Jackson2ObjectMapperBuilderCustomizer hostJacksonCustomizer() {
            return builder -> {
                builder.timeZone(TimeZone.getTimeZone("UTC"));
                builder.featuresToEnable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserModuleConfiguration {
        @Bean
        Module markerModule() {
            SimpleModule module = new SimpleModule("marker", Version.unknownVersion());
            module.addSerializer(Marker.class, new JsonSerializer<>() {
                @Override
                public void serialize(Marker value, com.fasterxml.jackson.core.JsonGenerator generator,
                                      SerializerProvider serializers) throws IOException {
                    generator.writeString("custom");
                }
            });
            return module;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserNumberModuleConfiguration {
        @Bean
        Module numberModule() {
            SimpleModule module = new SimpleModule("host-number", Version.unknownVersion());
            module.addSerializer(BigInteger.class, new JsonSerializer<>() {
                @Override
                public void serialize(BigInteger value, com.fasterxml.jackson.core.JsonGenerator generator,
                                      SerializerProvider serializers) throws IOException {
                    generator.writeString("host-number");
                }
            });
            return module;
        }
    }

    record Marker(String value) { }

    record Person(String name) { }
}
