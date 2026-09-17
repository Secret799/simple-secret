package com.ss.gb28181.zlm.autoconfigure;

import com.ss.gb28181.Gb28181Server;
import com.ss.gb28181.autoconfigure.SimpleSecretGb28181AutoConfiguration;
import com.ss.gb28181.zlm.GbZlmPlayer;
import com.ss.zlm4j.config.SimpleSecretZlmAutoConfiguration;
import com.ss.zlm4j.service.IZlmMediaService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = {SimpleSecretGb28181AutoConfiguration.class, SimpleSecretZlmAutoConfiguration.class})
@ConditionalOnClass({Gb28181Server.class, IZlmMediaService.class})
@ConditionalOnProperty(prefix = "simple-secret.gb28181-zlm", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(GbZlmProperties.class)
public class SimpleSecretGbZlmAutoConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(GbZlmPlayer.class)
    GbZlmPlayer gbZlmPlayer(Gb28181Server server, IZlmMediaService media, GbZlmProperties properties) {
        return new GbZlmPlayer(server, media, properties.getAdvertisedAddress(),
                properties.getTransport(), properties.getMaxSessions());
    }
}
