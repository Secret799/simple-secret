package com.ss.sensitive.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ss.sensitive.annotation.Sensitive;
import com.ss.sensitive.core.SensitiveService;
import com.ss.sensitive.core.SensitiveStrategy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 Jackson module 不依赖静态容器且不会在字段或线程间串用策略。 */
class SimpleSecretSensitiveModuleTest {

    @Test
    void shouldMaskAnnotatedStringFieldsByDefault() throws Exception {
        ObjectMapper mapper = mapper(SensitiveService.alwaysMask());

        JsonNode json = mapper.readTree(mapper.writeValueAsString(sample()));

        assertThat(json.get("phone").asText()).isEqualTo("180****1999");
        assertThat(json.get("email").asText()).isEqualTo("d*************@gmail.com.cn");
        assertThat(json.get("name").asText()).isEqualTo("Alice");
        assertThat(json.get("sequence").asInt()).isEqualTo(42);
    }

    @Test
    void shouldPassAnnotationHintsAndAllowExplicitPlainTextDecision() throws Exception {
        List<String> decisions = new CopyOnWriteArrayList<>();
        SensitiveService service = (roleKey, perms) -> {
            decisions.add(roleKey + ":" + perms);
            return false;
        };
        ObjectMapper mapper = mapper(service);

        JsonNode json = mapper.readTree(mapper.writeValueAsString(sample()));

        assertThat(json.get("phone").asText()).isEqualTo("18049531999");
        assertThat(decisions).contains("auditor:customer:read:raw");
    }

    @Test
    void shouldSerializeConcurrentlyWithoutSharingFieldStrategyState() throws Exception {
        ObjectMapper mapper = mapper(SensitiveService.alwaysMask());
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<JsonNode>> tasks = new ArrayList<>();
            for (int index = 0; index < 100; index++) {
                tasks.add(() -> mapper.readTree(mapper.writeValueAsString(sample())));
            }

            List<Future<JsonNode>> results = executor.invokeAll(tasks);

            for (Future<JsonNode> result : results) {
                assertThat(result.get().get("phone").asText()).isEqualTo("180****1999");
                assertThat(result.get().get("email").asText())
                        .isEqualTo("d*************@gmail.com.cn");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldMaskWhenConsumerDecisionFails() throws Exception {
        ObjectMapper mapper = mapper((roleKey, perms) -> {
            throw new IllegalStateException("policy unavailable");
        });

        JsonNode json = mapper.readTree(mapper.writeValueAsString(sample()));

        assertThat(json.get("phone").asText()).isEqualTo("180****1999");
    }

    private static ObjectMapper mapper(SensitiveService service) {
        return new ObjectMapper().registerModule(new SimpleSecretSensitiveModule(service));
    }

    private static Sample sample() {
        Sample sample = new Sample();
        sample.phone = "18049531999";
        sample.email = "duandazhi-jack@gmail.com.cn";
        sample.name = "Alice";
        sample.sequence = 42;
        return sample;
    }

    private static final class Sample {
        @Sensitive(
                strategy = SensitiveStrategy.PHONE,
                roleKey = "auditor",
                perms = "customer:read:raw")
        public String phone;

        @Sensitive(strategy = SensitiveStrategy.EMAIL)
        public String email;

        public String name;

        @Sensitive(strategy = SensitiveStrategy.ID_CARD)
        public int sequence;
    }
}
