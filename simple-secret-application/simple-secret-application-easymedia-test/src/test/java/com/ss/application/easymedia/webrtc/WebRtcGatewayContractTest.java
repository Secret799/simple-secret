package com.ss.application.easymedia.webrtc;

import com.ss.easymedia.webrtc.domain.WebRtcMediaTypes;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 需要运行中的测试应用、可用的 mk_api、已发布媒体流和真实浏览器 Offer SDP。
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "SIMPLE_SECRET_EASYMEDIA_IT", matches = "true")
class WebRtcGatewayContractTest {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Test
    void createsLocalWhepAnswerWithoutManagedSessionLocation() throws Exception {
        URI baseUri = URI.create(requiredEnv("SIMPLE_SECRET_EASYMEDIA_IT_BASE_URL"));
        String app = requiredEnv("SIMPLE_SECRET_EASYMEDIA_IT_APP");
        String stream = requiredEnv("SIMPLE_SECRET_EASYMEDIA_IT_STREAM");
        byte[] offer = Files.readAllBytes(
                Path.of(requiredEnv("SIMPLE_SECRET_EASYMEDIA_IT_OFFER_SDP")));
        URI createUri = baseUri.resolve("/easyMedia/api/webrtc/whep?app="
                + encode(app) + "&stream=" + encode(stream));

        HttpRequest request = HttpRequest.newBuilder(createUri)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", WebRtcMediaTypes.APPLICATION_SDP_VALUE)
                .header("Accept", WebRtcMediaTypes.APPLICATION_SDP_VALUE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(offer))
                .build();
        HttpResponse<byte[]> response = client.send(
                request, HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(201, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type")
                .orElse("").startsWith(WebRtcMediaTypes.APPLICATION_SDP_VALUE));
        assertFalse(response.headers().firstValue("Location").isPresent());
        assertTrue(response.body().length > 0);
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + name);
        }
        return value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
