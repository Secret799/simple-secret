package com.ss.auth.config;

import com.ss.auth.web.SimpleSecretAuthExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/** Auth starter 的可选 Servlet 异常处理自动配置。 */
@AutoConfiguration(after = SimpleSecretAuthAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = {
        "org.springframework.web.servlet.DispatcherServlet",
        "cn.dev33.satoken.exception.NotLoginException"
})
@ConditionalOnProperty(prefix = "simple-secret.auth", name = "enabled", havingValue = "true")
@ConditionalOnProperty(
        prefix = "simple-secret.auth.exception-handler",
        name = "enabled",
        havingValue = "true")
public class SimpleSecretAuthWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SimpleSecretAuthExceptionHandler.class)
    SimpleSecretAuthExceptionHandler simpleSecretAuthExceptionHandler() {
        return SimpleSecretAuthExceptionHandler.create();
    }
}
