package com.ss.zlm4j.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SimpleSecretIniPropertySourceLoader} 单元测试。
 *
 * <p>验证打包的 ini 文件能被加载，并按文件名推导出 {@code simple-secret.zlm4j-default} 前缀。</p>
 */
class SimpleSecretIniPropertySourceLoaderTest {

    private final SimpleSecretIniPropertySourceLoader loader = new SimpleSecretIniPropertySourceLoader();

    @Test
    void supportsIniExtension() {
        assertThat(loader.getFileExtensions()).containsExactly("ini");
    }

    @Test
    void doesNotRequireCommonsConfigurationAtRuntime() {
        assertThatThrownBy(() -> Class.forName(
                "org.apache.commons.configuration2.INIConfiguration"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void loadsIniWithDerivedPrefix() {
        Resource resource = new ClassPathResource("simple-secret__zlm4j-default__conf.ini");
        assertThat(resource.exists()).isTrue();

        List<org.springframework.core.env.PropertySource<?>> sources =
                loader.load("test", resource);

        assertThat(sources).hasSize(1);
        assertThat(sources.get(0)).isInstanceOf(SimpleSecretIniPropertySource.class);
        SimpleSecretIniPropertySource source = (SimpleSecretIniPropertySource) sources.get(0);

        // 前缀由文件名 honeybee__zlm4j-default__conf.ini 推导为 simple-secret.zlm4j-default
        assertThat(source.getProperty("simple-secret.zlm4j-default.protocol.enable_ts")).isEqualTo("1");
        assertThat(source.getProperty("simple-secret.zlm4j-default.protocol.enable_rtsp")).isEqualTo("1");
        assertThat(source.getProperty("simple-secret.zlm4j-default.general.mediaServerId")).isEqualTo("your_server_id");
    }

    @Test
    void exposesEveryPropertyNameWhenPrefixIsEmpty() {
        SimpleSecretIniPropertySource source = new SimpleSecretIniPropertySource(
                "", "test", Map.of(
                        "protocol.enable_ts", "1",
                        "protocol.enable_rtsp", "1"));

        assertThat(source.getPropertyNames())
                .containsExactlyInAnyOrder("protocol.enable_ts", "protocol.enable_rtsp");
        assertThat(source.getProperty("protocol.enable_ts")).isEqualTo("1");
    }

    @Test
    void exposesAndReadsEveryPropertyWhenPrefixIsNull() {
        SimpleSecretIniPropertySource source = new SimpleSecretIniPropertySource(
                null, "test", Map.of("protocol.enable_ts", "1"));

        assertThat(source.getPropertyNames()).containsExactly("protocol.enable_ts");
        assertThat(source.getProperty("protocol.enable_ts")).isEqualTo("1");
    }
}
