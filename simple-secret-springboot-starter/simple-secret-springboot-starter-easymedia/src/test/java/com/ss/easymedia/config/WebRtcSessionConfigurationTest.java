package com.ss.easymedia.config;

import com.aizuda.zlm4j.core.ZLMApi;
import com.ss.easymedia.config.properties.WebRtcProperties;
import com.ss.easymedia.webrtc.client.LocalZlmWebRtcSignalingClient;
import com.ss.easymedia.webrtc.client.ZlmWebRtcSignalingClient;
import com.ss.easymedia.webrtc.client.ZlmWebRtcUriPolicy;
import com.ss.easymedia.webrtc.id.WebRtcSessionIdGenerator;
import com.ss.easymedia.webrtc.metrics.MicrometerWebRtcSessionMetrics;
import com.ss.easymedia.webrtc.metrics.NoopWebRtcSessionMetrics;
import com.ss.easymedia.webrtc.metrics.WebRtcSessionMetrics;
import com.ss.easymedia.webrtc.repository.InMemoryWebRtcSessionRepository;
import com.ss.easymedia.webrtc.repository.RedissonWebRtcSessionRepository;
import com.ss.easymedia.webrtc.repository.WebRtcSessionRepository;
import com.ss.easymedia.webrtc.security.InMemoryWebRtcRateLimiter;
import com.ss.easymedia.webrtc.security.RedisWebRtcRateLimiter;
import com.ss.easymedia.webrtc.security.WebRtcAccessPolicy;
import com.ss.easymedia.webrtc.security.WebRtcIdentityProvider;
import com.ss.easymedia.webrtc.security.WebRtcRateLimiter;
import com.ss.easymedia.webrtc.service.WebRtcSessionCleanupJob;
import com.ss.easymedia.webrtc.service.WebRtcSessionService;
import com.ss.zlm4j.context.ZlmMediaContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebRtcSessionConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(WebRtcSessionConfiguration.class)
            .withPropertyValues(
                    "simple-secret.easymedia.webrtc.enabled=true",
                    "simple-secret.easymedia.webrtc.connect-timeout=3s",
                    "simple-secret.easymedia.webrtc.request-timeout=7s");

    @Test
    void shouldCreateAllDefaultWebRtcBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(WebRtcSessionIdGenerator.class);
            assertThat(context).hasSingleBean(ZlmWebRtcUriPolicy.class);
            assertThat(context).hasSingleBean(ZlmWebRtcSignalingClient.class);
            assertThat(context).hasSingleBean(WebRtcSessionRepository.class);
            assertThat(context).hasSingleBean(WebRtcIdentityProvider.class);
            assertThat(context).hasSingleBean(WebRtcAccessPolicy.class);
            assertThat(context).hasSingleBean(WebRtcRateLimiter.class);
            assertThat(context).hasSingleBean(WebRtcSessionService.class);
            assertThat(context).hasSingleBean(WebRtcSessionCleanupJob.class);
            assertThat(context).hasSingleBean(NoopWebRtcSessionMetrics.class);
            assertThat(context.getBean(WebRtcSessionRepository.class))
                    .isInstanceOf(InMemoryWebRtcSessionRepository.class);
            assertThat(context.getBean(WebRtcRateLimiter.class))
                    .isInstanceOf(InMemoryWebRtcRateLimiter.class);
        });
    }

    @Test
    void shouldUseRedisImplementationsWhenRedissonClientExists() {
        contextRunner
                .withUserConfiguration(RedissonWebRtcConfiguration.class)
                .withBean(RedissonClient.class, () -> mock(RedissonClient.class))
                .run(context -> {
                    assertThat(context.getBean(WebRtcSessionRepository.class))
                            .isInstanceOf(RedissonWebRtcSessionRepository.class);
                    assertThat(context.getBean(WebRtcRateLimiter.class))
                            .isInstanceOf(RedisWebRtcRateLimiter.class);
                });
    }

    @Test
    void shouldStartWithInMemoryFallbackWhenRedissonIsAbsent() {
        contextRunner
                .withUserConfiguration(RedissonWebRtcConfiguration.class)
                .withClassLoader(new FilteredClassLoader("org.redisson"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(WebRtcSessionRepository.class))
                            .isInstanceOf(InMemoryWebRtcSessionRepository.class);
                    assertThat(context.getBean(WebRtcRateLimiter.class))
                            .isInstanceOf(InMemoryWebRtcRateLimiter.class);
                });
    }

    @Test
    void shouldNotLoadWebRtcConfigurationWithoutWebMvc() {
        contextRunner
                .withClassLoader(new FilteredClassLoader("org.springframework.web.servlet"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(WebRtcSessionService.class);
                    assertThat(context).doesNotHaveBean(WebRtcProperties.class);
                });
    }

    @Test
    void shouldCreateNoWebRtcBeansWhenDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(WebRtcSessionConfiguration.class)
                .withPropertyValues("simple-secret.easymedia.webrtc.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(WebRtcSessionService.class);
                    assertThat(context).doesNotHaveBean(WebRtcSessionCleanupJob.class);
                    assertThat(context).doesNotHaveBean(ZlmWebRtcSignalingClient.class);
                });
    }

    @Test
    void shouldAllowCustomPolicyAndClockOverrides() {
        WebRtcAccessPolicy customPolicy = mock(WebRtcAccessPolicy.class);
        Clock fixedClock = Clock.fixed(Instant.ofEpochMilli(1234), ZoneOffset.UTC);
        contextRunner
                .withBean(WebRtcAccessPolicy.class, () -> customPolicy)
                .withBean(Clock.class, () -> fixedClock)
                .run(context -> {
                    assertThat(context.getBean(WebRtcAccessPolicy.class)).isSameAs(customPolicy);
                    assertThat(context.getBean(Clock.class)).isSameAs(fixedClock);
                });
    }

    @Test
    void shouldSelectMicrometerMetricsWhenRegistryExists() {
        contextRunner.withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(WebRtcSessionMetrics.class);
                    assertThat(context.getBean(WebRtcSessionMetrics.class))
                            .isInstanceOf(MicrometerWebRtcSessionMetrics.class);
                });
    }

    @Test
    void shouldDelayCleanupJobOnStartup() throws NoSuchMethodException {
        Scheduled scheduled = WebRtcSessionCleanupJob.class
                .getMethod("retryClosingSessions")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.initialDelayString())
                .isEqualTo("${simple-secret.easymedia.webrtc.cleanup-initial-delay:PT30S}");
    }

    @Test
    void shouldApplyConfiguredHttpTimeouts() {
        contextRunner.run(context -> {
            ClientHttpRequestFactory requestFactory = context.getBean(ClientHttpRequestFactory.class);
            assertThat(requestFactory).isInstanceOf(JdkClientHttpRequestFactory.class);
            assertThat(ReflectionTestUtils.getField(requestFactory, "readTimeout"))
                    .isEqualTo(Duration.ofSeconds(7));
            HttpClient httpClient = (HttpClient) ReflectionTestUtils.getField(requestFactory, "httpClient");
            assertThat(httpClient.connectTimeout()).contains(Duration.ofSeconds(3));
            assertThat(httpClient.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
        });
    }

    @Test
    void shouldSelectLocalZlmSignalingClientWhenEnabled() {
        ZlmMediaContext zlmMediaContext = mock(ZlmMediaContext.class);
        when(zlmMediaContext.getZlmApi()).thenReturn(mock(ZLMApi.class));

        contextRunner
                .withBean(ZlmMediaContext.class, () -> zlmMediaContext)
                .withPropertyValues("simple-secret.easymedia.webrtc.local-zlm-enabled=true")
                .run(context -> assertThat(context.getBean(ZlmWebRtcSignalingClient.class))
                        .isInstanceOf(LocalZlmWebRtcSignalingClient.class));
    }
}
