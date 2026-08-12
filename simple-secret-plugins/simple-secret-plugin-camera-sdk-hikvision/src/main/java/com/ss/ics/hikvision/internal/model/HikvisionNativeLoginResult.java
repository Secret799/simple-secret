package com.ss.ics.hikvision.internal.model;

/**
 * 原生登录结果的驱动内部快照。
 *
 * @author junpzx
 * @since 2026-08-12
 */
public record HikvisionNativeLoginResult(
        int userId,
        int startChannel,
        int deviceType,
        int deviceCategory,
        String serialNumber) {
}
