package com.ss.easymedia.webrtc.client;

import com.aizuda.zlm4j.callback.IMKWebRtcGetAnwerSdpCallBack;
import com.aizuda.zlm4j.core.ZLMApi;
import com.ss.easymedia.webrtc.domain.WebRtcMediaTypes;
import com.ss.easymedia.webrtc.domain.WebRtcSessionType;
import com.ss.easymedia.webrtc.domain.ZlmWebRtcResponse;
import com.ss.zlm4j.constants.ZlmMediaServerConstants;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 通过内嵌 ZLM C API 创建 WebRTC SDP Answer 的信令客户端。
 */
public class LocalZlmWebRtcSignalingClient implements ZlmWebRtcSignalingClient {

    /** 内嵌 ZLM 的 JNA API 入口。 */
    private final ZLMApi zlmApi;
    /** 等待异步 SDP Answer 回调的最长时间。 */
    private final Duration requestTimeout;

    /**
     * 创建内嵌 ZLM 信令客户端。
     */
    public LocalZlmWebRtcSignalingClient(ZLMApi zlmApi, Duration requestTimeout) {
        this.zlmApi = zlmApi;
        this.requestTimeout = requestTimeout;
    }

    /**
     * 使用 ZLM C API 把 SDP Offer 交换为 SDP Answer。
     *
     * @return 不带受管会话资源的 SDP Answer 响应
     */
    @Override
    public ZlmWebRtcResponse create(WebRtcSessionType type, String app, String stream,
                                    HttpHeaders requestHeaders, byte[] body) {
        String rtcUrl = "rtc://" + ZlmMediaServerConstants.DEFAULT_VHOST + "/" + app + "/" + stream;
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<String> answer = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();
        IMKWebRtcGetAnwerSdpCallBack callback = (userData, nativeAnswer, nativeError) -> {
            answer.set(nativeAnswer);
            error.set(nativeError);
            completed.countDown();
        };

        zlmApi.mk_webrtc_get_answer_sdp(null, callback, nativeType(type),
                new String(body, StandardCharsets.UTF_8), rtcUrl);
        awaitResponse(completed);

        if (error.get() != null && !error.get().isBlank()) {
            throw new IllegalStateException("Embedded ZLM WebRTC signaling failed: " + error.get());
        }
        if (answer.get() == null || answer.get().isBlank()) {
            throw new IllegalStateException("Embedded ZLM returned an empty WebRTC SDP answer");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(WebRtcMediaTypes.APPLICATION_SDP);
        return new ZlmWebRtcResponse(URI.create(rtcUrl), HttpStatus.CREATED, headers,
                answer.get().getBytes(StandardCharsets.UTF_8), false);
    }

    /**
     * 内嵌 C API 没有 HTTP 会话资源，不能执行 PATCH 或 DELETE。
     */
    @Override
    public ZlmWebRtcResponse exchange(URI upstreamLocation, HttpMethod method,
                                      HttpHeaders requestHeaders, byte[] body) {
        throw new IllegalStateException("Embedded ZLM signaling does not expose managed session operations");
    }

    /**
     * 将网关会话类型映射为 ZLM C API 的插件类型。
     */
    private String nativeType(WebRtcSessionType type) {
        return switch (type) {
            case WHIP -> "push";
            case WHEP -> "play";
        };
    }

    /**
     * 等待 Native 回调并将中断和超时转换为调用失败。
     */
    private void awaitResponse(CountDownLatch completed) {
        try {
            long timeoutMillis = Math.max(1, requestTimeout.toMillis());
            if (!completed.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Embedded ZLM WebRTC signaling timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for embedded ZLM WebRTC signaling", exception);
        }
    }
}
