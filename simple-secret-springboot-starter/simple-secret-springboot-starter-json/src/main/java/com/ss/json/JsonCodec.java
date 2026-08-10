package com.ss.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ss.json.exception.JsonOperationException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 基于调用方提供的 {@link ObjectMapper} 执行 JSON 编解码。
 *
 * <p>codec 不复制也不修改 mapper，mapper 的配置和生命周期由调用方负责。</p>
 */
public final class JsonCodec {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST_TYPE = new TypeReference<>() { };

    private final ObjectMapper objectMapper;

    /**
     * 创建 JSON codec。
     *
     * @param objectMapper 已完成配置的 mapper
     */
    public JsonCodec(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("ObjectMapper must not be null");
        }
        this.objectMapper = objectMapper;
    }

    /**
     * 获取当前 codec 使用的 mapper。
     *
     * @return 构造时传入的 mapper 实例
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param value 待序列化对象
     * @return JSON 字符串；输入为 {@code null} 时返回 {@code null}
     */
    public String toJsonString(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw failure("serialize", value.getClass().getName(), e);
        }
    }

    /**
     * 将 JSON 字符串反序列化为指定类型。
     *
     * @param json JSON 字符串
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 反序列化结果；空输入返回 {@code null}
     */
    public <T> T parseObject(String json, Class<T> type) {
        require(type, "Target type");
        if (isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (IOException e) {
            throw failure("deserialize", type.getName(), e);
        }
    }

    /**
     * 将 JSON 字节反序列化为指定类型。
     *
     * @param json JSON 字节
     * @param type 目标类型
     * @param <T>  目标类型
     * @return 反序列化结果；空输入返回 {@code null}
     */
    public <T> T parseObject(byte[] json, Class<T> type) {
        require(type, "Target type");
        if (json == null || json.length == 0) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (IOException e) {
            throw failure("deserialize", type.getName(), e);
        }
    }

    /**
     * 按泛型类型信息反序列化 JSON 字符串。
     *
     * @param json JSON 字符串
     * @param type 泛型类型引用
     * @param <T>  目标类型
     * @return 反序列化结果；空输入返回 {@code null}
     */
    public <T> T parseObject(String json, TypeReference<T> type) {
        require(type, "Type reference");
        if (isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (IOException e) {
            throw failure("deserialize", type.getType().getTypeName(), e);
        }
    }

    /**
     * 将 JSON 数组反序列化为对象列表。
     *
     * @param json        JSON 数组字符串
     * @param elementType 元素类型
     * @param <T>         元素类型
     * @return 对象列表；空输入返回不可变空列表
     */
    public <T> List<T> parseArray(String json, Class<T> elementType) {
        require(elementType, "Element type");
        if (isBlank(json)) {
            return List.of();
        }
        JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        try {
            return objectMapper.readValue(json, type);
        } catch (IOException e) {
            throw failure("deserialize", type.toCanonical(), e);
        }
    }

    /**
     * 将 JSON 对象反序列化为字符串键 Map。
     *
     * @param json JSON 对象字符串
     * @return Map；空输入返回 {@code null}
     */
    public Map<String, Object> parseMap(String json) {
        return parseObject(json, MAP_TYPE);
    }

    /**
     * 将 JSON 数组反序列化为 Map 列表。
     *
     * @param json JSON 数组字符串
     * @return Map 列表；空输入返回不可变空列表
     */
    public List<Map<String, Object>> parseArrayMap(String json) {
        if (isBlank(json)) {
            return List.of();
        }
        return parseObject(json, MAP_LIST_TYPE);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static void require(Object value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " must not be null");
        }
    }

    private static JsonOperationException failure(String operation, String target, Exception cause) {
        return new JsonOperationException("Unable to " + operation + " JSON for " + target,
                new SanitizedJsonCause(cause));
    }

    private static final class SanitizedJsonCause extends RuntimeException {
        private SanitizedJsonCause(Exception source) {
            super("Jackson operation failed: " + source.getClass().getName());
            setStackTrace(source.getStackTrace());
        }
    }
}
