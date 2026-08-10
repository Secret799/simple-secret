package com.ss.zlm4j.domain;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 媒体信息域
 *
 * @author JunPzx
 * @since 2024/7/25 17:35
 */
@Data
@Accessors(chain = true)
public class MediaInfoDomain {

    /**
     * 应用名
     */
    private String app;
    /**
     * 流id
     */
    private String stream;
    /**
     * 协议
     */
    private String schema;
    /**
     * 虚拟主机
     */
    private String vhost;
    /**
     * 当前媒体主机
     */
    private String host;
    /**
     * 当前媒体端口
     */
    private short port;
    /**
     * 当前媒体参数
     */
    private String params;
}
