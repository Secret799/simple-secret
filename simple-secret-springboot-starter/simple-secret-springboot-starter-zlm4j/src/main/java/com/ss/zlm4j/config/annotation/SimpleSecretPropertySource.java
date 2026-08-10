package com.ss.zlm4j.config.annotation;

import org.springframework.core.Ordered;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * simple-secret 配置源注解。
 *
 * <p>标注在自动配置类上，将指定位置的配置文件（yml/ini）加载进 Spring Environment，
 * 供 {@code @ConfigurationProperties} 绑定。迁移自 honeybee 的 {@code @HoneybeePropertySource}。</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SimpleSecretPropertySource {

    /**
     * 要加载的资源路径，例如 {@code "classpath:simple-secret-zlm4j.yml"}。
     *
     * @return 资源路径
     */
    String[] value();

    /**
     * 是否同时加载 {@code app-{activeProfile}.yml}。
     *
     * @return 是否加载 active profile 文件
     */
    boolean loadActiveProfile() default true;

    /**
     * 资源加载顺序。
     *
     * @return 顺序
     */
    int order() default Ordered.LOWEST_PRECEDENCE;
}
