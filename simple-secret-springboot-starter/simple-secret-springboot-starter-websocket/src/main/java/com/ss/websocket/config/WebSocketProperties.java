package com.ss.websocket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** WebSocket starter 配置项。 */
@ConfigurationProperties("simple-secret.websocket")
public class WebSocketProperties {

    private boolean enabled;
    private List<String> paths = new ArrayList<>();
    private List<String> allowedOrigins = new ArrayList<>();
    private Duration sendTimeLimit = Duration.ofSeconds(10);
    private int sendBufferSize = 512 * 1024;
    private String nodeId = UUID.randomUUID().toString();

    /** 返回是否启用自动配置。 */
    public boolean isEnabled() {
        return enabled;
    }

    /** 设置是否启用自动配置。 */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** 返回允许注册的端点路径；空列表表示注册全部 handler。 */
    public List<String> getPaths() {
        return paths;
    }

    /** 设置允许注册的端点路径。 */
    public void setPaths(List<String> paths) {
        this.paths = paths == null ? new ArrayList<>() : new ArrayList<>(paths);
    }

    /** 返回显式允许的 Origin；空列表保留 Spring 同源策略。 */
    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    /** 设置显式允许的 Origin。 */
    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null
                ? new ArrayList<>() : new ArrayList<>(allowedOrigins);
    }

    /** 返回并发发送的单次时间上限。 */
    public Duration getSendTimeLimit() {
        return sendTimeLimit;
    }

    /** 设置并发发送的单次时间上限。 */
    public void setSendTimeLimit(Duration sendTimeLimit) {
        this.sendTimeLimit = sendTimeLimit;
    }

    /** 返回并发发送缓冲区字节上限。 */
    public int getSendBufferSize() {
        return sendBufferSize;
    }

    /** 设置并发发送缓冲区字节上限。 */
    public void setSendBufferSize(int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }

    /** 返回跨节点消息来源节点标识。 */
    public String getNodeId() {
        return nodeId;
    }

    /** 设置跨节点消息来源节点标识。 */
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
}
