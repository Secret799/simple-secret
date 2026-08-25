package com.ss.ics.dahua.config;

import com.ss.ics.dahua.DahuaCameraSdkService;
import com.ss.ics.dahua.DahuaSdkOptions;

/** 创建大华 Camera SDK 服务的可替换入口。 */
@FunctionalInterface
public interface DahuaCameraSdkServiceFactory {

    /**
     * @param options 已校验的 SDK 配置
     * @return 已初始化的 SDK 服务
     */
    DahuaCameraSdkService create(DahuaSdkOptions options);
}
