package com.ss.influxdb.exception;

/**
 * InfluxDB 映射、查询、写入或初始化失败时抛出的统一异常。
 */
public class InfluxOperationException extends RuntimeException {
    /** 创建不带原始异常的操作异常。 */
    public InfluxOperationException(String message) {
        super(message);
    }

    /** 创建保留原始原因的操作异常。 */
    public InfluxOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
