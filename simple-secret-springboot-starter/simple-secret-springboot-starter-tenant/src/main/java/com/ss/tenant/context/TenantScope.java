package com.ss.tenant.context;

/**
 * 可通过 try-with-resources 安全恢复的租户上下文作用域。
 *
 * <p>作用域必须在创建它的线程中按后进先出顺序关闭。重复关闭已经成功关闭的作用域是幂等的。</p>
 */
@FunctionalInterface
public interface TenantScope extends AutoCloseable {

    /**
     * 恢复进入该作用域之前的租户上下文。
     *
     * @throws IllegalStateException 在非创建线程关闭，或未按后进先出顺序关闭
     */
    @Override
    void close();
}
