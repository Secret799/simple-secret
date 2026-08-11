package com.ss.tenant.context;

import com.ss.tenant.exception.TenantException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证租户上下文作用域不会泄漏，并在租户缺失时失败关闭。 */
class TenantContextTest {

    @Test
    void shouldUseNormalizedProviderTenantWhenNoScopeExists() {
        TenantContext context = new TenantContext(() -> " tenant-a ");

        assertThat(context.findTenantId()).contains("tenant-a");
        assertThat(context.requireTenantId()).isEqualTo("tenant-a");
        assertThat(context.isIgnored()).isFalse();
    }

    @Test
    void shouldFailClosedWhenTenantIsMissing() {
        TenantContext context = new TenantContext(() -> "  ");

        assertThat(context.findTenantId()).isEmpty();
        assertThatThrownBy(context::requireTenantId)
                .isInstanceOf(TenantException.class)
                .hasMessageContaining("tenant");
    }

    @Test
    void shouldRestoreNestedTenantScopes() {
        TenantContext context = new TenantContext(() -> "provider");

        try (TenantScope first = context.useTenant("tenant-a")) {
            assertThat(context.requireTenantId()).isEqualTo("tenant-a");
            try (TenantScope second = context.useTenant(" tenant-b ")) {
                assertThat(context.requireTenantId()).isEqualTo("tenant-b");
            }
            assertThat(context.requireTenantId()).isEqualTo("tenant-a");
        }

        assertThat(context.requireTenantId()).isEqualTo("provider");
    }

    @Test
    void shouldCleanCallbackScopesAfterSuccessAndFailure() {
        TenantContext context = new TenantContext(() -> "provider");

        assertThat(context.callWithTenant("tenant-a", context::requireTenantId))
                .isEqualTo("tenant-a");
        assertThatThrownBy(() -> context.runWithTenant("tenant-b", () -> {
            throw new IllegalArgumentException("boom");
        })).isInstanceOf(IllegalArgumentException.class).hasMessage("boom");
        assertThat(context.requireTenantId()).isEqualTo("provider");
    }

    @Test
    void shouldNestIgnoreAndTenantScopesLexically() {
        TenantContext context = new TenantContext(() -> "provider");

        assertThat(context.callWithoutTenant(() -> {
            assertThat(context.isIgnored()).isTrue();
            return context.callWithTenant("tenant-a", () -> {
                assertThat(context.isIgnored()).isFalse();
                return context.requireTenantId();
            });
        })).isEqualTo("tenant-a");
        assertThat(context.isIgnored()).isFalse();

        context.runWithoutTenant(() -> assertThat(context.isIgnored()).isTrue());
        assertThat(context.isIgnored()).isFalse();
    }

    @Test
    void shouldPreserveOuterTenantInsideIgnoreScope() {
        TenantContext context = new TenantContext(() -> "provider");

        context.runWithTenant("tenant-a", () -> context.runWithoutTenant(() -> {
            assertThat(context.isIgnored()).isTrue();
            assertThat(context.requireTenantId()).isEqualTo("tenant-a");
        }));

        assertThat(context.isIgnored()).isFalse();
        assertThat(context.requireTenantId()).isEqualTo("provider");
    }

    @Test
    void shouldRejectBlankScopedTenant() {
        TenantContext context = new TenantContext(() -> "provider");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> context.useTenant(" "))
                .withMessageContaining("tenantId");
    }

    @Test
    void shouldRejectOutOfOrderCloseWithoutCorruptingStack() {
        TenantContext context = new TenantContext(() -> "provider");
        TenantScope first = context.useTenant("tenant-a");
        TenantScope second = context.useTenant("tenant-b");

        assertThatIllegalStateException().isThrownBy(first::close)
                .withMessageContaining("order");
        assertThat(context.requireTenantId()).isEqualTo("tenant-b");

        second.close();
        assertThat(context.requireTenantId()).isEqualTo("tenant-a");
        first.close();
        first.close();
        assertThat(context.requireTenantId()).isEqualTo("provider");
    }

    @Test
    void shouldRejectClosingScopeFromAnotherThread() throws Exception {
        TenantContext context = new TenantContext(() -> "provider");
        TenantScope scope = context.useTenant("tenant-a");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            try {
                scope.close();
            } catch (Throwable error) {
                failure.set(error);
            }
        });

        thread.start();
        thread.join();

        assertThat(failure.get())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("thread");
        assertThat(context.requireTenantId()).isEqualTo("tenant-a");
        scope.close();
    }
}
