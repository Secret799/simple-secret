package com.ss.sensitive.annotation;

import com.ss.sensitive.core.SensitiveStrategy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记需要在 JSON 输出时按指定策略处理的字符串字段。 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Sensitive {

    /**
     * 返回字段使用的脱敏策略。
     *
     * @return 脱敏策略
     */
    SensitiveStrategy strategy();

    /**
     * 返回供应用决策使用的角色提示。
     *
     * @return 角色标识
     */
    String roleKey() default "";

    /**
     * 返回供应用决策使用的权限提示。
     *
     * @return 权限标识
     */
    String perms() default "";
}
