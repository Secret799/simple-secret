package com.ss.dict.exception;

/** 字典字段元数据或反射读写不符合公开契约时抛出的异常。 */
public class DictionaryMappingException extends RuntimeException {

    /**
     * 使用说明信息创建异常。
     *
     * @param message 错误说明
     */
    public DictionaryMappingException(String message) {
        super(message);
    }

    /**
     * 使用说明信息和原始异常创建异常。
     *
     * @param message 错误说明
     * @param cause   原始异常
     */
    public DictionaryMappingException(String message, Throwable cause) {
        super(message, cause);
    }
}
