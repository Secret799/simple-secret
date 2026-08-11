package com.ss.dict;

import com.ss.common.toolbox.cache.ExpiringCache;
import com.ss.dict.model.DictElement;
import com.ss.dict.model.DictValue;
import com.ss.dict.spi.DictSource;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 显式注册、查询并缓存字典数据的实例级注册表。
 *
 * <p>注册表不扫描 Spring 容器，也不按字符串加载类。消费者负责注册实际使用的数据源。</p>
 */
public final class DictionaryRegistry implements AutoCloseable {
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(30);

    private final ConcurrentMap<String, DictSource> sources = new ConcurrentHashMap<>();
    private final ExpiringCache<String, List<DictElement>> cache;

    /** 使用 30 秒默认缓存时间创建注册表。 */
    public DictionaryRegistry() {
        this(DEFAULT_TTL);
    }

    /**
     * 使用指定默认缓存时间创建注册表。
     *
     * @param defaultTtl 默认缓存时间
     */
    public DictionaryRegistry(Duration defaultTtl) {
        this.cache = new ExpiringCache<>(Objects.requireNonNull(defaultTtl, "defaultTtl"));
    }

    /**
     * 注册字典数据源。
     *
     * @param key    字典 key，首尾空白会被移除
     * @param source 数据源
     * @return 当前注册表
     * @throws IllegalStateException key 已注册时抛出
     */
    public DictionaryRegistry register(String key, DictSource source) {
        String normalizedKey = normalizeKey(key);
        DictSource existing = sources.putIfAbsent(normalizedKey,
                Objects.requireNonNull(source, "source"));
        if (existing != null) {
            throw new IllegalStateException("Dictionary source already registered: " + normalizedKey);
        }
        return this;
    }

    /**
     * 将实现 {@link DictValue} 的枚举注册为字典数据源。
     *
     * @param key      字典 key
     * @param enumType 枚举类型
     * @param <T>      枚举值类型
     * @return 当前注册表
     */
    public <T extends DictValue> DictionaryRegistry registerEnum(String key, Class<T> enumType) {
        Objects.requireNonNull(enumType, "enumType");
        List<DictElement> elements = DictEnums.elements(enumType);
        return register(key, () -> elements);
    }

    /**
     * 绕过缓存实时查询字典。
     *
     * @param key 字典 key
     * @return 不可变字典快照
     */
    public List<DictElement> query(String key) {
        String normalizedKey = normalizeKey(key);
        DictSource source = sources.get(normalizedKey);
        if (source == null) {
            throw new IllegalArgumentException("No dictionary source registered: " + normalizedKey);
        }
        List<? extends DictValue> values = Objects.requireNonNull(source.load(),
                "Dictionary source returned null: " + normalizedKey);
        return values.stream().map(DictElement::from).toList();
    }

    /**
     * 绕过缓存并按类型查询字典。
     *
     * @param key  字典 key
     * @param type 字典类型
     * @return 不可变字典快照
     */
    public List<DictElement> query(String key, String type) {
        String requiredType = requireText(type, "type");
        return query(key).stream()
                .filter(value -> requiredType.equals(value.type()))
                .toList();
    }

    /**
     * 使用默认 TTL 查询字典。
     *
     * @param key 字典 key
     * @return 不可变字典快照
     */
    public List<DictElement> queryCached(String key) {
        String normalizedKey = normalizeKey(key);
        return cache.computeIfAbsent(normalizedKey, this::query);
    }

    /**
     * 使用本次调用指定的 TTL 查询字典。
     *
     * @param key 字典 key
     * @param ttl 缓存时间
     * @return 不可变字典快照
     */
    public List<DictElement> queryCached(String key, Duration ttl) {
        String normalizedKey = normalizeKey(key);
        return cache.computeIfAbsent(normalizedKey, Objects.requireNonNull(ttl, "ttl"), this::query);
    }

    /**
     * 使用默认 TTL 并按类型查询字典。
     *
     * @param key  字典 key
     * @param type 字典类型
     * @return 不可变字典快照
     */
    public List<DictElement> queryCached(String key, String type) {
        String requiredType = requireText(type, "type");
        return queryCached(key).stream()
                .filter(value -> requiredType.equals(value.type()))
                .toList();
    }

    /**
     * 使用缓存按编码查找字典元素。
     *
     * @param key  字典 key
     * @param code 字典编码
     * @return 匹配元素，不存在时返回 {@code null}
     */
    public DictElement find(String key, String code) {
        String requiredCode = requireText(code, "code");
        return queryCached(key).stream()
                .filter(value -> requiredCode.equals(value.code()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 使用缓存按类型和编码查找字典元素。
     *
     * @param key  字典 key
     * @param type 字典类型
     * @param code 字典编码
     * @return 匹配元素，不存在时返回 {@code null}
     */
    public DictElement find(String key, String type, String code) {
        String requiredCode = requireText(code, "code");
        return queryCached(key, type).stream()
                .filter(value -> requiredCode.equals(value.code()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 绕过缓存批量查询多个字典 key。
     *
     * @param keys 字典 key 集合
     * @return 保持输入顺序的不可变结果映射
     */
    public Map<String, List<DictElement>> queryAll(Collection<String> keys) {
        Objects.requireNonNull(keys, "keys");
        Map<String, List<DictElement>> result = new LinkedHashMap<>();
        keys.forEach(key -> {
            String normalizedKey = normalizeKey(key);
            result.put(normalizedKey, query(normalizedKey));
        });
        return Collections.unmodifiableMap(result);
    }

    /**
     * 使用默认 TTL 批量查询多个字典 key。
     *
     * @param keys 字典 key 集合
     * @return 保持输入顺序的不可变结果映射
     */
    public Map<String, List<DictElement>> queryAllCached(Collection<String> keys) {
        Objects.requireNonNull(keys, "keys");
        Map<String, List<DictElement>> result = new LinkedHashMap<>();
        keys.forEach(key -> {
            String normalizedKey = normalizeKey(key);
            result.put(normalizedKey, queryCached(normalizedKey));
        });
        return Collections.unmodifiableMap(result);
    }

    /**
     * 使一个字典 key 的缓存失效。
     *
     * @param key 字典 key
     */
    public void invalidate(String key) {
        cache.remove(normalizeKey(key));
    }

    /** 清空全部字典缓存，不移除已注册数据源。 */
    public void clearCache() {
        cache.clear();
    }

    /** 关闭缓存可选清理资源。 */
    @Override
    public void close() {
        cache.close();
    }

    private static String normalizeKey(String key) {
        return requireText(key, "key").trim();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
