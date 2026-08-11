package com.ss.common.toolbox.cache;

import java.util.Objects;

/**
 * 缓存值比较器。
 *
 * @param <V> 值类型
 */
@FunctionalInterface
public interface ValueComparator<V> {

    /**
     * 判断两个值是否相等。
     *
     * @param left  左值
     * @param right 右值
     * @return 相等时返回 {@code true}
     */
    boolean isEqual(V left, V right);

    /**
     * 判断两个值是否不相等。
     *
     * @param left  左值
     * @param right 右值
     * @return 不相等时返回 {@code true}
     */
    default boolean isNotEqual(V left, V right) {
        return !isEqual(left, right);
    }

    /**
     * 返回使用 {@link Objects#equals(Object, Object)} 的比较器。
     *
     * @param <V> 值类型
     * @return 空值安全的自然比较器
     */
    static <V> ValueComparator<V> natural() {
        return Objects::equals;
    }
}
