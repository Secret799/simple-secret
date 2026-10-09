package com.ss.application.dahuatest.web;

import com.ss.ics.constants.enums.PtzControlCommandEnums;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 云台控制请求。凭据仅在内存中传递，禁止落日志。
 *
 * @param ip 设备地址
 * @param port NetSDK 端口
 * @param username 登录账号
 * @param password 登录密码
 * @param channel 逻辑通道，1 起
 * @param command 云台命令
 * @param isBegin true 按下开始，false 松开停止（跨调用，设备要求同会话配对时不可用）
 * @param speedLevel 速度档位 1-8，空则由 SDK 取默认
 * @param duration ISO-8601 时长（如 PT0.8S），单次调用内完成 开始→等待→停止
 * @author junpzx
 * @since 2026-09-16
 */
public record PtzRequest(
        @NotBlank(message = "ip 不能为空") String ip,
        @NotBlank(message = "port 不能为空") @Pattern(regexp = "[0-9]+", message = "port 必须是数字") String port,
        @NotBlank(message = "username 不能为空") String username,
        @NotBlank(message = "password 不能为空") String password,
        @NotBlank(message = "channel 不能为空") @Pattern(regexp = "[1-9][0-9]*", message = "channel 必须是正整数") String channel,
        @NotNull(message = "command 不能为空") PtzControlCommandEnums command,
        Boolean isBegin,
        @Min(value = 1, message = "speedLevel 取值 1-8") @Max(value = 8, message = "speedLevel 取值 1-8") Integer speedLevel,
        String duration) {

    /**
     * 页面按下语义：isBegin 缺省视为 true。
     *
     * @return 是否开始
     */
    public boolean begin() {
        return isBegin == null || isBegin;
    }
}
