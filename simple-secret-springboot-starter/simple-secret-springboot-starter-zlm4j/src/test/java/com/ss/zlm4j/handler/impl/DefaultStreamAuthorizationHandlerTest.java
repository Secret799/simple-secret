package com.ss.zlm4j.handler.impl;

import com.aizuda.zlm4j.core.ZLMApi;
import com.aizuda.zlm4j.structure.MK_AUTH_INVOKER;
import com.aizuda.zlm4j.structure.MK_MEDIA_INFO;
import com.aizuda.zlm4j.structure.MK_PUBLISH_AUTH_INVOKER;
import com.aizuda.zlm4j.structure.MK_SOCK_INFO;
import com.ss.zlm4j.config.properties.ZlmMediaProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultStreamAuthorizationHandlerTest {

    @Test
    void defaultPlayHandlerDeniesAnonymousAccess() {
        AtomicReference<String> denialReason = new AtomicReference<>();
        ZLMApi api = recordingApi(denialReason, "mk_auth_invoker_do");

        new DefaultStreamPlayHandler(new ZlmMediaProperties(), api)
                .handle(new MK_MEDIA_INFO(), new MK_AUTH_INVOKER(), new MK_SOCK_INFO());

        assertThat(denialReason.get()).isEqualTo("ANONYMOUS_PLAY_DISABLED");
    }

    @Test
    void playHandlerAllowsAnonymousAccessOnlyWhenExplicitlyEnabled() {
        AtomicReference<String> denialReason = new AtomicReference<>();
        ZlmMediaProperties properties = new ZlmMediaProperties();
        properties.setAllowAnonymousPlay(true);

        new DefaultStreamPlayHandler(properties, recordingApi(denialReason, "mk_auth_invoker_do"))
                .handle(new MK_MEDIA_INFO(), new MK_AUTH_INVOKER(), new MK_SOCK_INFO());

        assertThat(denialReason.get()).isEmpty();
    }

    @Test
    void defaultPublishHandlerDeniesAnonymousAccess() {
        AtomicReference<String> denialReason = new AtomicReference<>();
        ZLMApi api = recordingApi(denialReason, "mk_publish_auth_invoker_do2");

        new DefaultStreamPublishHandler(new ZlmMediaProperties(), api)
                .handle(new MK_MEDIA_INFO(), new MK_PUBLISH_AUTH_INVOKER(), new MK_SOCK_INFO());

        assertThat(denialReason.get()).isEqualTo("ANONYMOUS_PUBLISH_DISABLED");
    }

    private static ZLMApi recordingApi(AtomicReference<String> denialReason, String methodName) {
        return (ZLMApi) Proxy.newProxyInstance(
                ZLMApi.class.getClassLoader(),
                new Class<?>[]{ZLMApi.class},
                (proxy, method, args) -> {
                    if (method.getName().equals(methodName)) {
                        denialReason.set((String) args[1]);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
    }
}
