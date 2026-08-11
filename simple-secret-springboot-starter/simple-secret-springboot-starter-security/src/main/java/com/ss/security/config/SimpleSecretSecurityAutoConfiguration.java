package com.ss.security.config;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.ss.security.web.LoginRequiredInterceptor;
import com.ss.security.web.SecurityWebMvcConfigurer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Simple Secret Servlet 路由登录保护自动配置。 */
@AutoConfiguration(afterName = "com.ss.auth.config.SimpleSecretAuthAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "org.springframework.web.servlet.DispatcherServlet",
        "org.springframework.web.servlet.HandlerInterceptor",
        "cn.dev33.satoken.stp.StpLogic"
})
@ConditionalOnProperty(prefix = "simple-secret.security", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SecurityProperties.class)
public class SimpleSecretSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(StpLogic.class)
    StpLogic simpleSecretSecurityStpLogic() {
        return StpUtil.getStpLogic();
    }

    @Bean
    @ConditionalOnMissingBean(LoginRequiredInterceptor.class)
    LoginRequiredInterceptor simpleSecretLoginRequiredInterceptor(StpLogic stpLogic) {
        return new LoginRequiredInterceptor(stpLogic);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityWebMvcConfigurer.class)
    SecurityWebMvcConfigurer simpleSecretSecurityWebMvcConfigurer(
            LoginRequiredInterceptor interceptor, SecurityProperties properties) {
        return new SecurityWebMvcConfigurer(interceptor, properties);
    }
}
