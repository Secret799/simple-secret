package com.ss.zlm4j.config;

import com.ss.zlm4j.config.annotation.SimpleSecretPropertySource;
import com.ss.zlm4j.config.properties.MediaResourcePolicyProperties;
import com.ss.zlm4j.config.properties.ZlmMediaProperties;
import com.ss.zlm4j.config.properties.VideoStackValidationProperties;
import com.ss.zlm4j.context.ZlmCallbackHandlerContext;
import com.ss.zlm4j.context.ZlmMediaContext;
import com.ss.zlm4j.handler.register.DefaultZlmCallbackHandlerRegister;
import com.ss.zlm4j.handler.register.ZlmCallbackHandlerRegister;
import com.ss.zlm4j.security.DefaultMediaResourcePolicy;
import com.ss.zlm4j.security.MediaResourcePolicy;
import com.ss.zlm4j.service.validation.VideoStackValidator;
import com.ss.zlm4j.support.SpringUtils;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.OrderComparator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * zlm4j 自动配置
 *
 * @author JunPzx
 * @since 2024/6/12 13:41
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.ss.zlm4j")
@EnableConfigurationProperties({ZlmMediaProperties.class, MediaResourcePolicyProperties.class,
        VideoStackValidationProperties.class})
@SimpleSecretPropertySource(value = {
        "classpath:simple-secret-zlm4j.yml", "classpath:simple-secret__zlm4j-default__conf.ini"
})
@Import(SpringUtils.class)
public class SimpleSecretZlmAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MediaResourcePolicy.class)
    MediaResourcePolicy mediaResourcePolicy(MediaResourcePolicyProperties properties) {
        return new DefaultMediaResourcePolicy(properties);
    }

    @Bean
    @ConditionalOnMissingBean(VideoStackValidator.class)
    VideoStackValidator videoStackValidator(VideoStackValidationProperties properties) {
        return new VideoStackValidator(properties);
    }

    /**
     * 创建 zlm 转码等任务使用的调度执行器。
     *
     * @return 调度执行器
     */
    @Bean(name = "zlmScheduledExecutor", destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = "zlmScheduledExecutor")
    ScheduledThreadPoolExecutor zlmScheduledExecutor() {
        return new ScheduledThreadPoolExecutor(2, runnable -> {
            Thread thread = new Thread(runnable, "simple-secret-zlm-scheduled-");
            thread.setDaemon(true);
            return thread;
        });
    }


    /**
     * 创建并初始化 ZLMediaKit 原生运行上下文。
     *
     * @param properties 模块配置
     * @param callbackHandlerContext 回调处理器上下文
     * @param environment Spring 配置环境
     * @return ZLMediaKit 原生运行上下文
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "simple-secret.zlm4j.enabled", havingValue = "true")
    public ZlmMediaContext zlmMediaContext(ZlmMediaProperties properties,
                                           ZlmCallbackHandlerContext callbackHandlerContext,
                                           ConfigurableEnvironment environment) {
        Map<String, Object> zlm4jPropertiesMap = new HashMap<>();
        // 获取系统中的默认配置信息
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof SimpleSecretIniPropertySource mapSource) {
                // 获取当前属性源的所有键
                for (String key : mapSource.getPropertyNames()) {
                    if (key.startsWith("simple-secret.zlm4j-default.")) {
                        zlm4jPropertiesMap.put(key.replace("simple-secret.zlm4j-default.", ""),
                                mapSource.getProperty(key));
                    }
                }
            }
        }
        // 创建zlm4j上下文
        return new ZlmMediaContext(properties, callbackHandlerContext, zlm4jPropertiesMap);
    }


    /**
     * 创建回调处理器上下文并按 Spring 顺序注册扩展处理器。
     *
     * @param registers 回调处理器注册器列表
     * @return 已完成注册的回调处理器上下文
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "simple-secret.zlm4j.enabled", havingValue = "true")
    public ZlmCallbackHandlerContext callbackHandlerContext(List<ZlmCallbackHandlerRegister> registers) {
        ZlmCallbackHandlerContext context = new ZlmCallbackHandlerContext();
        if (registers == null || registers.isEmpty()) {
            return context;
        }
        // 根据oder排序
        OrderComparator.sort(registers);
        // 反转顺序,优先级最高的最后执行，因为涉及到对同一个对象的参数修改，所以优先级最高的最后执行
        Collections.reverse(registers);
        // 注册
        registers.forEach(register -> register.register(context));
        return context;
    }


    /**
     * 创建默认 ZLMediaKit 回调处理器注册器。
     *
     * @return 默认回调处理器注册器
     */
    @Bean
    @ConditionalOnProperty(name = "simple-secret.zlm4j.enabled", havingValue = "true")
    public ZlmCallbackHandlerRegister defaultZlmCallbackHandlerRegister() {
        return new DefaultZlmCallbackHandlerRegister();
    }
}
