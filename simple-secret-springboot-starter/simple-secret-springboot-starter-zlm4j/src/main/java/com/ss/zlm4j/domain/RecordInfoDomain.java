package com.ss.zlm4j.domain;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 录制信息域
 *
 * @author JunPzx
 * @since 2025/8/20 14:26
 */
@Data
@Accessors(chain = true)
public class RecordInfoDomain {
    /**
     * 应用名称
     */
    private String app;
    /**
     * 流ID
     */
    private String stream;
    /**
     * url
     */
    private String url;
    /**
     * 虚拟主机
     */
    private String vhost;
    /**
     * 文件名称
     */
    private String fileName;
    /**
     * 文件路径
     */
    private String filePath;
    /**
     * 文件夹路径
     */
    private String folderPath;
    /**
     * 时长（秒）
     */
    private float duration;
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    /**
     * 文件大小（byte）
     */
    private long fileSize;
}
