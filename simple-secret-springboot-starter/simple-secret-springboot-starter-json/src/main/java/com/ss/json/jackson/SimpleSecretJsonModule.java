package com.ss.json.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Simple Secret 的统一 Jackson 数值与日期时间规则。
 */
public final class SimpleSecretJsonModule extends SimpleModule {
    /**
     * 注册安全整数和精确小数的序列化规则。
     */
    public SimpleSecretJsonModule() {
        super("simple-secret-json");
        SafeIntegerSerializer safeIntegerSerializer = new SafeIntegerSerializer();
        addSerializer(Long.class, safeIntegerSerializer);
        addSerializer(Long.TYPE, safeIntegerSerializer);
        addSerializer(BigInteger.class, safeIntegerSerializer);
        addSerializer(BigDecimal.class, ToStringSerializer.instance);
    }
}
