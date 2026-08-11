package com.ss.dict.service;

import com.ss.dict.model.DictElement;
import com.ss.dict.model.DictValue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 业务字典服务契约。
 *
 * <p>实现方只需提供指定类型的全部字典值，接口默认完成存在性判断和双向批量转换。</p>
 */
@FunctionalInterface
public interface DictService {

    /** 默认多值分隔符。 */
    String DEFAULT_SEPARATOR = ",";

    /**
     * 返回指定字典类型的全部值。
     *
     * @param dictType 字典类型
     * @return 字典值集合，不应返回 {@code null}
     */
    List<? extends DictValue> getAllDictByDictType(String dictType);

    /**
     * 判断字典类型是否包含至少一个值。
     *
     * @param dictType 字典类型
     * @return 包含值时返回 {@code true}
     */
    default boolean existDictType(String dictType) {
        return !snapshot(dictType).isEmpty();
    }

    /**
     * 使用逗号分隔符将编码转换为标签。
     *
     * @param dictType 字典类型
     * @param dictCode 一个或多个字典编码
     * @return 转换结果，未知编码保持原值
     */
    default String getDictLabel(String dictType, String dictCode) {
        return getDictLabel(dictType, dictCode, DEFAULT_SEPARATOR);
    }

    /**
     * 使用指定分隔符将编码转换为标签。
     *
     * @param dictType 字典类型
     * @param dictCode 一个或多个字典编码
     * @param separator 字面量分隔符
     * @return 转换结果，未知编码保持原值
     */
    default String getDictLabel(String dictType, String dictCode, String separator) {
        List<DictElement> values = snapshot(dictType);
        return convert(values, dictCode, separator, DictElement::code, DictElement::label);
    }

    /**
     * 使用逗号分隔符将标签转换为编码。
     *
     * @param dictType 字典类型
     * @param dictLabel 一个或多个字典标签
     * @return 转换结果，未知标签保持原值
     */
    default String getDictCode(String dictType, String dictLabel) {
        return getDictCode(dictType, dictLabel, DEFAULT_SEPARATOR);
    }

    /**
     * 使用指定分隔符将标签转换为编码。
     *
     * @param dictType 字典类型
     * @param dictLabel 一个或多个字典标签
     * @param separator 字面量分隔符
     * @return 转换结果，未知标签保持原值
     */
    default String getDictCode(String dictType, String dictLabel, String separator) {
        List<DictElement> values = snapshot(dictType);
        return convert(values, dictLabel, separator, DictElement::label, DictElement::code);
    }

    private List<DictElement> snapshot(String dictType) {
        String requiredType = requireText(dictType, "dictType");
        List<? extends DictValue> values = Objects.requireNonNull(
                getAllDictByDictType(requiredType), "dictionary values");
        return values.stream().map(DictElement::from).toList();
    }

    private static String convert(List<DictElement> values, String input, String separator,
                                  Function<DictElement, String> source,
                                  Function<DictElement, String> target) {
        Objects.requireNonNull(input, "input");
        String requiredSeparator = requireText(separator, "separator");
        Map<String, String> mappings = new LinkedHashMap<>();
        values.forEach(value -> mappings.putIfAbsent(source.apply(value), target.apply(value)));
        return Arrays.stream(input.split(Pattern.quote(requiredSeparator), -1))
                .map(value -> mappings.getOrDefault(value, value))
                .collect(java.util.stream.Collectors.joining(requiredSeparator));
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
