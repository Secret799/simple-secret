package com.ss.mybatis.database;

/** 读取 JDBC 数据库元数据失败。 */
public final class DatabaseMetadataException extends RuntimeException {

    /**
     * 创建数据库元数据异常。
     *
     * @param message 安全错误消息
     * @param cause 原始异常
     */
    public DatabaseMetadataException(String message, Throwable cause) {
        super(message, cause);
    }
}
