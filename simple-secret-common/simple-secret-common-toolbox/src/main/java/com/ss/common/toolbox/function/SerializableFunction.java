package com.ss.common.toolbox.function;

import java.io.Serializable;
import java.util.function.Function;

/**
 * 可序列化的单参数函数，用于保留 Java 方法引用的元数据。
 *
 * @param <T> 输入类型
 * @param <R> 返回类型
 */
@FunctionalInterface
public interface SerializableFunction<T, R> extends Function<T, R>, Serializable {
}
