package com.ss.sensitive.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import com.ss.sensitive.core.SensitiveService;
import com.ss.sensitive.core.SensitiveStrategy;

import java.io.IOException;
import java.util.Objects;

/** 使用不可变字段配置执行字符串脱敏。 */
final class SensitiveStringSerializer extends StdScalarSerializer<String> {
    private final SensitiveStrategy strategy;
    private final String roleKey;
    private final String perms;
    private final SensitiveService service;

    SensitiveStringSerializer(
            SensitiveStrategy strategy,
            String roleKey,
            String perms,
            SensitiveService service) {
        super(String.class);
        this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
        this.roleKey = Objects.requireNonNull(roleKey, "roleKey must not be null");
        this.perms = Objects.requireNonNull(perms, "perms must not be null");
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @Override
    public void serialize(
            String value,
            JsonGenerator generator,
            SerializerProvider provider) throws IOException {
        generator.writeString(shouldMask() ? strategy.mask(value) : value);
    }

    private boolean shouldMask() {
        try {
            return service.isSensitive(roleKey, perms);
        } catch (RuntimeException exception) {
            return true;
        }
    }
}
