package com.ss.zlm4j.helper;

import com.aizuda.zlm4j.core.ZLMApi;
import com.aizuda.zlm4j.structure.MK_INI;
import com.ss.zlm4j.config.properties.ZlmMediaProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ZlmMediaConfiguratorTest {

    @Test
    void writesConfiguredListenAddressToNativeConfiguration() {
        Map<String, String> values = new HashMap<>();
        ZLMApi api = (ZLMApi) Proxy.newProxyInstance(
                ZLMApi.class.getClassLoader(),
                new Class<?>[]{ZLMApi.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("mk_ini_set_option")) {
                        values.put((String) args[1], (String) args[2]);
                    }
                    return null;
                });
        ZlmMediaProperties properties = new ZlmMediaProperties();
        properties.setListenIp("192.0.2.10");

        ZlmMediaHelper.Configurator.setConfig(api, new MK_INI(), properties);

        assertThat(values).containsEntry("general.listen_ip", "192.0.2.10");
    }
}
