package com.ss.sensitive.jackson;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.ss.sensitive.core.SensitiveService;

import java.util.Objects;

/** 可注册到任意 Jackson ObjectMapper 的 Simple Secret 字段脱敏模块。 */
public final class SimpleSecretSensitiveModule extends SimpleModule {

    /**
     * 创建字段脱敏模块。
     *
     * @param sensitiveService 脱敏决策服务
     */
    public SimpleSecretSensitiveModule(SensitiveService sensitiveService) {
        super("simple-secret-sensitive");
        setSerializerModifier(new SensitiveBeanSerializerModifier(
                Objects.requireNonNull(sensitiveService, "sensitiveService must not be null")));
    }
}
