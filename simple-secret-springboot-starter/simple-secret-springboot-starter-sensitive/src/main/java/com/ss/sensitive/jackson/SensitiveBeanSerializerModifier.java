package com.ss.sensitive.jackson;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.ss.sensitive.annotation.Sensitive;
import com.ss.sensitive.core.SensitiveService;

import java.util.List;
import java.util.Objects;

/** 为带 {@link Sensitive} 的字符串属性安装字段级序列化器。 */
final class SensitiveBeanSerializerModifier extends BeanSerializerModifier {
    private final SensitiveService service;

    SensitiveBeanSerializerModifier(SensitiveService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @Override
    public List<BeanPropertyWriter> changeProperties(
            SerializationConfig config,
            BeanDescription beanDescription,
            List<BeanPropertyWriter> beanProperties) {
        for (BeanPropertyWriter property : beanProperties) {
            Sensitive sensitive = property.getAnnotation(Sensitive.class);
            if (sensitive != null && property.getType().getRawClass() == String.class) {
                assignSerializer(property, new SensitiveStringSerializer(
                        sensitive.strategy(),
                        sensitive.roleKey(),
                        sensitive.perms(),
                        service));
            }
        }
        return beanProperties;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assignSerializer(
            BeanPropertyWriter property,
            SensitiveStringSerializer serializer) {
        property.assignSerializer((JsonSerializer) serializer);
    }
}
