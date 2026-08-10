package com.ss.json.property;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ss.common.toolbox.function.SerializableFunction;
import com.ss.common.toolbox.property.LambdaPropertyResolver;

import java.lang.reflect.Field;

/**
 * 根据 getter 方法引用解析对应的 JSON 属性名。
 *
 * <p>字段上的非空 {@link JsonProperty} 优先于字段名转换规则。</p>
 */
public final class JsonPropertyNameResolver {
    private JsonPropertyNameResolver() {
    }

    /**
     * 保持字段原始大小写并解析 JSON 属性名。
     *
     * @param getter JavaBean getter 方法引用
     * @param <T>    getter 所属类型
     * @return JSON 属性名
     */
    public static <T> String resolve(SerializableFunction<T, ?> getter) {
        return resolve(getter, "", NameCase.PRESERVE);
    }

    /**
     * 按指定分隔符和大小写规则解析 JSON 属性名。
     *
     * @param getter    JavaBean getter 方法引用
     * @param separator 驼峰单词分隔符
     * @param nameCase  大小写规则
     * @param <T>       getter 所属类型
     * @return JSON 属性名
     */
    public static <T> String resolve(SerializableFunction<T, ?> getter,
                                     String separator, NameCase nameCase) {
        if (separator == null) {
            throw new IllegalArgumentException("Separator must not be null");
        }
        if (nameCase == null) {
            throw new IllegalArgumentException("Name case must not be null");
        }
        Field field = LambdaPropertyResolver.resolveField(getter);
        JsonProperty annotation = field.getAnnotation(JsonProperty.class);
        if (annotation != null && !annotation.value().isBlank()) {
            return annotation.value();
        }
        return nameCase.apply(splitCamelCase(field.getName(), separator));
    }

    private static String splitCamelCase(String value, String separator) {
        StringBuilder result = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (index > 0 && Character.isUpperCase(current)
                    && (Character.isLowerCase(value.charAt(index - 1))
                    || Character.isDigit(value.charAt(index - 1)))) {
                result.append(separator);
            }
            result.append(current);
        }
        return result.toString();
    }
}
