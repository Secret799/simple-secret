package com.ss.easymedia.config;

import com.ss.easymedia.callback.TrackDelegateCallback;
import com.ss.easymedia.config.properties.EmsProperties;
import com.ss.easymedia.core.handler.AppHandler;
import com.ss.easymedia.core.handler.AppHandlerHolder;
import com.ss.easymedia.core.handler.EmsCommonStreamChangeHandler;
import com.ss.easymedia.core.handler.app.EmsStreamNoFoundAppDispatcher;
import com.ss.easymedia.core.handler.app.EmsStreamNoReaderAppDispatcher;
import com.ss.easymedia.support.udp.UdpMulticastManager;
import com.ss.easymedia.security.EasyMediaManagementAuthorizationFilter;
import com.ss.easymedia.security.EasyMediaManagementAuthorizer;
import com.ss.zlm4j.config.annotation.SimpleSecretPropertySource;
import com.ss.zlm4j.context.ZlmCallbackHandlerContext;
import com.ss.zlm4j.handler.register.ZlmCallbackHandlerRegister;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;

import java.util.List;

/**
 * EasyMedia 自动配置
 * <p>
 * 依赖 zlm4j starter（{@code simple-secret.zlm4j.enabled}）提供内嵌 ZLMediaKit 能力。
 * 通过 {@code @ComponentScan} 注册 AppHandler 作用域处理器与 RestController。
 *
 * @author JunPzx
 * @since 2026/8/6
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.ss.easymedia")
@EnableConfigurationProperties(EmsProperties.class)
@SimpleSecretPropertySource("classpath:simple-secret-easymedia.yml")
@Import(WebRtcSessionConfiguration.class)
@ConditionalOnClass(name = {
        "jakarta.servlet.Servlet",
        "jakarta.validation.Validator",
        "org.springframework.web.servlet.DispatcherServlet"
})
@ConditionalOnProperty(name = {"simple-secret.zlm4j.enabled", "simple-secret.easymedia.enabled"},
        havingValue = "true")
public class SimpleSecretEasyMediaAutoConfiguration {

    /**
     * 使用默认资源边界创建 EMS 回调处理注册器。
     *
     * @param trackDelegateCallbacks 媒体轨道回调列表
     * @return ZlmCallbackHandlerRegister 注册器
     */
    public ZlmCallbackHandlerRegister emsCallbackHandlerRegister(
            List<TrackDelegateCallback> trackDelegateCallbacks) {
        return emsCallbackHandlerRegister(trackDelegateCallbacks, new EmsProperties());
    }

    /**
     * 注册EMS自定义回调处理注册器。
     * <p>
     * 通过实现 {@link Ordered} 保证优先级高于 zlm4j 默认注册器，
     * 使流未找到、无人观看分发器在 {@link ZlmCallbackHandlerContext} 中最后生效。
     *
     * @param trackDelegateCallbacks 媒体轨道回调列表
     * @param properties EasyMedia 配置
     * @return ZlmCallbackHandlerRegister 注册器
     */
    @Bean
    public ZlmCallbackHandlerRegister emsCallbackHandlerRegister(List<TrackDelegateCallback> trackDelegateCallbacks,
                                                                 EmsProperties properties) {
        return new EmsCallbackHandlerRegister(trackDelegateCallbacks, properties);
    }

    /**
     * 注册AppHandlerHolder
     *
     * @param appHandlers AppHandler列表
     * @return AppHandlerHolder
     */
    @Bean
    @Primary
    public AppHandlerHolder appHandlerHolder(List<AppHandler> appHandlers) {
        AppHandlerHolder appHandlerHolder = new AppHandlerHolder();
        appHandlerHolder.register(appHandlers);
        return appHandlerHolder;
    }

    /**
     * 注册UdpMulticastManager
     *
     * @return UdpMulticastManager
     */
    @Bean(destroyMethod = "shutdownAll")
    public UdpMulticastManager emsUdpMulticastManager() {
        return new UdpMulticastManager();
    }

    /**
     * 默认拒绝所有管理请求，宿主应用可注册自定义授权器替换。

     *
     * @return 返回的 {@code EasyMediaManagementAuthorizer} 结果
     */
    @Bean
    @ConditionalOnMissingBean(EasyMediaManagementAuthorizer.class)
    @ConditionalOnProperty(prefix = "simple-secret.easymedia", name = "management-api-enabled",
            havingValue = "true")
    public EasyMediaManagementAuthorizer easyMediaManagementAuthorizer() {
        return request -> false;
    }

    /**
     * 注册只保护 EasyMedia 通用管理路径的授权过滤器。

     *
     * @param authorizer EasyMedia 管理 API 授权器
     * @return 返回的 {@code FilterRegistrationBean<EasyMediaManagementAuthorizationFilter>} 结果
     */
    @Bean
    @ConditionalOnProperty(prefix = "simple-secret.easymedia", name = "management-api-enabled",
            havingValue = "true")
    public FilterRegistrationBean<EasyMediaManagementAuthorizationFilter> easyMediaManagementAuthorizationFilter(
            EasyMediaManagementAuthorizer authorizer) {
        FilterRegistrationBean<EasyMediaManagementAuthorizationFilter> registration =
                new FilterRegistrationBean<>(new EasyMediaManagementAuthorizationFilter(authorizer));
        registration.addUrlPatterns("/easyMedia/api/common/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * EMS 回调注册器。
     */
    static class EmsCallbackHandlerRegister implements ZlmCallbackHandlerRegister, Ordered {

        /** 轨道委托回调。 */
        private final List<TrackDelegateCallback> trackDelegateCallbacks;

        /** EasyMedia 配置。 */
        private final EmsProperties properties;

        EmsCallbackHandlerRegister(List<TrackDelegateCallback> trackDelegateCallbacks, EmsProperties properties) {
            this.trackDelegateCallbacks = trackDelegateCallbacks;
            this.properties = properties;
        }

        @Override
        public void register(ZlmCallbackHandlerContext context) {
            context.setStreamChangeHandler(new EmsCommonStreamChangeHandler(trackDelegateCallbacks, properties))
                    .setStreamNoReaderHandler(new EmsStreamNoReaderAppDispatcher())
                    .setStreamNoFoundHandler(new EmsStreamNoFoundAppDispatcher());
        }

        @Override
        public int getOrder() {
            return 0;
        }
    }
}
