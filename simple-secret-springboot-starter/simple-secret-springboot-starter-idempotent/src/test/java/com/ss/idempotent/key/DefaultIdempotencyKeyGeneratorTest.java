package com.ss.idempotent.key;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证默认 key 稳定、隔离且不会保存敏感原文。 */
class DefaultIdempotencyKeyGeneratorTest {

    private final RequestIdentityResolver identityResolver = request -> "user-secret";
    private final DefaultIdempotencyKeyGenerator generator = new DefaultIdempotencyKeyGenerator(
            identityResolver, "ss:idempotent:");

    @Test
    void shouldGenerateStableDigestWithoutRawInputs() throws Exception {
        Method method = Sample.class.getDeclaredMethod("submit", String.class, Map.class);
        MockHttpServletRequest request = request("POST", "/orders");
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("b", 2);
        first.put("a", 1);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", 1);
        second.put("b", 2);

        String firstKey = generator.generate(method, new Object[]{"card-622202", first}, request);
        String secondKey = generator.generate(method, new Object[]{"card-622202", second}, request);

        assertThat(firstKey).isEqualTo(secondKey)
                .startsWith("ss:idempotent:")
                .hasSize("ss:idempotent:".length() + 64)
                .doesNotContain("user-secret", "card-622202", "/orders");
    }

    @Test
    void shouldSeparateMethodsRequestsIdentitiesAndBusinessArguments() throws Exception {
        Method firstMethod = Sample.class.getDeclaredMethod("submit", String.class, Map.class);
        Method secondMethod = Sample.class.getDeclaredMethod("cancel", String.class, Map.class);
        MockHttpServletRequest post = request("POST", "/orders");
        MockHttpServletRequest put = request("PUT", "/orders");
        MockHttpServletRequest query = request("POST", "/orders");
        query.setQueryString("channel=mobile");
        Object[] firstArgs = {"order-1", Map.of("amount", 10)};
        Object[] secondArgs = {"order-2", Map.of("amount", 10)};

        String baseline = generator.generate(firstMethod, firstArgs, post);

        assertThat(generator.generate(secondMethod, firstArgs, post)).isNotEqualTo(baseline);
        assertThat(generator.generate(firstMethod, firstArgs, put)).isNotEqualTo(baseline);
        assertThat(generator.generate(firstMethod, firstArgs, query)).isNotEqualTo(baseline);
        assertThat(generator.generate(firstMethod, secondArgs, post)).isNotEqualTo(baseline);

        DefaultIdempotencyKeyGenerator otherIdentity = new DefaultIdempotencyKeyGenerator(
                request -> "another-user", "ss:idempotent:");
        assertThat(otherIdentity.generate(firstMethod, firstArgs, post)).isNotEqualTo(baseline);
    }

    @Test
    void shouldIgnoreMultipartValuesInsideContainers() throws Exception {
        Method method = Sample.class.getDeclaredMethod("upload", Object.class);
        MockMultipartFile file = new MockMultipartFile(
                "file", "secret.txt", "text/plain", "top-secret".getBytes());
        Object[] first = {List.of("business", file)};
        Object[] second = {List.of("business", new MockMultipartFile(
                "file", "other.txt", "text/plain", "different".getBytes()))};

        assertThat(generator.generate(method, first, request("POST", "/upload")))
                .isEqualTo(generator.generate(method, second, request("POST", "/upload")));
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr("192.0.2.30");
        return request;
    }

    private static class Sample {

        void submit(String id, Map<String, Object> body) {
        }

        void cancel(String id, Map<String, Object> body) {
        }

        void upload(Object value) {
        }
    }
}
