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

    /**
     * 是否启用。
     */
    private boolean enabled;
    /**
     * 是否随 Spring 容器自动启动。
     */
    private boolean autoStartup = true;
    /**
     * 监听或连接主机。
     */
    private String host = "127.0.0.1";
    /**
     * 监听或连接端口。
     */
    private int port;
    /**
     * Netty 接收连接的线程数。
     */
    private int bossThreads = 1;
    /**
     * 工作线程数。
     */
    private int workerThreads;
    /**
     * 允许接收的最大 HTTP 聚合内容字节数。
     */
    private int maxHttpContentLength = 65_536;
    /**
     * 允许接收的最大 WebSocket 帧负载字节数。
     */
    private int maxFramePayloadLength = 65_536;
    /**
     * {@code handshake}超时时间。
     */
    private Duration handshakeTimeout = Duration.ofSeconds(10);
    /**
     * 关闭等待时间。
     */
    private Duration shutdownTimeout = Duration.ofSeconds(5);
    /**
     * 消息处理核心线程数。
     */
    private int handlerCoreSize = 2;
    /**
     * 消息处理最大线程数。
     */
    private int handlerMaxSize = 8;
    /**
     * 消息处理队列容量。
     */
    private int handlerQueueCapacity = 1_024;
    /**
     * 端点配置集合。
     */
    private Map<String, Endpoint> endpoints = new LinkedHashMap<>();

    /**
     * 返回是否启用独立监听服务。
     *
     * @return 满足条件时返回 true
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用独立监听服务。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回是否由 Spring 生命周期自动启动监听。
     *
     * @return 满足条件时返回 true
     */
    public boolean isAutoStartup() {
        return autoStartup;
    }

    /**
     * 设置是否由 Spring 生命周期自动启动监听。
     *
     * @param autoStartup 是否随 Spring 容器自动启动
     */
    public void setAutoStartup(boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    /**
     * 返回监听地址。
     *
     * @return 监听或连接主机
     */
    public String getHost() {
        return host;
    }

    /**
     * 设置监听地址。
     *
     * @param host 监听或连接主机
     */
    public void setHost(String host) {
        this.host = host;
    }

    /**
     * 返回监听端口；零表示由操作系统分配。
     *
     * @return 监听或连接端口
     */
    public int getPort() {
        return port;
    }

    /**
     * 设置监听端口。
     *
     * @param port 监听或连接端口
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * 返回接收连接的 event loop 线程数。
     *
     * @return Netty 接收连接的线程数
     */
    public int getBossThreads() {
        return bossThreads;
    }

    /**
     * 设置接收连接的 event loop 线程数。
     *
     * @param bossThreads Netty 接收连接的线程数
     */
    public void setBossThreads(int bossThreads) {
        this.bossThreads = bossThreads;
    }

    /**
     * 返回 I/O event loop 线程数；零使用 Netty 默认值。
     *
     * @return 工作线程数
     */
    public int getWorkerThreads() {
        return workerThreads;
    }

    /**
     * 设置 I/O event loop 线程数。
     *
     * @param workerThreads 工作线程数
     */
    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    /**
     * 返回聚合 HTTP 握手请求的最大字节数。
     *
     * @return 最大 HTTP 聚合内容字节数
     */
    public int getMaxHttpContentLength() {
        return maxHttpContentLength;
    }

    /**
     * 设置聚合 HTTP 握手请求的最大字节数。
     *
     * @param maxHttpContentLength 最大 HTTP 聚合内容字节数
     */
    public void setMaxHttpContentLength(int maxHttpContentLength) {
        this.maxHttpContentLength = maxHttpContentLength;
    }

    /**
     * 返回聚合 WebSocket frame 的最大字节数。
     *
     * @return 最大 WebSocket 帧负载字节数
     */
    public int getMaxFramePayloadLength() {
        return maxFramePayloadLength;
    }

    /**
     * 设置聚合 WebSocket frame 的最大字节数。
     *
     * @param maxFramePayloadLength 最大 WebSocket 帧负载字节数
     */
    public void setMaxFramePayloadLength(int maxFramePayloadLength) {
        this.maxFramePayloadLength = maxFramePayloadLength;
    }

    /**
     * 返回握手超时。
     *
     * @return WebSocket 握手超时时间
     */
    public Duration getHandshakeTimeout() {
        return handshakeTimeout;
    }

    /**
     * 设置握手超时。
     *
     * @param handshakeTimeout WebSocket 握手超时时间
     */
    public void setHandshakeTimeout(Duration handshakeTimeout) {
        this.handshakeTimeout = handshakeTimeout;
    }

    /**
     * 返回服务停止超时。
     *
     * @return 关闭等待时间
     */
    public Duration getShutdownTimeout() {
        return shutdownTimeout;
    }

    /**
     * 设置服务停止超时。
     *
     * @param shutdownTimeout 关闭等待时间
     */
    public void setShutdownTimeout(Duration shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    /**
     * 返回消息处理线程池核心线程数。
     *
     * @return 处理器核心线程数
     */
    public int getHandlerCoreSize() {
        return handlerCoreSize;
    }

    /**
     * 设置消息处理线程池核心线程数。
     *
     * @param handlerCoreSize 处理器核心线程数
     */
    public void setHandlerCoreSize(int handlerCoreSize) {
        this.handlerCoreSize = handlerCoreSize;
    }

    /**
     * 返回消息处理线程池最大线程数。
     *
     * @return 处理器最大线程数
     */
    public int getHandlerMaxSize() {
        return handlerMaxSize;
    }

    /**
     * 设置消息处理线程池最大线程数。
     *
     * @param handlerMaxSize 处理器最大线程数
     */
    public void setHandlerMaxSize(int handlerMaxSize) {
        this.handlerMaxSize = handlerMaxSize;
    }

    /**
     * 返回消息处理队列容量。
     *
     * @return 处理器队列容量
     */
    public int getHandlerQueueCapacity() {
        return handlerQueueCapacity;
    }

    /**
     * 设置消息处理队列容量。
     *
     * @param handlerQueueCapacity 处理器队列容量
     */
    public void setHandlerQueueCapacity(int handlerQueueCapacity) {
        this.handlerQueueCapacity = handlerQueueCapacity;
    }

    /**
     * 返回命名端点配置。
     *
     * @return 端点配置集合
     */
    public Map<String, Endpoint> getEndpoints() {
        return endpoints;
    }

    /**
     * 设置命名端点配置。
     *
     * @param endpoints 端点配置集合
     */
    public void setEndpoints(Map<String, Endpoint> endpoints) {
        this.endpoints = endpoints == null ? new LinkedHashMap<>() : new LinkedHashMap<>(endpoints);
    }

    /** 单个 WebSocket 端点配置。 */
    public static class Endpoint {

        /**
         * 是否启用。
         */
        private boolean enabled = true;
        /**
         * 文件或资源路径。
         */
        private String path;
        /**
         * 是否要求认证。
         */
        private boolean authenticationRequired = true;
        /**
         * 允许的 Origin 列表。
         */
        private List<String> allowedOrigins = new ArrayList<>();

        /**
         * 返回端点是否启用。
         *
         * @return 满足条件时返回 true
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 设置端点是否启用。
         *
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 返回端点绝对路径。
         *
         * @return 文件或资源路径
         */
        public String getPath() {
            return path;
        }

        /**
         * 设置端点绝对路径。
         *
         * @param path 文件或资源路径
         */
        public void setPath(String path) {
            this.path = path;
        }

        /**
         * 返回端点是否要求认证。
         *
         * @return 满足条件时返回 true
         */
        public boolean isAuthenticationRequired() {
            return authenticationRequired;
        }

        /**
         * 设置端点是否要求认证。
         *
         * @param authenticationRequired 是否要求认证
         */
        public void setAuthenticationRequired(boolean authenticationRequired) {
            this.authenticationRequired = authenticationRequired;
        }

        /**
         * 返回精确允许的 Origin。
         *
         * @return 允许的 Origin 列表
         */
        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        /**
         * 设置精确允许的 Origin。
         *
         * @param allowedOrigins 允许的 Origin 列表
         */
        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins == null
                    ? new ArrayList<>() : new ArrayList<>(allowedOrigins);
        }
    }
}
