package com.ss.encrypt.config;

import com.ss.encrypt.mybatis.EncryptedObjectProcessor;
import com.ss.encrypt.mybatis.MybatisDecryptInterceptor;
import com.ss.encrypt.mybatis.MybatisEncryptInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisEncryptAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SimpleSecretEncryptAutoConfiguration.class,
                    MybatisEncryptAutoConfiguration.class));

    @Test
    void shouldRegisterInterceptorsOnlyWhenBothSwitchesAreEnabled() {
        runner.withPropertyValues("simple-secret.encrypt.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(EncryptedObjectProcessor.class);
                    assertThat(context).doesNotHaveBean(MybatisEncryptInterceptor.class);
                });

        runner.withPropertyValues(
                        "simple-secret.encrypt.enabled=true",
                        "simple-secret.encrypt.mybatis.enabled=true",
                        "simple-secret.encrypt.keys.default.secret-key="
                                + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
                .run(context -> {
                    assertThat(context).hasSingleBean(EncryptedObjectProcessor.class);
                    assertThat(context).hasSingleBean(MybatisEncryptInterceptor.class);
                    assertThat(context).hasSingleBean(MybatisDecryptInterceptor.class);
                });
    }
}
