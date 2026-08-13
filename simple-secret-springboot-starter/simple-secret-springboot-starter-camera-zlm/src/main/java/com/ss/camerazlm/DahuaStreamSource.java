package com.ss.camerazlm;

import com.ss.ics.dahua.DahuaStreamCallback;
import com.ss.ics.domain.DeviceDomain;
import com.ss.ics.domain.PlayDomain;

/**
 * 供适配服务隔离厂商 SDK 的窄取流边界。
 *
 * @author junpzx
 * @since 2026-08-13
 */
@FunctionalInterface
interface DahuaStreamSource {

    /**
     * 启动实时 H.264 预览。
     *
     * @param device 设备连接信息
     * @param play 取流参数
     * @param callback 帧回调
     * @return 可关闭的厂商预览会话
     */
    AutoCloseable start(DeviceDomain device, PlayDomain play, DahuaStreamCallback callback);
}
