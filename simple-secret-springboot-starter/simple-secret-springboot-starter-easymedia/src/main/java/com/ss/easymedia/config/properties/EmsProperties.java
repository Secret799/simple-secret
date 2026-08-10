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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isManagementApiEnabled() {
        return managementApiEnabled;
    }

    public void setManagementApiEnabled(boolean managementApiEnabled) {
        this.managementApiEnabled = managementApiEnabled;
    }

    public String getDefaultPlayUrlTemplate() {
        return defaultPlayUrlTemplate;
    }

    public void setDefaultPlayUrlTemplate(String defaultPlayUrlTemplate) {
        this.defaultPlayUrlTemplate = defaultPlayUrlTemplate;
    }

    public String getDefaultPublishUrlTemplate() {
        return defaultPublishUrlTemplate;
    }

    public void setDefaultPublishUrlTemplate(String defaultPublishUrlTemplate) {
        this.defaultPublishUrlTemplate = defaultPublishUrlTemplate;
    }

    public ServerProperties getServer() {
        return server;
    }

    public void setServer(ServerProperties server) {
        this.server = server;
    }

    public WebRtcProperties getWebrtc() {
        return webrtc;
    }

    public void setWebrtc(WebRtcProperties webrtc) {
        this.webrtc = webrtc;
    }
}
