package com.ss.websocket.broker;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证跨节点消息的数据约束。 */
class WebSocketBrokerMessageTest {

    @Test
    void shouldDefensivelyCopyTargetKeysAndTreatEmptySetAsBroadcast() {
        Set<String> keys = new HashSet<>(Set.of(" 42 "));
        WebSocketBrokerMessage targeted =
                new WebSocketBrokerMessage("node-a", "/events", keys, "hello");
        keys.add("7");

        assertThat(targeted.sessionKeys()).containsExactly("42");
        assertThat(targeted.broadcast()).isFalse();
        assertThat(new WebSocketBrokerMessage(
                "node-a", "/events", Set.of(), "notice").broadcast()).isTrue();
    }

    @Test
    void shouldRejectInvalidRequiredFields() {
        assertThatThrownBy(() -> new WebSocketBrokerMessage(
                " ", "/events", Set.of(), "hello"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceNodeId");
        assertThatThrownBy(() -> new WebSocketBrokerMessage(
                "node-a", "events", Set.of(), "hello"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
    }
}
