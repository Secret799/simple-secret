package com.ss.easymedia.config;

import com.ss.easymedia.config.properties.EmsProperties;
import com.ss.easymedia.config.properties.WebRtcProperties;
import com.ss.easymedia.webrtc.client.LocalZlmWebRtcSignalingClient;
import com.ss.easymedia.webrtc.client.RestClientZlmWebRtcSignalingClient;
import com.ss.easymedia.webrtc.client.ZlmWebRtcSignalingClient;
import com.ss.easymedia.webrtc.client.ZlmWebRtcUriPolicy;
import com.ss.easymedia.webrtc.id.SecureWebRtcSessionIdGenerator;
import com.ss.easymedia.webrtc.id.WebRtcSessionIdGenerator;
import com.ss.easymedia.webrtc.metrics.MicrometerWebRtcSessionMetrics;
import com.ss.easymedia.webrtc.metrics.NoopWebRtcSessionMetrics;
import com.ss.easymedia.webrtc.metrics.WebRtcSessionMetrics;
import com.ss.easymedia.webrtc.repository.InMemoryWebRtcSessionRepository;
import com.ss.easymedia.webrtc.repository.WebRtcRedisKeys;
import com.ss.easymedia.webrtc.repository.WebRtcSessionRepository;
import com.ss.easymedia.webrtc.security.DefaultWebRtcAccessPolicy;
import com.ss.easymedia.webrtc.security.DefaultWebRtcIdentityProvider;
import com.ss.easymedia.webrtc.security.InMemoryWebRtcRateLimiter;
import com.ss.easymedia.webrtc.security.WebRtcAccessPolicy;
import com.ss.easymedia.webrtc.security.WebRtcIdentityProvider;
import com.ss.easymedia.webrtc.security.WebRtcRateLimiter;
import com.ss.easymedia.webrtc.service.DefaultWebRtcSessionService;
import com.ss.easymedia.webrtc.service.WebRtcSessionCleanupJob;
import com.ss.easymedia.webrtc.service.WebRtcSessionService;
import com.ss.zlm4j.context.ZlmMediaContext;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Clock;

/**
 * WebRTC 会话网关组件装配。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(EmsProperties.class)
@ConditionalOnClass(name = {
        "jakarta.servlet.Servlet",
        "jakarta.validation.Validator",
        "org.springframework.web.servlet.DispatcherServlet"
})
@ConditionalOnProperty(prefix = "simple-secret.easymedia.webrtc", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class WebRtcSessionConfiguration {

    /** @return 从 EMS 总配置提取的 WebRTC 配置。 */
    @Bean
    @ConditionalOnMissingBean
    public WebRtcProperties webRtcProperties(EmsProperties emsProperties) {
        return emsProperties.getWebrtc();
    }

    /** @return 用于会话时间戳和过期判断的 UTC 时钟。 */
    @Bean
    @ConditionalOnMissingBean
    public Clock webRtcClock() {
        return Clock.systemUTC();
    }

    /** @return 生成公开会话 ID 的密码学随机生成器。 */
    @Bean
    @ConditionalOnMissingBean
    public WebRtcSessionIdGenerator webRtcSessionIdGenerator() {
        return new SecureWebRtcSessionIdGenerator(new SecureRandom());
    }

    /** @return 统一构造 WebRTC Redis 键的工具。 */
    @Bean
    @ConditionalOnMissingBean
    public WebRtcRedisKeys webRtcRedisKeys() {
        return new WebRtcRedisKeys();
    }

    /** @return 配置了连接和读取超时的外置 ZLM HTTP 客户端工厂。 */
    @Bean(name = "webRtcClientHttpRequestFactory")
    @ConditionalOnMissingBean(name = "webRtcClientHttpRequestFactory")
    public ClientHttpRequestFactory webRtcClientHttpRequestFactory(WebRtcProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.getRequestTimeout());
        return factory;
    }

    /** @return 仅供外置 ZLM 信令适配器使用的 REST 客户端。 */
    @Bean(name = "webRtcRestClient")
    @ConditionalOnMissingBean(name = "webRtcRestClient")
    public RestClient webRtcRestClient(
            @Qualifier("webRtcClientHttpRequestFactory") ClientHttpRequestFactory requestFactory) {
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    /** @return 验证上游会话资源地址可信性的策略。 */
    @Bean
    @ConditionalOnMissingBean
    public ZlmWebRtcUriPolicy zlmWebRtcUriPolicy(WebRtcProperties properties) {
        return new ZlmWebRtcUriPolicy(properties.getSignalingBaseUrl());
    }

    /** @return 使用当前 JVM 内嵌 ZLM C API 的信令客户端。 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "simple-secret.easymedia.webrtc", name = "local-zlm-enabled",
            havingValue = "true")
    public ZlmWebRtcSignalingClient localZlmWebRtcSignalingClient(
            ZlmMediaContext zlmMediaContext, WebRtcProperties properties) {
        return new LocalZlmWebRtcSignalingClient(
                zlmMediaContext.getZlmApi(), properties.getRequestTimeout());
    }

    /** @return 向外置 ZLM HTTP 服务转发的信令客户端。 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "simple-secret.easymedia.webrtc", name = "local-zlm-enabled",
            havingValue = "false", matchIfMissing = true)
    public ZlmWebRtcSignalingClient zlmWebRtcSignalingClient(
            @Qualifier("webRtcRestClient") RestClient restClient,
            WebRtcProperties properties,
            ZlmWebRtcUriPolicy uriPolicy) {
        return new RestClientZlmWebRtcSignalingClient(
                restClient, properties.getSignalingBaseUrl(), uriPolicy);
    }

    /** @return 未配置 Redis 集成时使用的单机内存会话仓库。 */
    @Bean
    @ConditionalOnMissingBean(value = WebRtcSessionRepository.class,
            type = "org.redisson.api.RedissonClient")
    public WebRtcSessionRepository webRtcSessionRepository() {
        return new InMemoryWebRtcSessionRepository();
    }

    /** @return 默认身份解析器：从配置读取固定主体，未配置且要求认证时拒绝匿名请求。 */
    @Bean
    @ConditionalOnMissingBean
    public WebRtcIdentityProvider webRtcIdentityProvider(WebRtcProperties properties) {
        return new DefaultWebRtcIdentityProvider(properties);
    }

    /** @return 默认的创建和会话所有权访问策略。 */
    @Bean
    @ConditionalOnMissingBean
    public WebRtcAccessPolicy webRtcAccessPolicy(WebRtcProperties properties) {
        return new DefaultWebRtcAccessPolicy(properties);
    }

    /** @return 未配置 Redis 集成时使用的有界单机限流器。 */
    @Bean
    @ConditionalOnMissingBean(value = WebRtcRateLimiter.class,
            type = "org.redisson.api.RedissonClient")
    public WebRtcRateLimiter webRtcRateLimiter(WebRtcRedisKeys keys,
                                                WebRtcProperties properties,
                                                Clock clock) {
        return new InMemoryWebRtcRateLimiter(keys, properties, clock);
    }

    /** @return 写入 Micrometer 的生产指标实现。 */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean(WebRtcSessionMetrics.class)
    public WebRtcSessionMetrics micrometerWebRtcSessionMetrics(MeterRegistry registry) {
        return new MicrometerWebRtcSessionMetrics(registry);
    }

    /** @return 未配置指标注册表时使用的空实现。 */
    @Bean
    @ConditionalOnMissingBean(WebRtcSessionMetrics.class)
    public WebRtcSessionMetrics noopWebRtcSessionMetrics() {
        return new NoopWebRtcSessionMetrics();
    }

    /** @return 编排鉴权、限流、上游信令和会话持久化的服务。 */
    @Bean
    @ConditionalOnMissingBean
    public WebRtcSessionService webRtcSessionService(
            WebRtcIdentityProvider identityProvider,
            WebRtcAccessPolicy accessPolicy,
            WebRtcRateLimiter rateLimiter,
            ZlmWebRtcSignalingClient zlmClient,
            ZlmWebRtcUriPolicy uriPolicy,
            WebRtcSessionRepository repository,
            WebRtcSessionIdGenerator idGenerator,
            WebRtcProperties properties,
            Clock clock,
            WebRtcSessionMetrics metrics) {
        return new DefaultWebRtcSessionService(
                identityProvider, accessPolicy, rateLimiter, zlmClient, uriPolicy,
                repository, idGenerator, properties, clock, metrics);
    }

    /** @return 定期补偿失败上游 DELETE 操作的任务。 */
    @Bean
    @ConditionalOnMissingBean
    public WebRtcSessionCleanupJob webRtcSessionCleanupJob(
            WebRtcSessionRepository repository,
            WebRtcSessionService service,
            WebRtcProperties properties,
            Clock clock) {
        return new WebRtcSessionCleanupJob(repository, service, properties, clock);
    }
}
