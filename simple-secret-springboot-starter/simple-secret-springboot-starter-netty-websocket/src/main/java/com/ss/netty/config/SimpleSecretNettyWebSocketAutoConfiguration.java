package com.ss.netty.config;

import com.ss.netty.auth.NettyWebSocketAuthenticator;
import com.ss.netty.handler.NettyWebSocketMessageHandler;
import com.ss.netty.server.NettyWebSocketChannelInitializer;
import com.ss.netty.server.NettyWebSocketEndpointRegistry;
import com.ss.netty.server.NettyWebSocketServer;
import com.ss.netty.session.NettyWebSocketChannelRegistry;
import io.netty.bootstrap.ServerBootstrap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Simple Secret 独立 Netty WebSocket 自动配置。 */
@AutoConfiguration
@ConditionalOnClass(ServerBootstrap.class)
@ConditionalOnProperty(prefix = "simple-secret.netty.websocket", name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(NettyWebSocketProperties.class)
public class SimpleSecretNettyWebSocketAutoConfiguration {

    /** 创建并校验端点和应用 handler 索引。 */
    @Bean
    @ConditionalOnMissingBean
    NettyWebSocketEndpointRegistry nettyWebSocketEndpointRegistry(
            NettyWebSocketProperties properties,
            ObjectProvider<NettyWebSocketMessageHandler> handlers,
            ObjectProvider<NettyWebSocketAuthenticator> authenticator) {
        List<NettyWebSocketMessageHandler> orderedHandlers = handlers.orderedStream().toList();
        return new NettyWebSocketEndpointRegistry(
                properties, orderedHandlers, authenticator.getIfAvailable());
    }

    /** 创建本应用实例内的 channel 注册表。 */
    @Bean
    @ConditionalOnMissingBean
    NettyWebSocketChannelRegistry nettyWebSocketChannelRegistry() {
        return new NettyWebSocketChannelRegistry();
    }

    /** 创建有界消息处理执行器。 */
    @Bean(name = "nettyWebSocketHandlerExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "nettyWebSocketHandlerExecutor")
    Executor nettyWebSocketHandlerExecutor(NettyWebSocketProperties properties) {
        AtomicInteger sequence = new AtomicInteger();
        return new ThreadPoolExecutor(
                properties.getHandlerCoreSize(), properties.getHandlerMaxSize(),
                60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(properties.getHandlerQueueCapacity()),
                task -> {
                    Thread thread = new Thread(task,
                            "simple-secret-netty-ws-handler-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** 创建每条连接的协议 pipeline 初始化器。 */
    @Bean
    @ConditionalOnMissingBean
    NettyWebSocketChannelInitializer nettyWebSocketChannelInitializer(
            NettyWebSocketProperties properties,
            NettyWebSocketEndpointRegistry endpoints,
            ObjectProvider<NettyWebSocketAuthenticator> authenticator,
            @Qualifier("nettyWebSocketHandlerExecutor") Executor handlerExecutor,
            NettyWebSocketChannelRegistry channels) {
        return new NettyWebSocketChannelInitializer(
                properties, endpoints, authenticator.getIfAvailable(), handlerExecutor, channels);
    }

    /** 创建由 Spring 自动启动和停止的 Netty WebSocket 服务。 */
    @Bean
    @ConditionalOnMissingBean
    NettyWebSocketServer nettyWebSocketServer(
            NettyWebSocketProperties properties,
            NettyWebSocketChannelInitializer initializer) {
        return new NettyWebSocketServer(properties, initializer);
    }
}
