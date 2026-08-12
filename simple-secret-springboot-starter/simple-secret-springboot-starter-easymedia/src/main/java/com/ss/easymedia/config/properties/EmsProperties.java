package com.ss.easymedia.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * ems模块配置
 *
 * @author JunPzx
 * @since 2025/8/25 14:10
 */
@ConfigurationProperties(prefix = "simple-secret.easymedia")
public class EmsProperties {
    /**
     * 是否启用 EasyMedia。
     */
    private boolean enabled;

    /**
     * 是否暴露通用媒体管理 API。
     */
    private boolean managementApiEnabled;

    /**
     * 播放地址模板
     */
    private String defaultPlayUrlTemplate = "{}://{}:{}/{}/{}.live.ts";

    /**
     * 推流地址模板
     */
    private String defaultPublishUrlTemplate = "rtmp://{}:{}/{}/{}";

    /**
     * 服务配置
     */
    @NestedConfigurationProperty
    private ServerProperties server;

    /**
     * WebRTC 会话配置。
     */
    @NestedConfigurationProperty
    private WebRtcProperties webrtc = new WebRtcProperties();

    /**
     * 判断是否启用。
     *
     * @return 满足条件时返回 true
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 判断{@code managementApiEnabled}。
     *
     * @return 满足条件时返回 true
     */
    public boolean isManagementApiEnabled() {
        return managementApiEnabled;
    }

    /**
     * 设置{@code managementApiEnabled}。
     *
     * @param managementApiEnabled 是否启用媒体管理 API
     */
    public void setManagementApiEnabled(boolean managementApiEnabled) {
        this.managementApiEnabled = managementApiEnabled;
    }

    /**
     * 返回默认播放地址模板。
     *
     * @return 默认播放地址模板
     */
    public String getDefaultPlayUrlTemplate() {
        return defaultPlayUrlTemplate;
    }

    /**
     * 设置{@code defaultPlayUrlTemplate}。
     *
     * @param defaultPlayUrlTemplate 默认播放地址模板
     */
    public void setDefaultPlayUrlTemplate(String defaultPlayUrlTemplate) {
        this.defaultPlayUrlTemplate = defaultPlayUrlTemplate;
    }

    /**
     * 返回默认推流地址模板。
     *
     * @return 默认推流地址模板
     */
    public String getDefaultPublishUrlTemplate() {
        return defaultPublishUrlTemplate;
    }

    /**
     * 设置{@code defaultPublishUrlTemplate}。
     *
     * @param defaultPublishUrlTemplate 默认推流地址模板
     */
    public void setDefaultPublishUrlTemplate(String defaultPublishUrlTemplate) {
        this.defaultPublishUrlTemplate = defaultPublishUrlTemplate;
    }

    /**
     * 返回EasyMedia 服务端配置。
     *
     * @return EasyMedia 服务端配置
     */
    public ServerProperties getServer() {
        return server;
    }

    /**
     * 设置{@code server}。
     *
     * @param server EasyMedia 服务端配置
     */
    public void setServer(ServerProperties server) {
        this.server = server;
    }

    /**
     * 返回WebRTC 配置。
     *
     * @return WebRTC 配置
     */
    public WebRtcProperties getWebrtc() {
        return webrtc;
    }

    /**
     * 设置{@code webrtc}。
     *
     * @param webrtc WebRTC 配置
     */
    public void setWebrtc(WebRtcProperties webrtc) {
        this.webrtc = webrtc;
    }
}
