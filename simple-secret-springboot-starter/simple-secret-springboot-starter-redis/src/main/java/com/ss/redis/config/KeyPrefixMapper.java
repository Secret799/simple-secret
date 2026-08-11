package com.ss.redis.config;

import org.redisson.api.NameMapper;

import java.util.Objects;

/**
 * 为 Redisson 对象名称添加稳定前缀。
 *
 * <p>映射是幂等的，已经带有同一前缀的名称不会被重复改写。</p>
 */
public final class KeyPrefixMapper implements NameMapper {

    private final String prefix;

    /**
     * 创建名称映射器。
     *
     * @param prefix 名称前缀；空白字符串表示不改写名称
     */
    public KeyPrefixMapper(String prefix) {
        Objects.requireNonNull(prefix, "prefix must not be null");
        String normalized = prefix.trim();
        while (normalized.endsWith(":")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.prefix = normalized.isBlank() ? "" : normalized + ":";
    }

    @Override
    public String map(String name) {
        String validatedName = requireName(name);
        if (prefix.isEmpty() || validatedName.startsWith(prefix)) {
            return validatedName;
        }
        return prefix + validatedName;
    }

    @Override
    public String unmap(String name) {
        String validatedName = requireName(name);
        if (!prefix.isEmpty() && validatedName.startsWith(prefix)) {
            return validatedName.substring(prefix.length());
        }
        return validatedName;
    }

    private static String requireName(String name) {
        Objects.requireNonNull(name, "name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name;
    }
}
