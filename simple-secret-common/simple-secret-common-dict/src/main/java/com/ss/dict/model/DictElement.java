package com.ss.dict.model;

import java.util.Objects;

/**
 * 不可变字典元素快照。
 *
 * @param scope     可见范围
 * @param scopeCode 范围编码
 * @param code      字典编码
 * @param label     展示标签
 * @param type      字典类型
 */
public record DictElement(DictScope scope, String scopeCode, String code, String label, String type)
        implements DictValue {

    /**
     * 校验并构造不可变字典元素。
     *
     * @param scope 字典作用域
     * @param scopeCode 字典作用域编码
     * @param code 业务编码
     * @param label 显示标签
     * @param type 目标类型
     */
    public DictElement {
        scope = Objects.requireNonNull(scope, "scope");
        scopeCode = requireText(scopeCode, "scopeCode");
        code = requireText(code, "code");
        label = requireText(label, "label");
        type = requireText(type, "type");
    }

    /**
     * 从业务字典值复制快照。
     *
     * @param value 业务字典值
     * @return 不可变快照
     */
    public static DictElement from(DictValue value) {
        Objects.requireNonNull(value, "value");
        return new DictElement(value.getDictScope(), value.getDictScopeCode(),
                value.getDictCode(), value.getDictLabel(), value.getDictType());
    }

    @Override
    public String getDictCode() {
        return code;
    }

    @Override
    public String getDictLabel() {
        return label;
    }

    @Override
    public String getDictType() {
        return type;
    }

    @Override
    public DictScope getDictScope() {
        return scope;
    }

    @Override
    public String getDictScopeCode() {
        return scopeCode;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
