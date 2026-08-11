package com.ss.security.web;

import cn.dev33.satoken.stp.StpLogic;
import com.ss.security.config.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证 Security 路由注册使用构造时的路径和顺序快照。 */
class SecurityWebMvcConfigurerTest {

    @Test
    void shouldNotRegisterInterceptorWhenIncludePatternsAreEmpty() {
        SecurityProperties properties = new SecurityProperties();
        properties.setPathPatterns(List.of());
        SecurityWebMvcConfigurer configurer = new SecurityWebMvcConfigurer(
                new LoginRequiredInterceptor(new StpLogic("test")), properties);
        ExposedInterceptorRegistry registry = new ExposedInterceptorRegistry();

        configurer.addInterceptors(registry);

        assertThat(registry.interceptors()).isEmpty();
    }

    @Test
    void shouldRegisterFrozenIncludeExcludeAndOrder() {
        SecurityProperties properties = new SecurityProperties();
        properties.setPathPatterns(List.of("/private/**"));
        properties.setExcludePathPatterns(List.of("/private/public"));
        properties.setOrder(25);
        LoginRequiredInterceptor interceptor = new LoginRequiredInterceptor(new StpLogic("test"));
        SecurityWebMvcConfigurer configurer = new SecurityWebMvcConfigurer(interceptor, properties);
        properties.setPathPatterns(List.of("/changed/**"));
        properties.setExcludePathPatterns(List.of());
        properties.setOrder(-100);
        ExposedInterceptorRegistry registry = new ExposedInterceptorRegistry();
        HandlerInterceptor before = new HandlerInterceptor() { };
        HandlerInterceptor after = new HandlerInterceptor() { };
        registry.addInterceptor(after).order(30);
        registry.addInterceptor(before).order(20);

        configurer.addInterceptors(registry);

        assertThat(registry.interceptors()).hasSize(3);
        assertThat(registry.interceptors().get(0)).isSameAs(before);
        assertThat(registry.interceptors().get(2)).isSameAs(after);
        assertThat(registry.interceptors().get(1))
                .isInstanceOfSatisfying(MappedInterceptor.class, mapped -> {
                    assertThat(mapped.getInterceptor()).isSameAs(interceptor);
                    assertThat(mapped.getIncludePathPatterns()).containsExactly("/private/**");
                    assertThat(mapped.getExcludePathPatterns()).containsExactly("/private/public");
                });
    }

    private static final class ExposedInterceptorRegistry extends InterceptorRegistry {
        private List<Object> interceptors() {
            return getInterceptors();
        }
    }
}
