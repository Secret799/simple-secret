package com.ss.nats.handler;

import com.ss.nats.message.NatsMessageContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NatsMessageHandlerTest {

    @Test
    void defaultsShouldUseOrdinaryAsyncSubscriptionOnDefaultClient() {
        NatsMessageHandler handler = new NatsMessageHandler() {
            @Override public String subject() { return "events.>"; }
            @Override public void handle(NatsMessageContext message) { }
        };
        NatsMessageValidator validator = message -> false;

        assertThat(handler.clientKey()).isEqualTo("default");
        assertThat(handler.queue()).isEmpty();
        assertThat(handler.ordered()).isFalse();
        assertThat(validator.validate(null)).isFalse();
    }
}
