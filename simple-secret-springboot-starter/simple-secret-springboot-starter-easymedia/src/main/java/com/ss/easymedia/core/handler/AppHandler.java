package com.ss.easymedia.core.handler;

import org.springframework.core.Ordered;

/**
 * app处理器
 *
 * @author JunPzx
 * @since 2025/8/21 16:02
 */
public interface AppHandler extends Ordered {
    /**
     * app作用域
     *
     * @return app作用域
     */
    String app();

    @Override
    default int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
