package com.ss.gb28181.autoconfigure;

import com.ss.gb28181.DeviceCredentials;
import com.ss.gb28181.Gb28181Server;
import com.ss.gb28181.GbAlarmListener;
import com.ss.gb28181.GbCatalogListener;
import com.ss.gb28181.GbMobilePositionListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Optional;

/**
 * GB28181 注册、心跳和目录查询服务自动配置。
 */
@AutoConfiguration
@EnableConfigurationProperties(Gb28181Properties.class)
@ConditionalOnClass(Gb28181Server.class)
@ConditionalOnProperty(prefix = "simple-secret.gb28181", name = "enabled", havingValue = "true")
public class SimpleSecretGb28181AutoConfiguration {

    /**
     * 创建由 Spring 容器管理生命周期的默认 GB28181 服务。
     *
     * @param properties GB28181 配置
     * @param credentials 宿主提供的设备密码查询接口
     * @param alarmListener 可选报警监听器
     * @param catalogListener 可选目录监听器
     * @param mobilePositionListener 可选移动位置监听器
     * @return 已启动的 GB28181 服务
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(Gb28181Server.class)
    Gb28181Server gb28181Server(Gb28181Properties properties, DeviceCredentials credentials,
                                Optional<GbAlarmListener> alarmListener,
                                Optional<GbCatalogListener> catalogListener,
                                Optional<GbMobilePositionListener> mobilePositionListener) {
        return Gb28181Server.open(properties.toOptions(), credentials,
                alarmListener.orElse(null), catalogListener.orElse(null), mobilePositionListener.orElse(null));
    }
}
