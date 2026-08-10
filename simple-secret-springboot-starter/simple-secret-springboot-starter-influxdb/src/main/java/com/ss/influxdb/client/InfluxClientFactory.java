package com.ss.influxdb.client;

import com.ss.influxdb.config.InfluxdbProperties;
import org.influxdb.InfluxDB;

/**
 * 创建 InfluxDB 客户端的可替换工厂边界。
 */
@FunctionalInterface
public interface InfluxClientFactory {
    /**
     * 根据已校验配置创建未共享的客户端。
     *
     * @param properties 连接配置
     * @return 新客户端
     */
    InfluxDB create(InfluxdbProperties properties);
}
