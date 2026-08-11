package com.ss.dict.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记需要把字典编码翻译到展示字段的对象字段。 */
@Documented
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DictField {

    /**
     * 返回已注册的字典 source key。
     *
     * @return 字典 source key
     */
    String value();

    /**
     * 返回固定字典类型，与 {@link #typeField()} 互斥。
     *
     * @return 固定字典类型，空字符串表示不限制
     */
    String type() default "";

    /**
     * 返回当前对象中提供动态字典类型的字段名，与 {@link #type()} 互斥。
     *
     * @return 类型字段名，空字符串表示不使用
     */
    String typeField() default "";

    /**
     * 返回接收展示标签的字段名。
     *
     * <p>为空时使用“源字段名 + DisplayLabel”。</p>
     *
     * @return 展示字段名
     */
    String labelField() default "";

    /**
     * 返回源字段是否包含多个编码。
     *
     * @return 多值时返回 {@code true}
     */
    boolean multiple() default false;

    /**
     * 返回多值编码的字面量分隔符。
     *
     * @return 非空分隔符
     */
    String separator() default ",";
}
