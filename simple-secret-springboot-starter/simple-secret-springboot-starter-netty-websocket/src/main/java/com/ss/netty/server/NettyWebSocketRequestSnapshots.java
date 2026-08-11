package com.ss.netty.server;

import com.ss.netty.auth.NettyWebSocketHandshakeRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 将引用计数的 Netty HTTP 请求复制成应用可安全持有的不可变快照。 */
final class NettyWebSocketRequestSnapshots {

    private NettyWebSocketRequestSnapshots() {
    }

    /** 在 Netty 释放请求前复制握手数据。 */
    static NettyWebSocketHandshakeRequest from(FullHttpRequest request,
                                                SocketAddress remoteAddress) {
        QueryStringDecoder query = new QueryStringDecoder(request.uri());
        return new NettyWebSocketHandshakeRequest(
                request.method().name(), request.uri(), query.path(),
                copyHeaders(request), copyQuery(query), copyCookies(request), remoteAddress);
    }

    private static Map<String, List<String>> copyHeaders(FullHttpRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        request.headers().names().forEach(name -> headers.put(
                name.toLowerCase(Locale.ROOT), List.copyOf(request.headers().getAll(name))));
        return headers;
    }

    private static Map<String, List<String>> copyQuery(QueryStringDecoder decoder) {
        Map<String, List<String>> query = new LinkedHashMap<>();
        decoder.parameters().forEach((name, values) ->
                query.put(name, List.copyOf(new ArrayList<>(values))));
        return query;
    }

    private static Map<String, String> copyCookies(FullHttpRequest request) {
        Map<String, String> cookies = new LinkedHashMap<>();
        for (String header : request.headers().getAll(HttpHeaderNames.COOKIE)) {
            Set<Cookie> decoded = ServerCookieDecoder.STRICT.decode(header);
            decoded.forEach(cookie -> cookies.putIfAbsent(cookie.name(), cookie.value()));
        }
        return cookies;
    }
}
