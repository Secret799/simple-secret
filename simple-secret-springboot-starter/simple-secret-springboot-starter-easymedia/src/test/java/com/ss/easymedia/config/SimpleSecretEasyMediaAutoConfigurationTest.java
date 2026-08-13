package com.ss.easymedia.config;

import com.ss.easymedia.callback.TrackDelegateCallback;
import com.ss.easymedia.config.properties.EmsProperties;
import com.ss.easymedia.core.handler.EmsCommonStreamChangeHandler;
import com.ss.easymedia.core.handler.AppHandlerHolder;
import com.ss.easymedia.controller.ApiController;
import com.ss.easymedia.webrtc.client.ZlmWebRtcSignalingClient;
import com.ss.easymedia.webrtc.security.WebRtcIdentityProvider;
import com.ss.easymedia.webrtc.service.WebRtcSessionService;
import com.ss.zlm4j.context.ZlmCallbackHandlerContext;
import com.ss.zlm4j.domain.MediaSourceDomain;
import com.ss.zlm4j.domain.TrackDomain;
import com.ss.zlm4j.handler.register.ZlmCallbackHandlerRegister;
import com.ss.zlm4j.service.ISnapService;
import com.ss.zlm4j.service.ITranscodeService;
import com.ss.zlm4j.service.IVideoStackService;
import com.ss.zlm4j.service.IZlmMediaService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * EasyMedia 自动配置装配测试。
 */
class SimpleSecretEasyMediaAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleSecretEasyMediaAutoConfiguration.class))
            // ApiController 依赖的 zlm4j 服务，测试中以 mock 代替（不加载真实 zlm4j 原生库）
            .withBean(IZlmMediaService.class, () -> mock(IZlmMediaService.class))
            .withBean(ISnapService.class, () -> mock(ISnapService.class))
            .withBean(ITranscodeService.class, () -> mock(ITranscodeService.class))
            .withBean(IVideoStackService.class, () -> mock(IVideoStackService.class));

    @Test
    void shouldRegisterEasyMediaStreamChangeHandler() {
        TrackDelegateCallback callback = (MediaSourceDomain source, TrackDomain track,
                                          TrackDelegateCallback.TackDelegateInfo frame) -> {
        };
        SimpleSecretEasyMediaAutoConfiguration configuration =
                new SimpleSecretEasyMediaAutoConfiguration();
        ZlmCallbackHandlerRegister register = configuration
                .emsCallbackHandlerRegister(List.of(callback), new EmsProperties());
        ZlmCallbackHandlerContext handlerContext = new ZlmCallbackHandlerContext();

        register.register(handlerContext);

        assertThat(handlerContext.getStreamChangeHandler())
                .isInstanceOf(EmsCommonStreamChangeHandler.class);
    }

    @Test
    void shouldKeepCompatibleOneArgumentCallbackRegisterFactory() {
        TrackDelegateCallback callback = (source, track, frame) -> {
        };
        SimpleSecretEasyMediaAutoConfiguration configuration =
                new SimpleSecretEasyMediaAutoConfiguration();

        ZlmCallbackHandlerRegister register = configuration.emsCallbackHandlerRegister(List.of(callback));
        ZlmCallbackHandlerContext handlerContext = new ZlmCallbackHandlerContext();
        register.register(handlerContext);

        assertThat(handlerContext.getStreamChangeHandler())
                .isInstanceOf(EmsCommonStreamChangeHandler.class);
    }

    @Test
    void shouldSkipAllBeansByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(AppHandlerHolder.class);
            assertThat(context).doesNotHaveBean(WebRtcSessionService.class);
        });
    }

    @Test
    void shouldCreateEasyMediaBeansWhenExplicitlyEnabled() {
        contextRunner.withPropertyValues(
                        "simple-secret.zlm4j.enabled=true",
                        "simple-secret.easymedia.enabled=true")
                .run(context -> {
            assertThat(context).hasSingleBean(AppHandlerHolder.class);
            assertThat(context).hasSingleBean(WebRtcSessionService.class);
            assertThat(context).hasSingleBean(WebRtcIdentityProvider.class);
            assertThat(context).hasSingleBean(ZlmWebRtcSignalingClient.class);
            assertThat(context).doesNotHaveBean(ApiController.class);
        });
    }

    @Test
    void shouldCreateManagementApiOnlyWhenExplicitlyEnabled() {
        contextRunner.withPropertyValues(
                        "simple-secret.zlm4j.enabled=true",
                        "simple-secret.easymedia.enabled=true",
                        "simple-secret.easymedia.management-api-enabled=true")
                .run(context -> assertThat(context).hasSingleBean(ApiController.class));
    }

    @Test
    void shouldSkipAllBeansWhenEasymediaDisabled() {
        contextRunner
                .withPropertyValues("simple-secret.easymedia.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AppHandlerHolder.class);
                    assertThat(context).doesNotHaveBean(WebRtcSessionService.class);
                });
    }

    @Test
    void shouldSkipAllBeansWhenZlm4jDisabled() {
        contextRunner
                .withPropertyValues("simple-secret.zlm4j.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AppHandlerHolder.class);
                    assertThat(context).doesNotHaveBean(WebRtcSessionService.class);
                });
    }

    @Test
    void shouldShutdownUdpManagerWhenApplicationContextCloses() throws Exception {
        Method method = SimpleSecretEasyMediaAutoConfiguration.class
                .getDeclaredMethod("emsUdpMulticastManager");

        assertThat(method.getAnnotation(Bean.class).destroyMethod()).isEqualTo("shutdownAll");
    }
}
