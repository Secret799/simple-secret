package com.ss.encrypt.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptResponseBodyWrapperTest {

    @Test
    void shouldCaptureWriterContentWithoutCommittingUnderlyingResponse()
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        EncryptResponseBodyWrapper wrapper =
                new EncryptResponseBodyWrapper(response, 64);

        wrapper.getWriter().write("中文-body");

        assertThat(wrapper.bodyAsString()).isEqualTo("中文-body");
        assertThat(response.getContentAsByteArray()).isEmpty();
        assertThatThrownBy(wrapper::getOutputStream)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectResponsesBeyondConfiguredLimit() throws Exception {
        EncryptResponseBodyWrapper wrapper = new EncryptResponseBodyWrapper(
                new MockHttpServletResponse(), 4);

        assertThatThrownBy(() -> wrapper.getOutputStream()
                        .write("12345".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ApiPayloadTooLargeException.class);
    }
}
