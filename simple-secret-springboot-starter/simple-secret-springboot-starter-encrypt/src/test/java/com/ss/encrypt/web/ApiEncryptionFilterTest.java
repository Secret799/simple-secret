package com.ss.encrypt.web;

import com.ss.encrypt.annotation.ApiEncrypt;
import com.ss.encrypt.config.EncryptProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class ApiEncryptionFilterTest {

    @Test
    void shouldDecryptRequestEncryptResponseAndPreserveStatusAndHeaders()
            throws Exception {
        ApiTestCrypto.Fixture fixture = ApiTestCrypto.fixture();
        ApiEncryptionProtocol protocol = new ApiEncryptionProtocol(fixture.service());
        ApiEncryptedPayload requestPayload = protocol.encrypt(
                "{\"id\":42}", "request");
        MockHttpServletRequest request = encryptedRequest(requestPayload);
        MockHttpServletResponse response = new MockHttpServletResponse();
        ApiEncryptionFilter filter = filter(annotation("secured"), protocol, 1024);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            HttpServletRequest decrypted = (HttpServletRequest) servletRequest;
            HttpServletResponse encryptedResponse = (HttpServletResponse) servletResponse;
            assertThat(new String(decrypted.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8)).isEqualTo("{\"id\":42}");
            encryptedResponse.setStatus(201);
            encryptedResponse.setHeader("X-Business", "kept");
            encryptedResponse.getWriter().write("{\"ok\":true}");
        });

        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getHeader("X-Business")).isEqualTo("kept");
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(response.getHeader("Access-Control-Allow-Methods")).isNull();
        assertThat(protocol.decrypt(
                response.getHeader("X-Encrypt-Key"),
                response.getContentAsString(), "response"))
                .isEqualTo("{\"ok\":true}");
    }

    @Test
    void shouldRejectMissingHeaderAndOversizedRequestWithoutCallingChain()
            throws Exception {
        ApiEncryptionProtocol protocol = new ApiEncryptionProtocol(
                ApiTestCrypto.fixture().service());
        AtomicBoolean called = new AtomicBoolean();
        FilterChain chain = (request, response) -> called.set(true);

        MockHttpServletRequest missing = new MockHttpServletRequest("POST", "/secure");
        missing.setContent("v1.body".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse missingResponse = new MockHttpServletResponse();
        filter(annotation("secured"), protocol, 1024)
                .doFilter(missing, missingResponse, chain);

        ApiEncryptedPayload payload = protocol.encrypt("large-body", "request");
        MockHttpServletResponse largeResponse = new MockHttpServletResponse();
        filter(annotation("secured"), protocol, 4)
                .doFilter(encryptedRequest(payload), largeResponse, chain);

        assertThat(missingResponse.getStatus()).isEqualTo(400);
        assertThat(largeResponse.getStatus()).isEqualTo(413);
        assertThat(called).isFalse();
    }

    @Test
    void shouldPassThroughUnannotatedRequest() throws Exception {
        ApiEncryptionProtocol protocol = new ApiEncryptionProtocol(
                ApiTestCrypto.fixture().service());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/plain");
        request.setContent("plain".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        filter(null, protocol, 1024).doFilter(request, response,
                (servletRequest, servletResponse) -> called.set(true));

        assertThat(called).isTrue();
        assertThat(response.getHeader("X-Encrypt-Key")).isNull();
    }

    private static ApiEncryptionFilter filter(
            ApiEncrypt annotation,
            ApiEncryptionProtocol protocol,
            long maxRequestBytes) {
        EncryptProperties.Api properties = new EncryptProperties.Api();
        properties.setHeaderName("X-Encrypt-Key");
        properties.setRequestKeyId("request");
        properties.setResponseKeyId("response");
        properties.setMaxRequestSize(
                org.springframework.util.unit.DataSize.ofBytes(maxRequestBytes));
        properties.setMaxResponseSize(
                org.springframework.util.unit.DataSize.ofKilobytes(1));
        return new ApiEncryptionFilter(
                request -> annotation,
                protocol,
                properties,
                new DefaultApiEncryptionFailureHandler());
    }

    private static MockHttpServletRequest encryptedRequest(
            ApiEncryptedPayload payload) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/secure");
        request.addHeader("X-Encrypt-Key", payload.keyHeader());
        request.setContent(payload.body().getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static ApiEncrypt annotation(String methodName) throws Exception {
        Method method = ApiEncryptionFilterTest.class.getDeclaredMethod(methodName);
        return method.getAnnotation(ApiEncrypt.class);
    }

    @ApiEncrypt(response = true)
    private static void secured() {
    }
}
