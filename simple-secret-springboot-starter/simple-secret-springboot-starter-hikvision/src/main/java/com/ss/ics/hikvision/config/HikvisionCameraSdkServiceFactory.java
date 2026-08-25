package com.ss.ics.hikvision.config;

import com.ss.ics.hikvision.HikvisionCameraSdkService;
import com.ss.ics.hikvision.HikvisionSdkOptions;

/** 创建海康威视 Camera SDK 服务的可替换入口。 */
@FunctionalInterface
public interface HikvisionCameraSdkServiceFactory {

    /**
     * @param options 已校验的 SDK 配置
     * @return 已初始化的 SDK 服务
     */
    HikvisionCameraSdkService create(HikvisionSdkOptions options);
}
