package com.ss.application.dahuatest.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 开始推流请求。凭据仅在内存中传递，禁止落日志。
 *
 * @param ip 设备地址
 * @param port NetSDK 端口（大华默认 37777）
 * @param username 登录账号
 * @param password 登录密码
 * @param channel 逻辑通道，1 起（SDK 侧按起始通道换算）
 * @param streamType 0 主码流，1 子码流
 * @param app ZLM 应用名
 * @param stream ZLM 流名
 * @author junpzx
 * @since 2026-09-16
 */
public record StreamStartRequest(
        @NotBlank(message = "ip 不能为空") String ip,
        @NotBlank(message = "port 不能为空") @Pattern(regexp = "[0-9]+", message = "port 必须是数字") String port,
        @NotBlank(message = "username 不能为空") String username,
        @NotBlank(message = "password 不能为空") String password,
        @NotBlank(message = "channel 不能为空") @Pattern(regexp = "[1-9][0-9]*", message = "channel 必须是正整数") String channel,
        @NotNull(message = "streamType 不能为空") @Min(value = 0, message = "streamType 取值 0 或 1") @Max(value = 1, message = "streamType 取值 0 或 1") Integer streamType,
        @NotBlank(message = "app 不能为空") @Pattern(regexp = "[A-Za-z0-9._-]{1,128}", message = "app 只允许字母数字点下划线连字符") String app,
        @NotBlank(message = "stream 不能为空") @Pattern(regexp = "[A-Za-z0-9._-]{1,128}", message = "stream 只允许字母数字点下划线连字符") String stream) {
}
