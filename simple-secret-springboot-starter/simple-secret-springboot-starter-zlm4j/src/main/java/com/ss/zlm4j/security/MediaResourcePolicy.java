package com.ss.zlm4j.security;

import java.net.URI;
import java.nio.file.Path;

/**
 * 校验外部媒体地址和录像写入路径的安全边界。
 */
public interface MediaResourcePolicy {

    /**
     * 校验并返回规范化的媒体 URI。
     *
     * @param value 原始地址
     * @param usage 使用场景
     * @return 规范化 URI
     */
    URI requireAllowed(String value, MediaResourceUsage usage);

    /**
     * 校验并返回录像根目录内的绝对路径。
     *
     * @param value 原始路径
     * @return 规范化绝对路径
     */
    Path requireRecordingPath(String value);
}
