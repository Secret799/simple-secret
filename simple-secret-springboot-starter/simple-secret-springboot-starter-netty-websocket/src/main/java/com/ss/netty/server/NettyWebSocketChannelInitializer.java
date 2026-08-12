package com.ss.netty.server;

import com.ss.netty.auth.NettyWebSocketAuthenticator;
import com.ss.netty.config.NettyWebSocketProperties;
import com.ss.netty.session.NettyWebSocketChannelRegistry;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

/** 为每条连接安装有界 HTTP/WebSocket 协议处理链。 */
public final class NettyWebSocketChannelInitializer extends ChannelInitializer<Channel> {

    private final NettyWebSocketProperties properties;
    private final NettyWebSocketEndpointRegistry endpoints;
    private final NettyWebSocketAuthenticator authenticator;
    private final Executor handlerExecutor;
    private final NettyWebSocketChannelRegistry channels;
    private final Semaphore handlerCapacity;

    /**
     * 创建 channel 初始化器。
     *
     * @param properties 模块配置
     * @param endpoints 端点配置集合
     * @param authenticator 握手认证器
     * @param handlerExecutor 处理器执行器
     * @param channels Netty 通道组
     */
    public NettyWebSocketChannelInitializer(NettyWebSocketProperties properties,
                                            NettyWebSocketEndpointRegistry endpoints,
                                            NettyWebSocketAuthenticator authenticator,
                                            Executor handlerExecutor,
                                            NettyWebSocketChannelRegistry channels) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.endpoints = Objects.requireNonNull(endpoints, "endpoints must not be null");
        this.authenticator = authenticator;
        this.handlerExecutor = Objects.requireNonNull(
                handlerExecutor, "handlerExecutor must not be null");
        this.channels = Objects.requireNonNull(channels, "channels must not be null");
        long totalHandlerCapacity = (long) properties.getHandlerMaxSize()
                + properties.getHandlerQueueCapacity();
        if (totalHandlerCapacity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "handler capacity must not exceed " + Integer.MAX_VALUE);
        }
        this.handlerCapacity = new Semaphore((int) totalHandlerCapacity, true);
    }

    @Override
    protected void initChannel(Channel channel) {
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast("nettyWebSocketHttpCodec", new HttpServerCodec());
        pipeline.addLast("nettyWebSocketHttpAggregator",
                new HttpObjectAggregator(properties.getMaxHttpContentLength()));
        pipeline.addLast("nettyWebSocketFrameAggregator",
                new WebSocketFrameAggregator(properties.getMaxFramePayloadLength()));
        pipeline.addLast("nettyWebSocketFrameHandler", new NettyWebSocketFrameHandler(
                endpoints, authenticator, handlerExecutor, channels,
                properties.getMaxFramePayloadLength(), properties.getHandshakeTimeout(),
                handlerCapacity));
    }
}
