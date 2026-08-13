package com.ss.application.djisei.config;

import com.ss.application.djisei.diagnostic.DjiSeiTrackCallback;
import com.ss.application.djisei.parser.H26xSeiParser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * DJI RTMP SEI 诊断组件配置。
 *
 * @author junpzx
 * @since 2026-08-13
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DjiSeiProperties.class)
public class DjiSeiConfiguration {

    /**
     * 提供可由宿主应用覆盖的 UTC 时钟。
     *
     * @return 系统 UTC 时钟
     */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock djiSeiClock() {
        return Clock.systemUTC();
    }

    /**
     * 创建仅在诊断开关启用时生效的媒体轨道回调。
     *
     * @param properties 已校验的诊断配置
     * @param clock 统计和汇总时钟
     * @return DJI RTMP SEI 轨道回调
     */
    @Bean
    @ConditionalOnProperty(prefix = "simple-secret.dji-sei", name = "enabled", havingValue = "true")
    public DjiSeiTrackCallback djiSeiTrackCallback(DjiSeiProperties properties, Clock clock) {
        return new DjiSeiTrackCallback(new H26xSeiParser(), properties, clock);
    }
}
