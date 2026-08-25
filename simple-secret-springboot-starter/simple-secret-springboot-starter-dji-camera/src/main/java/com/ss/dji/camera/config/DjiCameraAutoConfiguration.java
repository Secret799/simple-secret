package com.ss.dji.camera.config;

import com.ss.dji.camera.diagnostic.DjiSeiEventListener;
import com.ss.dji.camera.diagnostic.DjiSeiTrackCallback;
import com.ss.dji.camera.parser.H26xSeiParser;
import com.ss.dji.camera.web.DjiSeiController;
import com.ss.dji.camera.web.DjiSeiEventHub;
import com.ss.easymedia.config.SimpleSecretEasyMediaAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;
import java.util.List;

/**
 * 大疆 Camera 集成工具自动配置。
 */
@AutoConfiguration(after = SimpleSecretEasyMediaAutoConfiguration.class)
@EnableConfigurationProperties(DjiSeiProperties.class)
public class DjiCameraAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock djiSeiClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(DjiSeiTrackCallback.class)
    @ConditionalOnProperty(prefix = "simple-secret.dji-sei", name = "enabled", havingValue = "true")
    public DjiSeiTrackCallback djiSeiTrackCallback(DjiSeiProperties properties, Clock clock,
                                                    List<DjiSeiEventListener> eventListeners) {
        return new DjiSeiTrackCallback(new H26xSeiParser(), properties, clock, eventListeners);
    }

    /** Servlet 应用中的 SEI 实时诊断接口。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(SseEmitter.class)
    @ConditionalOnProperty(prefix = "simple-secret.dji-sei", name = "enabled", havingValue = "true")
    static class DjiSeiWebConfiguration {

        @Bean
        @ConditionalOnMissingBean(DjiSeiEventHub.class)
        DjiSeiEventHub djiSeiEventHub(Clock clock, DjiSeiProperties properties) {
            return new DjiSeiEventHub(clock, properties);
        }

        @Bean
        @ConditionalOnMissingBean(DjiSeiController.class)
        DjiSeiController djiSeiController(DjiSeiEventHub eventHub) {
            return new DjiSeiController(eventHub);
        }
    }
}
