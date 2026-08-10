package com.ss.zlm4j.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ini 文件属性源加载器，按文件名中的 {@code __} 分隔符推导属性前缀。
 *
 * <p>例如 {@code simple-secret__zlm4j-default__conf.ini} 推导前缀
 * {@code simple-secret.zlm4j-default}。迁移自 honeybee 的 {@code HoneybeeIniPropertySourceLoader}。</p>
 */
public class SimpleSecretIniPropertySourceLoader implements PropertySourceLoader {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleSecretIniPropertySourceLoader.class);

    @Override
    public String[] getFileExtensions() {
        return new String[]{"ini"};
    }

    @Override
    public List<PropertySource<?>> load(String name, Resource resource) {
        try {
            // 如果文件名中存在__那么进行分割，生成前缀
            String prefix = "";
            String filename = resource.getFilename();
            if (filename != null && !filename.isBlank()) {
                String[] splitResult = filename.split("__");
                if (splitResult.length > 1) {
                    prefix = Arrays.stream(splitResult, 0, splitResult.length - 1)
                            .collect(Collectors.joining("."));
                }
            }
            return List.of(new SimpleSecretIniPropertySource(prefix, name, parse(resource)));
        } catch (Exception e) {
            LOG.error("解析ini文件失败,忽略该文件加载:{}", resource.getFilename(), e);
            return Collections.emptyList();
        }
    }

    private static Map<String, String> parse(Resource resource) throws IOException {
        Map<String, String> properties = new LinkedHashMap<>();
        String section = "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (lineNumber == 1 && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
                    continue;
                }
                if (trimmed.startsWith("[")) {
                    if (!trimmed.endsWith("]") || trimmed.length() < 3) {
                        throw new IOException("无效的 INI section，行号: " + lineNumber);
                    }
                    section = trimmed.substring(1, trimmed.length() - 1).trim();
                    if (section.isEmpty()) {
                        throw new IOException("INI section 不能为空，行号: " + lineNumber);
                    }
                    continue;
                }

                int delimiter = delimiterIndex(trimmed);
                if (delimiter <= 0) {
                    throw new IOException("无效的 INI 属性，行号: " + lineNumber);
                }
                String key = trimmed.substring(0, delimiter).trim();
                if (key.isEmpty()) {
                    throw new IOException("INI 属性名不能为空，行号: " + lineNumber);
                }
                String value = trimmed.substring(delimiter + 1).trim();
                properties.put(section.isEmpty() ? key : section + "." + key, value);
            }
        }
        return properties;
    }

    private static int delimiterIndex(String line) {
        int equals = line.indexOf('=');
        int colon = line.indexOf(':');
        if (equals < 0) {
            return colon;
        }
        if (colon < 0) {
            return equals;
        }
        return Math.min(equals, colon);
    }
}
