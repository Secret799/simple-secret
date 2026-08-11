package com.ss.tenant.context;

import com.ss.tenant.exception.TenantException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** 管理当前线程的临时租户和租户忽略作用域。 */
public final class TenantContext {
    private final TenantContextProvider provider;
    private final ThreadLocal<Deque<ScopeState>> states = new ThreadLocal<>();

    /**
     * 创建租户上下文。
     *
     * @param provider 应用提供的租户来源
     */
    public TenantContext(TenantContextProvider provider) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
    }

    /**
     * 查找当前租户，临时作用域优先于应用 provider。
     *
     * @return 去除首尾空白后的租户标识
     */
    public Optional<String> findTenantId() {
        Deque<ScopeState> stack = states.get();
        if (stack != null) {
            for (ScopeState state : stack) {
                if (state.tenantId != null) {
                    return Optional.of(state.tenantId);
                }
            }
        }
        return normalize(provider.currentTenantId());
    }

    /**
     * 获取当前租户，租户缺失时阻止继续执行。
     *
     * @return 当前租户标识
     * @throws TenantException 当前上下文没有有效租户标识
     */
    public String requireTenantId() {
        return findTenantId().orElseThrow(() ->
                new TenantException("No tenant id is available for the current operation"));
    }

    /**
     * 返回最内层作用域是否要求忽略租户隔离。
     *
     * @return 忽略租户隔离时为 {@code true}
     */
    public boolean isIgnored() {
        ScopeState state = currentState();
        return state != null && state.ignored;
    }

    /**
     * 在当前线程绑定临时租户。
     *
     * @param tenantId 非空租户标识
     * @return 用于恢复上下文的作用域
     * @throws IllegalArgumentException 租户标识为空
     */
    public TenantScope useTenant(String tenantId) {
        String normalized = normalize(tenantId).orElseThrow(() ->
                new IllegalArgumentException("tenantId must not be blank"));
        return push(new ScopeState(normalized, false));
    }

    /**
     * 在当前线程临时忽略租户隔离。
     *
     * @return 用于恢复上下文的作用域
     * @implNote 忽略状态只影响 SQL 隔离，外层临时租户仍可通过
     * {@link #findTenantId()} 和 {@link #requireTenantId()} 读取。
     */
    public TenantScope ignoreTenant() {
        return push(new ScopeState(null, true));
    }

    /**
     * 在指定租户作用域中执行操作。
     *
     * @param tenantId 租户标识
     * @param action 操作
     */
    public void runWithTenant(String tenantId, Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        try (TenantScope ignored = useTenant(tenantId)) {
            action.run();
        }
    }

    /**
     * 在指定租户作用域中计算结果。
     *
     * @param tenantId 租户标识
     * @param action 操作
     * @param <T> 结果类型
     * @return 操作结果
     */
    public <T> T callWithTenant(String tenantId, Supplier<T> action) {
        Objects.requireNonNull(action, "action must not be null");
        try (TenantScope ignored = useTenant(tenantId)) {
            return action.get();
        }
    }

    /**
     * 在忽略租户隔离的作用域中执行操作。
     *
     * @param action 操作
     */
    public void runWithoutTenant(Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        try (TenantScope ignored = ignoreTenant()) {
            action.run();
        }
    }

    /**
     * 在忽略租户隔离的作用域中计算结果。
     *
     * @param action 操作
     * @param <T> 结果类型
     * @return 操作结果
     */
    public <T> T callWithoutTenant(Supplier<T> action) {
        Objects.requireNonNull(action, "action must not be null");
        try (TenantScope ignored = ignoreTenant()) {
            return action.get();
        }
    }

    private TenantScope push(ScopeState state) {
        Deque<ScopeState> stack = states.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            states.set(stack);
        }
        stack.push(state);
        return new ScopeHandle(state, Thread.currentThread());
    }

    private ScopeState currentState() {
        Deque<ScopeState> stack = states.get();
        return stack == null ? null : stack.peek();
    }

    private static Optional<String> normalize(String tenantId) {
        if (tenantId == null) {
            return Optional.empty();
        }
        String normalized = tenantId.trim();
        return normalized.isEmpty() ? Optional.empty() : Optional.of(normalized);
    }

    private static final class ScopeState {
        private final String tenantId;
        private final boolean ignored;

        private ScopeState(String tenantId, boolean ignored) {
            this.tenantId = tenantId;
            this.ignored = ignored;
        }
    }

    private final class ScopeHandle implements TenantScope {
        private final ScopeState state;
        private final Thread owner;
        private boolean closed;

        private ScopeHandle(ScopeState state, Thread owner) {
            this.state = state;
            this.owner = owner;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("Tenant scope must be closed on its owner thread");
            }
            Deque<ScopeState> stack = states.get();
            if (stack == null || stack.peek() != state) {
                throw new IllegalStateException("Tenant scopes must be closed in reverse order");
            }
            stack.pop();
            if (stack.isEmpty()) {
                states.remove();
            }
            closed = true;
        }
    }
}
