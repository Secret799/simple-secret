package com.ss.camera.config;

import com.ss.camera.service.UrlAssemblyHolder;
import com.ss.camera.service.UrlAssemblyService;
import com.ss.camera.service.dahua.DahuaCameraUrlAssemblyService;
import com.ss.camera.service.dahua.DahuaNvrUrlAssemblyService;
import com.ss.camera.service.hikvision.HikCameraUrlAssemblyService;
import com.ss.camera.service.hikvision.HikNvrUrlAssemblyService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.util.List;

/** 自动注册内置摄像机 RTSP 地址组装器。 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "simple-secret.camera", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CameraAutoConfiguration {

    /** @return 海康威视摄像机地址组装器 */
    @Bean
    @ConditionalOnMissingBean
    HikCameraUrlAssemblyService hikCameraUrlAssemblyService() {
        return new HikCameraUrlAssemblyService();
    }

    /** @return 海康威视 NVR 地址组装器 */
    @Bean
    @ConditionalOnMissingBean
    HikNvrUrlAssemblyService hikNvrUrlAssemblyService() {
        return new HikNvrUrlAssemblyService();
    }

    /** @return 大华摄像机地址组装器 */
    @Bean
    @ConditionalOnMissingBean
    DahuaCameraUrlAssemblyService dahuaCameraUrlAssemblyService() {
        return new DahuaCameraUrlAssemblyService();
    }

    /** @return 大华 NVR 地址组装器 */
    @Bean
    @ConditionalOnMissingBean
    DahuaNvrUrlAssemblyService dahuaNvrUrlAssemblyService() {
        return new DahuaNvrUrlAssemblyService();
    }

    /**
     * @param services 应用上下文中的全部地址组装器
     * @return 不可变地址组装器注册表
     */
    @Bean
    @ConditionalOnMissingBean
    UrlAssemblyHolder urlAssemblyHolder(List<UrlAssemblyService> services) {
        return new UrlAssemblyHolder(services);
    }
}
