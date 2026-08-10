package com.ss.zlm4j.config;

import org.springframework.core.env.EnumerablePropertySource;

import java.util.Map;

/**
 * ini 文件属性源，为 ini 键统一附加前缀。
 *
 * <p>迁移自 honeybee 的 {@code HoneybeeIniPropertySource}，去掉 hutool 依赖。</p>
 */
public class SimpleSecretIniPropertySource extends EnumerablePropertySource<Map<String, String>> {
    private final String prefix;

    public SimpleSecretIniPropertySource(String prefix, String name, Map<String, String> source) {
        super(name, source);
        this.prefix = prefix;
    }

    @Override
    public String[] getPropertyNames() {
        boolean hasPrefix = prefix != null && !prefix.isBlank();
        return source.keySet().stream()
                .map(key -> hasPrefix ? prefix + "." + key : key)
                .toArray(String[]::new);
    }

    @Override
    public Object getProperty(String name) {
        if (prefix != null && !prefix.isBlank() && name.startsWith(prefix + ".")) {
            name = name.substring((prefix + ".").length());
        }
        return source.get(name);
    }
}
