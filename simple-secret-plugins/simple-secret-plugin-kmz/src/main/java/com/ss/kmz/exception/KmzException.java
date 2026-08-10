package com.ss.kmz.exception;

import java.io.Serial;

/**
 * KML、WPML 或 KMZ 内容无法安全读取或写出时抛出的异常。
 */
public class KmzException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 创建异常。 */
    public KmzException(String message) {
        super(message);
    }

    /** 创建带原因的异常。 */
    public KmzException(String message, Throwable cause) {
        super(message, cause);
    }
}
