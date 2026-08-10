package com.ss.zlm4j.security;

/**
 * 媒体资源的使用场景，用于错误诊断和后续策略扩展。
 */
public enum MediaResourceUsage {
    PULL,
    PUSH,
    SNAPSHOT,
    TRANSCODE,
    STACK_VIDEO,
    STACK_IMAGE,
    STACK_OUTPUT
}
