package com.ss.core.domain;

import com.ss.core.http.HttpStatusCodes;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通用响应结果。
 *
 * @param <T> 响应数据类型
 */
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 结果状态码，通常使用 {@link HttpStatusCodes} 中的常量。 */
    private int code;

    /** 适合调用方展示的结果消息。 */
    private String message;

    /** 业务响应数据。 */
    private T data;

    /**
     * 创建空的成功结果。
     *
     * @return 状态码为 {@link HttpStatusCodes#OK} 的成功结果
     */
    public static Result<Void> ok() {
        return of(HttpStatusCodes.OK, "操作成功", null);
    }

    /**
     * 创建携带数据的成功结果。
     *
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 成功结果
     */
    public static <T> Result<T> ok(T data) {
        return of(HttpStatusCodes.OK, "操作成功", data);
    }

    /**
     * 创建只携带自定义消息的成功结果。
     *
     * @param message 响应消息
     * @return 成功结果
     */
    public static Result<Void> okMessage(String message) {
        return of(HttpStatusCodes.OK, message, null);
    }

    /**
     * 创建携带自定义消息和数据的成功结果。
     *
     * @param message 响应消息
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 成功结果
     */
    public static <T> Result<T> ok(String message, T data) {
        return of(HttpStatusCodes.OK, message, data);
    }

    /**
     * 创建空的失败结果。
     *
     * @return 状态码为 {@link HttpStatusCodes#INTERNAL_SERVER_ERROR} 的失败结果
     */
    public static Result<Void> fail() {
        return of(HttpStatusCodes.INTERNAL_SERVER_ERROR, "操作失败", null);
    }

    /**
     * 创建携带数据的失败结果。
     *
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 失败结果
     */
    public static <T> Result<T> fail(T data) {
        return of(HttpStatusCodes.INTERNAL_SERVER_ERROR, "操作失败", data);
    }

    /**
     * 创建只携带自定义消息的失败结果。
     *
     * @param message 响应消息
     * @return 失败结果
     */
    public static Result<Void> failMessage(String message) {
        return of(HttpStatusCodes.INTERNAL_SERVER_ERROR, message, null);
    }

    /**
     * 创建携带自定义消息和数据的失败结果。
     *
     * @param message 响应消息
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 失败结果
     */
    public static <T> Result<T> fail(String message, T data) {
        return of(HttpStatusCodes.INTERNAL_SERVER_ERROR, message, data);
    }

    /**
     * 创建指定状态码的失败结果。
     *
     * @param code 状态码
     * @param message 响应消息
     * @return 失败结果
     */
    public static Result<Void> fail(int code, String message) {
        return of(code, message, null);
    }

    /**
     * 创建警告结果。
     *
     * @param message 警告消息
     * @return 警告结果
     */
    public static Result<Void> warn(String message) {
        return of(HttpStatusCodes.WARNING, message, null);
    }

    /**
     * 创建携带数据的警告结果。
     *
     * @param message 警告消息
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 警告结果
     */
    public static <T> Result<T> warn(String message, T data) {
        return of(HttpStatusCodes.WARNING, message, data);
    }

    /**
     * 判断结果是否成功。
     *
     * @param result 待判断结果，可以为 null
     * @return 状态码为 200 时返回 true
     */
    public static boolean isSuccess(Result<?> result) {
        return result != null && result.code == HttpStatusCodes.OK;
    }

    /**
     * 判断结果是否失败。
     *
     * @param result 待判断结果，可以为 null
     * @return null 或状态码不是 200 时返回 true
     */
    public static boolean isError(Result<?> result) {
        return !isSuccess(result);
    }

    private static <T> Result<T> of(int code, String message, T data) {
        Result<T> result = new Result<>();
        result.code = code;
        result.message = message;
        result.data = data;
        return result;
    }

    /**
     * 返回状态码。
     *
     * @return 结果状态码
     */
    public int getCode() {
        return code;
    }

    /**
     * 设置状态码。
     *
     * @param code 结果状态码
     */
    public void setCode(int code) {
        this.code = code;
    }

    /**
     * 返回响应消息。
     *
     * @return 适合调用方展示的消息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置响应消息。
     *
     * @param message 适合调用方展示的消息
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 返回响应数据。
     *
     * @return 业务响应数据
     */
    public T getData() {
        return data;
    }

    /**
     * 设置响应数据。
     *
     * @param data 业务响应数据
     */
    public void setData(T data) {
        this.data = data;
    }
}
