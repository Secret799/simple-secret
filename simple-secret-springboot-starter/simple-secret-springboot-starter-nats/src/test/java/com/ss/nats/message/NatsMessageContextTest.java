package com.ss.nats.message;

import io.nats.client.Message;
import io.nats.client.impl.Headers;
import io.nats.client.impl.NatsMessage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class NatsMessageContextTest {

    @Test
    void shouldSnapshotPayloadHeadersAndRoutingMetadata() {
        byte[] payload = "hello".getBytes(StandardCharsets.UTF_8);
        Headers headers = new Headers().put("trace-id", "abc");
        Message source = NatsMessage.builder()
                .subject("devices.edge.state")
                .replyTo("_INBOX.reply")
                .headers(headers)
                .data(payload)
                .build();

        NatsMessageContext context = new NatsMessageContext("edge", source);
        payload[0] = 'X';
        headers.put("trace-id", "changed");
        byte[] returned = context.getPayload();
        returned[0] = 'Y';
        Headers returnedHeaders = context.getHeaders();
        returnedHeaders.put("trace-id", "returned");

        assertThat(context.getClientKey()).isEqualTo("edge");
        assertThat(context.getSubject()).isEqualTo("devices.edge.state");
        assertThat(context.getReplyTo()).isEqualTo("_INBOX.reply");
        assertThat(context.getPayloadAsString()).isEqualTo("hello");
        assertThat(context.getHeaders().getFirst("trace-id")).isEqualTo("abc");
    }

    @Test
    void shouldDecodeUsingCallerProvidedFunctions() {
        Message source = NatsMessage.builder().subject("numbers").data("42").build();
        NatsMessageContext context = new NatsMessageContext("default", source);

        int value = context.decodeText(Integer::parseInt);
        String hex = context.decode(bytes -> Integer.toHexString(
                Integer.parseInt(new String(bytes, StandardCharsets.UTF_8))));

        assertThat(value).isEqualTo(42);
        assertThat(hex).isEqualTo("2a");
    }
}
