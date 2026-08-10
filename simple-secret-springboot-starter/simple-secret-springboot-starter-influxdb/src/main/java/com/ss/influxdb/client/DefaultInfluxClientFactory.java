package com.ss.influxdb.client;

import com.ss.influxdb.config.InfluxdbProperties;
import okhttp3.OkHttpClient;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;

import java.util.concurrent.TimeUnit;

/**
 * 使用 influxdb-java 官方工厂和显式 HTTP 超时创建客户端。
 */
public class DefaultInfluxClientFactory implements InfluxClientFactory {
    @Override
    public InfluxDB create(InfluxdbProperties properties) {
        properties.validate();
        OkHttpClient.Builder httpClient = new OkHttpClient.Builder()
                .connectTimeout(properties.getConnectTimeoutMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(properties.getReadTimeoutMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(properties.getWriteTimeoutMillis(), TimeUnit.MILLISECONDS);
        if (properties.getUsername() == null || properties.getUsername().isBlank()) {
            return InfluxDBFactory.connect(properties.getUrl().trim(), httpClient);
        }
        return InfluxDBFactory.connect(properties.getUrl().trim(), properties.getUsername(),
                properties.getPassword() == null ? "" : properties.getPassword(), httpClient);
    }
}
