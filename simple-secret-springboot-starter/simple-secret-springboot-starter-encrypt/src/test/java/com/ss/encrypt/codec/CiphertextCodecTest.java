package com.ss.encrypt.codec;

import com.ss.encrypt.core.CipherEncoding;
import com.ss.encrypt.core.EncryptionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CiphertextCodecTest {

    @Test
    void shouldRoundTripBase64AndHex() {
        byte[] input = new byte[] {0, 1, 15, 16, -1};

        String base64 = CiphertextCodec.encode(input, CipherEncoding.BASE64);
        String hex = CiphertextCodec.encode(input, CipherEncoding.HEX);

        assertThat(CiphertextCodec.decode(base64, CipherEncoding.BASE64))
                .containsExactly(input);
        assertThat(hex).isEqualTo("00010f10ff");
        assertThat(CiphertextCodec.decode(hex, CipherEncoding.HEX))
                .containsExactly(input);
    }

    @Test
    void shouldRejectMalformedCiphertextWithoutEchoingIt() {
        assertThatThrownBy(() -> CiphertextCodec.decode("secret-not-hex", CipherEncoding.HEX))
                .isInstanceOf(EncryptionException.class)
                .hasMessageNotContaining("secret-not-hex");
    }
}
