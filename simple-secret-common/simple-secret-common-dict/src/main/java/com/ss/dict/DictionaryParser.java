package com.ss.dict;

import com.ss.dict.annotation.DictField;
import com.ss.dict.exception.DictionaryMappingException;
import com.ss.dict.model.DictElement;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 将对象中 {@link DictField} 标记的编码翻译到对应展示字段。
 *
 * <p>解析器修改调用方对象并返回同一实例，不复制业务对象。</p>
 */
public final class DictionaryParser {
    private static final String DEFAULT_LABEL_SUFFIX = "DisplayLabel";

    private final DictionaryRegistry registry;
    private final ClassValue<List<FieldMapping>> mappings = new ClassValue<>() {
        @Override
        protected List<FieldMapping> computeValue(Class<?> type) {
            return inspect(type);
        }
    };

    /**
     * 创建字典对象解析器。
     *
     * @param registry 已配置的数据源注册表
     */
    public DictionaryParser(DictionaryRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * 使用缓存翻译一个对象。
     *
     * @param data 业务对象
     * @param <T>  对象类型
     * @return 原对象；输入为 {@code null} 时返回 {@code null}
     */
    public <T> T parse(T data) {
        return parse(data, true);
    }

    /**
     * 翻译一个对象。
     *
     * @param data     业务对象
     * @param useCache 是否使用注册表缓存
     * @param <T>      对象类型
     * @return 原对象；输入为 {@code null} 时返回 {@code null}
     */
    public <T> T parse(T data, boolean useCache) {
        if (data == null) {
            return null;
        }
        parseAll(List.of(data), useCache);
        return data;
    }

    /**
     * 使用缓存翻译列表中的对象。
     *
     * @param data 对象列表
     * @param <T>  对象类型
     * @return 原列表
     */
    public <T> List<T> parseAll(List<T> data) {
        return parseAll(data, true);
    }

    /**
     * 翻译列表中的对象。
     *
     * <p>同一次调用内，同一个 source key 最多加载一次，即使关闭全局缓存。</p>
     *
     * @param data     对象列表
     * @param useCache 是否使用注册表缓存
     * @param <T>      对象类型
     * @return 原列表
     */
    public <T> List<T> parseAll(List<T> data, boolean useCache) {
        Objects.requireNonNull(data, "data");
        Map<String, List<DictElement>> dictionaries = new HashMap<>();
        for (T item : data) {
            if (item == null) {
                continue;
            }
            for (FieldMapping mapping : mappings.get(item.getClass())) {
                Object rawValue = read(mapping.sourceField(), item);
                if (rawValue == null) {
                    continue;
                }
                List<DictElement> elements = dictionaries.computeIfAbsent(mapping.source(),
                        key -> useCache ? registry.queryCached(key) : registry.query(key));
                apply(item, rawValue, mapping, elements);
            }
        }
        return data;
    }

    private static void apply(Object target, Object rawValue, FieldMapping mapping,
                              List<DictElement> elements) {
        String type = mapping.fixedType();
        if (mapping.typeField() != null) {
            Object typeValue = read(mapping.typeField(), target);
            type = typeValue == null || String.valueOf(typeValue).isBlank()
                    ? null : String.valueOf(typeValue);
        }
        String rawCode = String.valueOf(rawValue);
        String label = mapping.multiple()
                ? translateMultiple(rawCode, type, mapping.separator(), elements)
                : translate(rawCode, type, elements);
        write(mapping.labelField(), target, label);
    }

    private static String translateMultiple(String rawCodes, String type, String separator,
                                            List<DictElement> elements) {
        return Arrays.stream(rawCodes.split(Pattern.quote(separator), -1))
                .map(code -> translate(code, type, elements))
                .collect(Collectors.joining(separator));
    }

    private static String translate(String code, String type, List<DictElement> elements) {
        return elements.stream()
                .filter(element -> code.equals(element.code()))
                .filter(element -> type == null || type.equals(element.type()))
                .map(DictElement::label)
                .findFirst()
                .orElse(code);
    }

    private static List<FieldMapping> inspect(Class<?> runtimeType) {
        List<FieldMapping> result = new ArrayList<>();
        for (Field field : fields(runtimeType)) {
            DictField annotation = field.getAnnotation(DictField.class);
            if (annotation != null) {
                result.add(mapping(runtimeType, field, annotation));
            }
        }
        return List.copyOf(result);
    }

    private static FieldMapping mapping(Class<?> runtimeType, Field sourceField,
                                        DictField annotation) {
        rejectStatic(sourceField, sourceField, "dictionary source field");
        String source = requireText(annotation.value(), "dictionary source", sourceField);
        String fixedType = annotation.type().trim();
        String typeFieldName = annotation.typeField().trim();
        if (!fixedType.isEmpty() && !typeFieldName.isEmpty()) {
            throw error(sourceField, "type and typeField cannot both be configured");
        }

        String labelFieldName = annotation.labelField().isBlank()
                ? sourceField.getName() + DEFAULT_LABEL_SUFFIX
                : annotation.labelField().trim();
        Field labelField = findField(runtimeType, labelFieldName);
        if (labelField == null) {
            throw error(sourceField, "label field not found: " + labelFieldName);
        }
        if (!labelField.getType().isAssignableFrom(String.class)) {
            throw error(sourceField, "label field cannot receive String: " + labelFieldName);
        }
        rejectStatic(labelField, sourceField, "label field");

        Field typeField = null;
        if (!typeFieldName.isEmpty()) {
            typeField = findField(runtimeType, typeFieldName);
            if (typeField == null) {
                throw error(sourceField, "type field not found: " + typeFieldName);
            }
            rejectStatic(typeField, sourceField, "type field");
            makeAccessible(typeField, sourceField);
        }
        if (annotation.multiple() && annotation.separator().isBlank()) {
            throw error(sourceField, "separator must not be blank for multiple values");
        }

        makeAccessible(sourceField, sourceField);
        makeAccessible(labelField, sourceField);
        return new FieldMapping(source, fixedType.isEmpty() ? null : fixedType, typeField,
                sourceField, labelField, annotation.multiple(), annotation.separator());
    }

    private static List<Field> fields(Class<?> type) {
        List<Field> result = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            result.addAll(List.of(current.getDeclaredFields()));
        }
        return result;
    }

    private static Field findField(Class<?> type, String name) {
        return fields(type).stream()
                .filter(field -> name.equals(field.getName()))
                .findFirst()
                .orElse(null);
    }

    private static void makeAccessible(Field field, Field annotatedField) {
        if (!field.trySetAccessible()) {
            throw error(annotatedField, "field is not accessible: " + field.getName());
        }
    }

    private static void rejectStatic(Field field, Field annotatedField, String role) {
        if (Modifier.isStatic(field.getModifiers())) {
            throw error(annotatedField, role + " must not be static: " + field.getName());
        }
    }

    private static Object read(Field field, Object target) {
        try {
            return field.get(target);
        } catch (IllegalAccessException exception) {
            throw new DictionaryMappingException(
                    "Cannot read dictionary field " + field.getDeclaringClass().getName()
                            + "." + field.getName(), exception);
        }
    }

    private static void write(Field field, Object target, String value) {
        try {
            field.set(target, value);
        } catch (IllegalAccessException | IllegalArgumentException exception) {
            throw new DictionaryMappingException(
                    "Cannot write dictionary label field " + field.getDeclaringClass().getName()
                            + "." + field.getName(), exception);
        }
    }

    private static String requireText(String value, String name, Field field) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw error(field, name + " must not be blank");
        }
        return normalized;
    }

    private static DictionaryMappingException error(Field field, String message) {
        return new DictionaryMappingException(field.getDeclaringClass().getName() + "."
                + field.getName() + ": " + message);
    }

    private record FieldMapping(String source, String fixedType, Field typeField,
                                Field sourceField, Field labelField,
                                boolean multiple, String separator) {
    }
}
