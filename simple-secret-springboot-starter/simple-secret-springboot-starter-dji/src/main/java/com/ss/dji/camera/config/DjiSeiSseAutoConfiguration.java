package com.ss.dji.camera.config;

import com.ss.dji.camera.web.DjiSeiSseController;
import com.ss.dji.camera.web.DjiSeiSseService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Clock;

/** Servlet SSE endpoint configuration for DJI SEI diagnostics. */
@AutoConfiguration(after = DjiCameraAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(SseEmitter.class)
@ConditionalOnProperty(prefix = "simple-secret.dji-sei", name = "enabled", havingValue = "true")
public class DjiSeiSseAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DjiSeiSseService djiSeiSseService(DjiSeiProperties properties, Clock clock) {
        return new DjiSeiSseService(properties, clock);
    }

    @Bean
    @ConditionalOnMissingBean
    public DjiSeiSseController djiSeiSseController(DjiSeiSseService sseService) {
        return new DjiSeiSseController(sseService);
    }
}
