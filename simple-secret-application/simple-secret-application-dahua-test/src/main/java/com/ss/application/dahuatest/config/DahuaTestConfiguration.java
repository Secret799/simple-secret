package com.ss.application.dahuatest.config;

import com.ss.ics.dahua.DahuaCameraSdkService;
import com.ss.ics.dahua.DahuaSdkOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * 大华 NetSDK 服务装配。
 *
 * <p>原生库目录只从环境变量 {@code DAHUA_NETSDK_DIRECTORY} 读取，凭据和厂商二进制路径不得写入源码。
 * 环境变量缺失时不创建 SDK Bean，应用仍可启动用于页面和 ZLM 自检，接口返回 SDK 未就绪。</p>
 *
 * @author junpzx
 * @since 2026-09-16
 */
@Configuration
public class DahuaTestConfiguration {

    /**
     * 创建随容器生命周期关闭的大华 SDK 服务。
     *
     * @return 大华 SDK 服务
     */
    @Bean(destroyMethod = "close")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
            "T(org.springframework.util.StringUtils).hasText(T(java.lang.System).getenv('DAHUA_NETSDK_DIRECTORY'))")
    public DahuaCameraSdkService dahuaCameraSdkService() {
        return DahuaCameraSdkService.open(
                DahuaSdkOptions.defaults(Path.of(System.getenv("DAHUA_NETSDK_DIRECTORY"))));
    }

}
