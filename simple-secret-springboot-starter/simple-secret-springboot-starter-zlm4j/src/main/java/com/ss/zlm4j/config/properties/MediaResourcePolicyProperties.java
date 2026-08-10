package com.ss.zlm4j.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 外部媒体资源与录像目录的访问策略。
 */
@Data
@ConfigurationProperties(prefix = "simple-secret.zlm4j.resource-policy")
public class MediaResourcePolicyProperties {

    /**
     * 允许访问的媒体协议。
     */
    private Set<String> allowedSchemes = new LinkedHashSet<>(
            Set.of("http", "https", "rtsp", "rtsps", "rtmp", "rtmps"));

    /**
     * 可显式访问的主机名，匹配时忽略大小写。
     */
    private Set<String> allowedHosts = new LinkedHashSet<>();

    /**
     * 可显式访问的 IP 网段。
     */
    private Set<String> allowedCidrs = new LinkedHashSet<>();

    /**
     * 录像文件允许写入的根目录。
     */
    private String recordingRoot = "./www";
}
