package com.ss.auth.domain;

/**
 * 客户端可用状态。
 */
public enum ClientStatus {
    /** 客户端可用于认证。 */
    NORMAL,
    /** 客户端已禁用。 */
    DISABLED
}
