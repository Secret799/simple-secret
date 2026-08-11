package com.ss.netty.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 独立 Netty WebSocket 服务配置。 */
@ConfigurationProperties("simple-secret.netty.websocket")
public class NettyWebSocketProperties {

    private boolean enabled;
    private boolean autoStartup = true;
    private String host = "127.0.0.1";
    private int port;
    private int bossThreads = 1;
    private int workerThreads;
    private int maxHttpContentLength = 65_536;
    private int maxFramePayloadLength = 65_536;
    private Duration handshakeTimeout = Duration.ofSeconds(10);
    private Duration shutdownTimeout = Duration.ofSeconds(5);
    private int handlerCoreSize = 2;
    private int handlerMaxSize = 8;
    private int handlerQueueCapacity = 1_024;
    private Map<String, Endpoint> endpoints = new LinkedHashMap<>();

    /** 返回是否启用独立监听服务。 */
    public boolean isEnabled() {
        return enabled;
    }

    /** 设置是否启用独立监听服务。 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** 返回是否由 Spring 生命周期自动启动监听。 */
    public boolean isAutoStartup() {
        return autoStartup;
    }

    /** 设置是否由 Spring 生命周期自动启动监听。 */
    public void setAutoStartup(boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    /** 返回监听地址。 */
    public String getHost() {
        return host;
    }

    /** 设置监听地址。 */
    public void setHost(String host) {
        this.host = host;
    }

    /** 返回监听端口；零表示由操作系统分配。 */
    public int getPort() {
        return port;
    }

    /** 设置监听端口。 */
    public void setPort(int port) {
        this.port = port;
    }

    /** 返回接收连接的 event loop 线程数。 */
    public int getBossThreads() {
        return bossThreads;
    }

    /** 设置接收连接的 event loop 线程数。 */
    public void setBossThreads(int bossThreads) {
        this.bossThreads = bossThreads;
    }

    /** 返回 I/O event loop 线程数；零使用 Netty 默认值。 */
    public int getWorkerThreads() {
        return workerThreads;
    }

    /** 设置 I/O event loop 线程数。 */
    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    /** 返回聚合 HTTP 握手请求的最大字节数。 */
    public int getMaxHttpContentLength() {
        return maxHttpContentLength;
    }

    /** 设置聚合 HTTP 握手请求的最大字节数。 */
    public void setMaxHttpContentLength(int maxHttpContentLength) {
        this.maxHttpContentLength = maxHttpContentLength;
    }

    /** 返回聚合 WebSocket frame 的最大字节数。 */
    public int getMaxFramePayloadLength() {
        return maxFramePayloadLength;
    }

    /** 设置聚合 WebSocket frame 的最大字节数。 */
    public void setMaxFramePayloadLength(int maxFramePayloadLength) {
        this.maxFramePayloadLength = maxFramePayloadLength;
    }

    /** 返回握手超时。 */
    public Duration getHandshakeTimeout() {
        return handshakeTimeout;
    }

    /** 设置握手超时。 */
    public void setHandshakeTimeout(Duration handshakeTimeout) {
        this.handshakeTimeout = handshakeTimeout;
    }

    /** 返回服务停止超时。 */
    public Duration getShutdownTimeout() {
        return shutdownTimeout;
    }

    /** 设置服务停止超时。 */
    public void setShutdownTimeout(Duration shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    /** 返回消息处理线程池核心线程数。 */
    public int getHandlerCoreSize() {
        return handlerCoreSize;
    }

    /** 设置消息处理线程池核心线程数。 */
    public void setHandlerCoreSize(int handlerCoreSize) {
        this.handlerCoreSize = handlerCoreSize;
    }

    /** 返回消息处理线程池最大线程数。 */
    public int getHandlerMaxSize() {
        return handlerMaxSize;
    }

    /** 设置消息处理线程池最大线程数。 */
    public void setHandlerMaxSize(int handlerMaxSize) {
        this.handlerMaxSize = handlerMaxSize;
    }

    /** 返回消息处理队列容量。 */
    public int getHandlerQueueCapacity() {
        return handlerQueueCapacity;
    }

    /** 设置消息处理队列容量。 */
    public void setHandlerQueueCapacity(int handlerQueueCapacity) {
        this.handlerQueueCapacity = handlerQueueCapacity;
    }

    /** 返回命名端点配置。 */
    public Map<String, Endpoint> getEndpoints() {
        return endpoints;
    }

    /** 设置命名端点配置。 */
    public void setEndpoints(Map<String, Endpoint> endpoints) {
        this.endpoints = endpoints == null ? new LinkedHashMap<>() : new LinkedHashMap<>(endpoints);
    }

    /** 单个 WebSocket 端点配置。 */
    public static class Endpoint {

        private boolean enabled = true;
        private String path;
        private boolean authenticationRequired = true;
        private List<String> allowedOrigins = new ArrayList<>();

        /** 返回端点是否启用。 */
        public boolean isEnabled() {
            return enabled;
        }

        /** 设置端点是否启用。 */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /** 返回端点绝对路径。 */
        public String getPath() {
            return path;
        }

        /** 设置端点绝对路径。 */
        public void setPath(String path) {
            this.path = path;
        }

        /** 返回端点是否要求认证。 */
        public boolean isAuthenticationRequired() {
            return authenticationRequired;
        }

        /** 设置端点是否要求认证。 */
        public void setAuthenticationRequired(boolean authenticationRequired) {
            this.authenticationRequired = authenticationRequired;
        }

        /** 返回精确允许的 Origin。 */
        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        /** 设置精确允许的 Origin。 */
        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins == null
                    ? new ArrayList<>() : new ArrayList<>(allowedOrigins);
        }
    }
}
