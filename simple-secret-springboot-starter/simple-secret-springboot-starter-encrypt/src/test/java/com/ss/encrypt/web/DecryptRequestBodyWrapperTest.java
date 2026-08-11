package com.ss.encrypt.web;

import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DecryptRequestBodyWrapperTest {

    @Test
    void shouldExposeUtf8JsonBodyAndCorrectStreamState() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("application/octet-stream");
        DecryptRequestBodyWrapper wrapper = new DecryptRequestBodyWrapper(
                request, "{\"name\":\"中文\"}".getBytes(StandardCharsets.UTF_8));

        ServletInputStream input = wrapper.getInputStream();

        assertThat(wrapper.getContentType()).isEqualTo("application/json");
        assertThat(wrapper.getCharacterEncoding()).isEqualTo("UTF-8");
        assertThat(input.isReady()).isTrue();
        assertThat(input.isFinished()).isFalse();
        assertThat(input.readAllBytes()).isEqualTo(wrapper.getContentAsByteArray());
        assertThat(input.isFinished()).isTrue();
        assertThat(wrapper.getContentLengthLong())
                .isEqualTo(wrapper.getContentAsByteArray().length);
    }
}
