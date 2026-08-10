package com.ss.easymedia.webrtc.client;

import com.ss.easymedia.webrtc.domain.WebRtcSessionType;
import com.ss.easymedia.webrtc.domain.ZlmWebRtcResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 基于 Spring RestClient 的 ZLM 信令客户端。
 */
public class RestClientZlmWebRtcSignalingClient implements ZlmWebRtcSignalingClient {

    /** 允许从客户端转发给上游的请求头白名单。 */
    private static final Set<String> REQUEST_HEADERS = lowerCaseSet(
            HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT, HttpHeaders.IF_MATCH);

    /** 允许从上游返回给客户端的响应头白名单。 */
    private static final Set<String> RESPONSE_HEADERS = lowerCaseSet(
            HttpHeaders.CONTENT_TYPE, HttpHeaders.LOCATION, HttpHeaders.ETAG,
            "Accept-Patch", HttpHeaders.LINK, HttpHeaders.CACHE_CONTROL);

    /** 执行外置 ZLM HTTP 调用的客户端。 */
    private final RestClient restClient;
    /** 已验证的上游信令服务基地址。 */
    private final URI signalingBaseUri;
    /** 校验上游会话 Location 的安全策略。 */
    private final ZlmWebRtcUriPolicy uriPolicy;

    /** 创建 HTTP 转发信令客户端。 */
    public RestClientZlmWebRtcSignalingClient(RestClient restClient, URI signalingBaseUri,
                                              ZlmWebRtcUriPolicy uriPolicy) {
        this.restClient = restClient;
        this.signalingBaseUri = signalingBaseUri;
        this.uriPolicy = uriPolicy;
    }

    /** @return 上游创建会话后的原始信令响应。 */
    @Override
    public ZlmWebRtcResponse create(WebRtcSessionType type, String app, String stream,
                                    HttpHeaders requestHeaders, byte[] body) {
        URI requestUri = UriComponentsBuilder.fromUri(signalingBaseUri)
                .path(type.getUpstreamPath())
                .queryParam("app", "{app}")
                .queryParam("stream", "{stream}")
                .encode()
                .buildAndExpand(app, stream)
                .toUri();
        return execute(requestUri, HttpMethod.POST, requestHeaders, body);
    }

    /** @return 向受信会话资源转发 PATCH 或 DELETE 后的响应。 */
    @Override
    public ZlmWebRtcResponse exchange(URI upstreamLocation, HttpMethod method,
                                      HttpHeaders requestHeaders, byte[] body) {
        URI trustedUri = uriPolicy.requireTrustedLocation(signalingBaseUri, upstreamLocation);
        return execute(trustedUri, method, requestHeaders, body);
    }

    /** 执行一次受请求头白名单保护的上游信令请求。 */
    private ZlmWebRtcResponse execute(URI requestUri, HttpMethod method,
                                      HttpHeaders requestHeaders, byte[] body) {
        RestClient.RequestBodySpec request = restClient.method(method)
                .uri(requestUri)
                .headers(headers -> copyAllowedHeaders(requestHeaders, headers, REQUEST_HEADERS));
        RestClient.RequestHeadersSpec<?> requestSpec = body == null || body.length == 0
                ? request : request.body(body);
        return requestSpec.exchange((clientRequest, clientResponse) -> {
            byte[] responseBody;
            try {
                responseBody = clientResponse.getBody().readAllBytes();
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read ZLM signaling response", exception);
            }
            return new ZlmWebRtcResponse(
                    clientRequest.getURI(),
                    clientResponse.getStatusCode(),
                    filterHeaders(clientResponse.getHeaders(), RESPONSE_HEADERS),
                    responseBody
            );
        });
    }

    /** 将白名单中的响应或请求头复制到目标对象。 */
    private static void copyAllowedHeaders(HttpHeaders source, HttpHeaders target, Set<String> allowed) {
        if (source == null || source.isEmpty()) {
            return;
        }
        source.forEach((name, values) -> {
            if (allowed.contains(name.toLowerCase(Locale.ROOT)) && !CollectionUtils.isEmpty(values)) {
                target.put(name, new ArrayList<>(values));
            }
        });
    }

    /** @return 仅包含白名单头的独立响应头副本。 */
    private static HttpHeaders filterHeaders(HttpHeaders source, Set<String> allowed) {
        HttpHeaders result = new HttpHeaders();
        copyAllowedHeaders(source, result, allowed);
        return result;
    }

    /** @return 用于大小写无关头名匹配的不可变集合。 */
    private static Set<String> lowerCaseSet(String... names) {
        Set<String> result = new HashSet<>();
        for (String name : names) {
            result.add(name.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(result);
    }
}
