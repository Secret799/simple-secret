package com.ss.netty.server;

import com.ss.netty.auth.NettyWebSocketAuthenticator;
import com.ss.netty.auth.NettyWebSocketHandshakeRequest;
import com.ss.netty.auth.NettyWebSocketPrincipal;
import com.ss.netty.handler.NettyWebSocketMessageHandler;
import com.ss.netty.message.NettyWebSocketMessage;
import com.ss.netty.session.NettyWebSocketChannelRegistry;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketCloseStatus;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.ScheduledFuture;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** 每条 TCP 连接独享的 WebSocket 握手和 frame 处理器。 */
final class NettyWebSocketFrameHandler extends SimpleChannelInboundHandler<Object> {

    static final AttributeKey<String> PATH_ATTRIBUTE =
            AttributeKey.valueOf(NettyWebSocketFrameHandler.class, "path");
    static final AttributeKey<NettyWebSocketPrincipal> PRINCIPAL_ATTRIBUTE =
            AttributeKey.valueOf(NettyWebSocketFrameHandler.class, "principal");

    private static final System.Logger LOGGER =
            System.getLogger(NettyWebSocketFrameHandler.class.getName());

    private final NettyWebSocketEndpointRegistry endpoints;
    private final NettyWebSocketAuthenticator authenticator;
    private final Executor handlerExecutor;
    private final NettyWebSocketChannelRegistry channels;
    private final int maxFramePayloadLength;
    private final Duration handshakeTimeout;
    private final Semaphore handlerCapacity;
    private final ArrayDeque<HandlerInvocation> pendingMessages = new ArrayDeque<>();
    private WebSocketServerHandshaker handshaker;
    private ScheduledFuture<?> handshakeDeadline;
    private boolean authenticationPending;
    private boolean handlerRunning;
    private boolean handlerClosed;

    NettyWebSocketFrameHandler(NettyWebSocketEndpointRegistry endpoints,
                               NettyWebSocketAuthenticator authenticator,
                               Executor handlerExecutor,
                               NettyWebSocketChannelRegistry channels,
                               int maxFramePayloadLength,
                               Duration handshakeTimeout) {
        this(endpoints, authenticator, handlerExecutor, channels,
                maxFramePayloadLength, handshakeTimeout,
                new Semaphore(Integer.MAX_VALUE));
    }

    NettyWebSocketFrameHandler(NettyWebSocketEndpointRegistry endpoints,
                               NettyWebSocketAuthenticator authenticator,
                               Executor handlerExecutor,
                               NettyWebSocketChannelRegistry channels,
                               int maxFramePayloadLength,
                               Duration handshakeTimeout,
                               Semaphore handlerCapacity) {
        this.endpoints = Objects.requireNonNull(endpoints, "endpoints must not be null");
        this.authenticator = authenticator;
        this.handlerExecutor = Objects.requireNonNull(
                handlerExecutor, "handlerExecutor must not be null");
        this.channels = Objects.requireNonNull(channels, "channels must not be null");
        if (maxFramePayloadLength <= 0) {
            throw new IllegalArgumentException("maxFramePayloadLength must be positive");
        }
        this.maxFramePayloadLength = maxFramePayloadLength;
        if (handshakeTimeout == null || handshakeTimeout.isZero() || handshakeTimeout.isNegative()) {
            throw new IllegalArgumentException("handshakeTimeout must be positive");
        }
        this.handshakeTimeout = handshakeTimeout;
        this.handlerCapacity = Objects.requireNonNull(
                handlerCapacity, "handlerCapacity must not be null");
    }

    @Override
    public void channelActive(ChannelHandlerContext context) throws Exception {
        handshakeDeadline = context.executor().schedule(() -> {
            context.close();
        }, handshakeTimeout.toMillis(), TimeUnit.MILLISECONDS);
        super.channelActive(context);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, Object message) {
        if (message instanceof FullHttpRequest request) {
            handleHttpRequest(context, request);
            return;
        }
        if (message instanceof WebSocketFrame frame) {
            handleFrame(context, frame);
        }
    }

    private void handleHttpRequest(ChannelHandlerContext context, FullHttpRequest request) {
        if (handshaker != null || authenticationPending || !request.decoderResult().isSuccess()
                || !HttpMethod.GET.equals(request.method()) || !isUpgrade(request)) {
            sendHttpResponse(context, HttpResponseStatus.BAD_REQUEST);
            return;
        }
        NettyWebSocketHandshakeRequest snapshot = NettyWebSocketRequestSnapshots.from(
                request, context.channel().remoteAddress());
        Optional<NettyWebSocketEndpoint> endpointResult = endpoints.endpoint(snapshot.path());
        if (endpointResult.isEmpty()) {
            sendHttpResponse(context, HttpResponseStatus.NOT_FOUND);
            return;
        }
        if (!endpoints.isOriginAllowed(snapshot.path(),
                request.headers().get(HttpHeaderNames.ORIGIN),
                request.headers().get(HttpHeaderNames.HOST))) {
            sendHttpResponse(context, HttpResponseStatus.FORBIDDEN);
            return;
        }

        NettyWebSocketEndpoint endpoint = endpointResult.orElseThrow();
        if (endpoint.authenticationRequired()) {
            authenticateAsync(context, request, endpoint, snapshot);
            return;
        }
        beginHandshake(context, request, endpoint.path(), null);
    }

    private void authenticateAsync(ChannelHandlerContext context,
                                   FullHttpRequest request,
                                   NettyWebSocketEndpoint endpoint,
                                   NettyWebSocketHandshakeRequest snapshot) {
        FullHttpRequest handshakeRequest = copyHandshakeRequest(request);
        authenticationPending = true;
        try {
            handlerExecutor.execute(() -> {
                NettyWebSocketPrincipal principal = authenticate(endpoint, snapshot);
                try {
                    context.executor().execute(() -> completeAuthentication(
                            context, handshakeRequest, endpoint, principal));
                } catch (RejectedExecutionException rejected) {
                    handshakeRequest.release();
                }
            });
        } catch (RejectedExecutionException rejected) {
            authenticationPending = false;
            handshakeRequest.release();
            sendHttpResponse(context, HttpResponseStatus.SERVICE_UNAVAILABLE);
        }
    }

    private void completeAuthentication(ChannelHandlerContext context,
                                        FullHttpRequest request,
                                        NettyWebSocketEndpoint endpoint,
                                        NettyWebSocketPrincipal principal) {
        try {
            authenticationPending = false;
            if (!context.channel().isActive() || handshaker != null) {
                return;
            }
            if (principal == null) {
                sendHttpResponse(context, HttpResponseStatus.UNAUTHORIZED);
                return;
            }
            beginHandshake(context, request, endpoint.path(), principal);
        } finally {
            request.release();
        }
    }

    private NettyWebSocketPrincipal authenticate(NettyWebSocketEndpoint endpoint,
                                                 NettyWebSocketHandshakeRequest request) {
        if (!endpoint.authenticationRequired()) {
            return null;
        }
        if (authenticator == null) {
            return null;
        }
        try {
            Optional<NettyWebSocketPrincipal> result = authenticator.authenticate(request);
            return result == null ? null : result.orElse(null);
        } catch (RuntimeException ignored) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Netty WebSocket authentication rejected for path {0}", endpoint.path());
            return null;
        }
    }

    private void beginHandshake(ChannelHandlerContext context, FullHttpRequest request,
                                String path, NettyWebSocketPrincipal principal) {
        String host = request.headers().get(HttpHeaderNames.HOST);
        if (host == null || host.isBlank()) {
            sendHttpResponse(context, HttpResponseStatus.BAD_REQUEST);
            return;
        }
        WebSocketServerHandshakerFactory factory = new WebSocketServerHandshakerFactory(
                "ws://" + host.trim() + path, null, false, maxFramePayloadLength);
        handshaker = factory.newHandshaker(request);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(context.channel())
                    .addListener(ChannelFutureListener.CLOSE);
            return;
        }

        context.channel().attr(PATH_ATTRIBUTE).set(path);
        context.channel().attr(PRINCIPAL_ATTRIBUTE).set(principal);
        handshaker.handshake(context.channel(), request).addListener(future -> {
            if (future.isSuccess()) {
                cancelHandshakeDeadline();
                channels.register(path, principal, context.channel());
                return;
            }
            context.channel().attr(PATH_ATTRIBUTE).set(null);
            context.channel().attr(PRINCIPAL_ATTRIBUTE).set(null);
            context.close();
        });
    }

    private void handleFrame(ChannelHandlerContext context, WebSocketFrame frame) {
        if (frame instanceof CloseWebSocketFrame closeFrame) {
            if (handshaker == null) {
                context.close();
            } else {
                handshaker.close(context, closeFrame.retain());
            }
            return;
        }
        if (frame instanceof PingWebSocketFrame pingFrame) {
            context.writeAndFlush(new PongWebSocketFrame(pingFrame.content().retain()));
            return;
        }
        if (frame instanceof PongWebSocketFrame) {
            return;
        }
        if (frame instanceof BinaryWebSocketFrame) {
            closeWithStatus(context, WebSocketCloseStatus.INVALID_MESSAGE_TYPE);
            return;
        }
        if (!(frame instanceof TextWebSocketFrame textFrame)) {
            closeWithStatus(context, WebSocketCloseStatus.INVALID_MESSAGE_TYPE);
            return;
        }

        String path = context.channel().attr(PATH_ATTRIBUTE).get();
        if (path == null) {
            closeWithStatus(context, WebSocketCloseStatus.PROTOCOL_ERROR);
            return;
        }
        Optional<NettyWebSocketMessageHandler> handler = endpoints.handler(path);
        if (handler.isEmpty()) {
            closeWithStatus(context, WebSocketCloseStatus.POLICY_VIOLATION);
            return;
        }
        NettyWebSocketMessage message = new NettyWebSocketMessage(
                path, context.channel().id().asLongText(),
                context.channel().attr(PRINCIPAL_ATTRIBUTE).get(), textFrame.text());
        dispatchMessage(context, handler.orElseThrow(), message);
    }

    private void dispatchMessage(ChannelHandlerContext context,
                                 NettyWebSocketMessageHandler handler,
                                 NettyWebSocketMessage message) {
        if (!handlerCapacity.tryAcquire()) {
            rejectPendingMessages(context);
            return;
        }
        boolean submit;
        synchronized (pendingMessages) {
            if (handlerClosed) {
                handlerCapacity.release();
                return;
            }
            pendingMessages.addLast(new HandlerInvocation(context, handler, message));
            submit = !handlerRunning;
            if (submit) {
                handlerRunning = true;
            }
        }
        if (submit) {
            submitNextMessage();
        }
    }

    private void submitNextMessage() {
        HandlerInvocation invocation;
        synchronized (pendingMessages) {
            if (handlerClosed) {
                handlerRunning = false;
                return;
            }
            invocation = pendingMessages.pollFirst();
            if (invocation == null) {
                handlerRunning = false;
                return;
            }
        }
        try {
            handlerExecutor.execute(() -> {
                try {
                    invokeHandler(invocation.handler(), invocation.message());
                } finally {
                    handlerCapacity.release();
                    submitNextMessage();
                }
            });
        } catch (RejectedExecutionException rejected) {
            handlerCapacity.release();
            rejectPendingMessages(invocation.context());
        }
    }

    private void rejectPendingMessages(ChannelHandlerContext context) {
        int discarded;
        synchronized (pendingMessages) {
            handlerClosed = true;
            handlerRunning = false;
            discarded = pendingMessages.size();
            pendingMessages.clear();
        }
        if (discarded > 0) {
            handlerCapacity.release(discarded);
        }
        Runnable close = () -> closeWithStatus(
                context, WebSocketCloseStatus.TRY_AGAIN_LATER);
        if (context.executor().inEventLoop()) {
            close.run();
            return;
        }
        try {
            context.executor().execute(close);
        } catch (RejectedExecutionException ignored) {
            context.close();
        }
    }

    private void discardPendingMessages() {
        int discarded;
        synchronized (pendingMessages) {
            handlerClosed = true;
            discarded = pendingMessages.size();
            pendingMessages.clear();
        }
        if (discarded > 0) {
            handlerCapacity.release(discarded);
        }
    }

    private static FullHttpRequest copyHandshakeRequest(FullHttpRequest request) {
        FullHttpRequest copy = new DefaultFullHttpRequest(
                request.protocolVersion(), request.method(), request.uri(),
                Unpooled.EMPTY_BUFFER, request.headers().copy(), EmptyHttpHeaders.INSTANCE);
        copy.setDecoderResult(request.decoderResult());
        return copy;
    }

    private static void invokeHandler(NettyWebSocketMessageHandler handler,
                                      NettyWebSocketMessage message) {
        try {
            handler.handle(message);
        } catch (RuntimeException failure) {
            LOGGER.log(System.Logger.Level.ERROR,
                    "Netty WebSocket message handler failed for path {0}, session {1}: {2}",
                    message.path(), message.sessionId(), failure.getClass().getName());
        }
    }

    private void closeWithStatus(ChannelHandlerContext context, WebSocketCloseStatus status) {
        if (handshaker == null) {
            context.close();
            return;
        }
        handshaker.close(context.channel(), new CloseWebSocketFrame(status));
    }

    private static boolean isUpgrade(FullHttpRequest request) {
        return request.headers().contains(HttpHeaderNames.UPGRADE,
                HttpHeaderValues.WEBSOCKET, true)
                && request.headers().containsValue(HttpHeaderNames.CONNECTION,
                HttpHeaderValues.UPGRADE, true);
    }

    private static void sendHttpResponse(ChannelHandlerContext context,
                                         HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer(status.toString(), CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        HttpUtil.setContentLength(response, response.content().readableBytes());
        context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        cancelHandshakeDeadline();
        discardPendingMessages();
        String path = context.channel().attr(PATH_ATTRIBUTE).get();
        if (path != null) {
            channels.remove(path, context.channel().attr(PRINCIPAL_ATTRIBUTE).get(), context.channel());
        }
        super.channelInactive(context);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        LOGGER.log(System.Logger.Level.ERROR,
                "Netty WebSocket channel failed: {0}", cause.getClass().getName());
        context.close();
    }

    private void cancelHandshakeDeadline() {
        ScheduledFuture<?> deadline = handshakeDeadline;
        handshakeDeadline = null;
        if (deadline != null) {
            deadline.cancel(false);
        }
    }

    private record HandlerInvocation(ChannelHandlerContext context,
                                     NettyWebSocketMessageHandler handler,
                                     NettyWebSocketMessage message) {
    }
}
