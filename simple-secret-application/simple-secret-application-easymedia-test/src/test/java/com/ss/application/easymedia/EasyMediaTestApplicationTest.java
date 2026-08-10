package com.ss.application.easymedia;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = EasyMediaTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EasyMediaTestApplicationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void startsWithoutNativeMediaLibraryByDefault() {
        assertThat(environment.getProperty("simple-secret.zlm4j.enabled", Boolean.class))
                .isFalse();
        assertThat(environment.getProperty("simple-secret.easymedia.enabled", Boolean.class))
                .isFalse();
        assertThat(applicationContext.containsBean("zlmMediaContext")).isFalse();
        assertThat(applicationContext.containsBean("zlm4jWebRTCController")).isFalse();
    }
}
