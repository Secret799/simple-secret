package com.ss.geo.photo;

/**
 * DJI 照片元数据无法安全读取时抛出的异常。
 */
public class DjiMetadataReadException extends IllegalArgumentException {

    /** 创建读取异常。 */
    public DjiMetadataReadException(String message) {
        super(message);
    }

    /** 创建带原始原因的读取异常。 */
    public DjiMetadataReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
