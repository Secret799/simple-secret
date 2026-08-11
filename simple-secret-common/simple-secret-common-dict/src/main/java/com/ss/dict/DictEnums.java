package com.ss.dict;

import com.ss.dict.model.DictElement;
import com.ss.dict.model.DictValue;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 实现 {@link DictValue} 的枚举查询工具。 */
public final class DictEnums {

    private DictEnums() {
    }

    /**
     * 按编码查询枚举值。
     *
     * @param enumType 枚举类型
     * @param code     字典编码
     * @param <T>      枚举值类型
     * @return 匹配值，不存在时返回 {@code null}
     */
    public static <T extends DictValue> T find(Class<T> enumType, String code) {
        return findInternal(enumType, null, requireText(code, "code"));
    }

    /**
     * 按类型和编码查询枚举值。
     *
     * @param enumType 枚举类型
     * @param type     字典类型
     * @param code     字典编码
     * @param <T>      枚举值类型
     * @return 匹配值，不存在时返回 {@code null}
     */
    public static <T extends DictValue> T find(Class<T> enumType, String type, String code) {
        return findInternal(enumType, requireText(type, "type"), requireText(code, "code"));
    }

    /**
     * 将枚举声明顺序复制为不可变字典元素列表。
     *
     * @param enumType 枚举类型
     * @param <T>      枚举值类型
     * @return 不可变字典元素列表
     */
    public static <T extends DictValue> List<DictElement> elements(Class<T> enumType) {
        return Arrays.stream(constants(enumType)).map(DictElement::from).toList();
    }

    private static <T extends DictValue> T findInternal(Class<T> enumType, String type, String code) {
        return Arrays.stream(constants(enumType))
                .filter(value -> code.equals(value.getDictCode()))
                .filter(value -> type == null || type.equals(value.getDictType()))
                .findFirst()
                .orElse(null);
    }

    private static <T extends DictValue> T[] constants(Class<T> enumType) {
        Objects.requireNonNull(enumType, "enumType");
        T[] constants = enumType.getEnumConstants();
        if (constants == null) {
            throw new IllegalArgumentException(enumType.getName() + " is not an enum type");
        }
        return constants;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
