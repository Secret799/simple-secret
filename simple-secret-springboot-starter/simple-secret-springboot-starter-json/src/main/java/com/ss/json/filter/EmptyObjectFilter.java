package com.ss.json.filter;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Jackson 值过滤器：对象为 {@code null} 或所有实例字段均为 {@code null} 时视为空。
 *
 * <p>无法安全读取字段时保守地视为非空，避免反射限制导致有效数据被过滤。</p>
 */
public final class EmptyObjectFilter {

    @Override
    public boolean equals(Object value) {
        if (value == null) {
            return true;
        }
        Class<?> type = value.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                    continue;
                }
                try {
                    if (!field.trySetAccessible() || field.get(value) != null) {
                        return false;
                    }
                } catch (IllegalAccessException | RuntimeException e) {
                    return false;
                }
            }
            type = type.getSuperclass();
        }
        return true;
    }

    @Override
    public int hashCode() {
        // Jackson 将过滤器作为比较器使用；恒定值与上面的宽松 equals 语义保持一致。
        return 0;
    }

}
