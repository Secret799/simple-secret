package com.ss.redis.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyPrefixMapperTest {

    @Test
    void passesNamesThroughWhenPrefixIsBlank() {
        KeyPrefixMapper mapper = new KeyPrefixMapper("  ");

        assertThat(mapper.map("orders")).isEqualTo("orders");
        assertThat(mapper.unmap("orders")).isEqualTo("orders");
    }

    @Test
    void normalizesPrefixAndMapsOnlyOnce() {
        KeyPrefixMapper mapper = new KeyPrefixMapper(" app:: ");

        assertThat(mapper.map("orders")).isEqualTo("app:orders");
        assertThat(mapper.map("app:orders")).isEqualTo("app:orders");
    }

    @Test
    void removesOnlyItsOwnPrefix() {
        KeyPrefixMapper mapper = new KeyPrefixMapper("app");

        assertThat(mapper.unmap("app:orders")).isEqualTo("orders");
        assertThat(mapper.unmap("other:orders")).isEqualTo("other:orders");
    }

    @Test
    void rejectsNullAndBlankNames() {
        KeyPrefixMapper mapper = new KeyPrefixMapper("app");

        assertThatThrownBy(() -> mapper.map(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> mapper.map("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mapper.unmap(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> mapper.unmap("\t")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullPrefix() {
        assertThatThrownBy(() -> new KeyPrefixMapper(null))
                .isInstanceOf(NullPointerException.class);
    }
}
