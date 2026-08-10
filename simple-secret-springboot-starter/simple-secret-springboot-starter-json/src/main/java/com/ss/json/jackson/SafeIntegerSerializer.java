package com.ss.json.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigInteger;

/**
 * 将 JavaScript 安全范围外的整数序列化为字符串，防止前端解析时丢失精度。
 */
public final class SafeIntegerSerializer extends JsonSerializer<Number> {
    /** JavaScript 最小安全整数。 */
    public static final BigInteger MIN_SAFE_INTEGER = new BigInteger("-9007199254740991");
    /** JavaScript 最大安全整数。 */
    public static final BigInteger MAX_SAFE_INTEGER = new BigInteger("9007199254740991");

    /**
     * 按 JavaScript 安全整数边界写出数字或字符串。
     *
     * @param value       待序列化整数
     * @param generator   JSON 输出生成器
     * @param serializers 序列化上下文
     * @throws IOException 写出失败
     */
    @Override
    public void serialize(Number value, JsonGenerator generator, SerializerProvider serializers)
            throws IOException {
        BigInteger integer = value instanceof BigInteger bigInteger
                ? bigInteger : BigInteger.valueOf(value.longValue());
        if (integer.compareTo(MIN_SAFE_INTEGER) >= 0 && integer.compareTo(MAX_SAFE_INTEGER) <= 0) {
            generator.writeNumber(integer);
        } else {
            generator.writeString(integer.toString());
        }
    }
}
