package com.ss.json.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ss.json.JsonCodec;
import com.ss.json.config.DefaultObjectMapperFactory;

import java.util.List;
import java.util.Map;

/**
 * 非 Spring 场景使用的静态 JSON 工具。
 *
 * <p>该类使用独立的默认 mapper，不接收 Spring 容器中的 Jackson 定制。</p>
 */
public final class JsonUtils {
    private static final JsonCodec DEFAULT_CODEC = new JsonCodec(DefaultObjectMapperFactory.create());

    private JsonUtils() {
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param value 待序列化对象
     * @return JSON 字符串；输入为 {@code null} 时返回 {@code null}
     */
    public static String toJsonString(Object value) {
        return DEFAULT_CODEC.toJsonString(value);
    }

    /**
     * 将 JSON 字符串反序列化为指定类型。
     *
     * @param json JSON 字符串
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 反序列化结果；空输入返回 {@code null}
     */
    public static <T> T parseObject(String json, Class<T> type) {
        return DEFAULT_CODEC.parseObject(json, type);
    }

    /**
     * 将 UTF-8 JSON 字节反序列化为指定类型。
     *
     * @param json JSON 字节
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 反序列化结果；空输入返回 {@code null}
     */
    public static <T> T parseObject(byte[] json, Class<T> type) {
        return DEFAULT_CODEC.parseObject(json, type);
    }

    /**
     * 按泛型类型信息反序列化 JSON 字符串。
     *
     * @param json JSON 字符串
     * @param type 泛型类型引用
     * @param <T>  目标类型
     * @return 反序列化结果；空输入返回 {@code null}
     */
    public static <T> T parseObject(String json, TypeReference<T> type) {
        return DEFAULT_CODEC.parseObject(json, type);
    }

    /**
     * 将 JSON 数组反序列化为对象列表。
     *
     * @param json JSON 数组字符串
     * @param type 元素类型
     * @param <T>  元素类型
     * @return 对象列表；空输入返回不可变空列表
     */
    public static <T> List<T> parseArray(String json, Class<T> type) {
        return DEFAULT_CODEC.parseArray(json, type);
    }

    /**
     * 将 JSON 对象反序列化为字符串键 Map。
     *
     * @param json JSON 对象字符串
     * @return Map；空输入返回 {@code null}
     */
    public static Map<String, Object> parseMap(String json) {
        return DEFAULT_CODEC.parseMap(json);
    }

    /**
     * 将 JSON 数组反序列化为 Map 列表。
     *
     * @param json JSON 数组字符串
     * @return Map 列表；空输入返回不可变空列表
     */
    public static List<Map<String, Object>> parseArrayMap(String json) {
        return DEFAULT_CODEC.parseArrayMap(json);
    }
}
