package com.ss.consumer.json;

import com.ss.json.JsonCodec;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the published JSON starter from a third-party application classpath. */
class JsonStarterConsumerTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConsumerApplication.class);

    @Test
    void discoversAutoConfigurationAndCreatesJsonCodec() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(JsonCodec.class);
            assertThat(context.getBean(JsonCodec.class).toJsonString(new Message("ready")))
                    .isEqualTo("{\"value\":\"ready\"}");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class ConsumerApplication {
    }

    record Message(String value) {
    }
}
