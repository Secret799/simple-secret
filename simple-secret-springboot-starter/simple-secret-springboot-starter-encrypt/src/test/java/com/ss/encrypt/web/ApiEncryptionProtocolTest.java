package com.ss.encrypt.web;

import com.ss.encrypt.core.EncryptionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiEncryptionProtocolTest {

    @Test
    void shouldRoundTripIndependentRequestAndResponseKeys() throws Exception {
        ApiTestCrypto.Fixture fixture = ApiTestCrypto.fixture();
        ApiEncryptionProtocol protocol = new ApiEncryptionProtocol(fixture.service());

        ApiEncryptedPayload request = protocol.encrypt("request-json", "request");
        ApiEncryptedPayload response = protocol.encrypt("response-json", "response");

        assertThat(request.keyHeader()).startsWith("v1.");
        assertThat(request.body()).startsWith("v1.");
        assertThat(request.toString())
                .doesNotContain(request.keyHeader(), request.body());
        assertThat(protocol.decrypt(request.keyHeader(), request.body(), "request"))
                .isEqualTo("request-json");
        assertThat(protocol.decrypt(response.keyHeader(), response.body(), "response"))
                .isEqualTo("response-json");
    }

    @Test
    void shouldRejectUnknownVersionAndTamperedBody() throws Exception {
        ApiEncryptionProtocol protocol = new ApiEncryptionProtocol(
                ApiTestCrypto.fixture().service());
        ApiEncryptedPayload payload = protocol.encrypt("protected", "request");
        String tampered = payload.body().substring(0, payload.body().length() - 1)
                + (payload.body().endsWith("A") ? "B" : "A");

        assertThatThrownBy(() -> protocol.decrypt(
                        "v2." + payload.keyHeader().substring(3),
                        payload.body(), "request"))
                .isInstanceOf(EncryptionException.class);
        assertThatThrownBy(() -> protocol.decrypt(
                        payload.keyHeader(), tampered, "request"))
                .isInstanceOf(EncryptionException.class)
                .hasMessageNotContaining("protected");
    }
}
