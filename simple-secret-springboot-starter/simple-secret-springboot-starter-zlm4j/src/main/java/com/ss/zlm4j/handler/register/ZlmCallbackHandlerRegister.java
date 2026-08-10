package com.ss.zlm4j.handler.register;

import com.ss.zlm4j.context.ZlmCallbackHandlerContext;
import org.springframework.core.Ordered;

/**
 * zlm 回调处理 注册器
 * <p>
 * 多个注册器存在优先级,请参考{@link org.springframework.core.annotation.Order}或者{@link Ordered}或者{@link org.springframework.core.PriorityOrdered}
 * <p>
 * PriorityOrdered优先于Ordered或者Order
 * <p>
 * 值越小优先级越高
 *
 * @author JunPzx
 * @since 2025/9/29 10:14
 */
public interface ZlmCallbackHandlerRegister {
    /**
     * 注册
     *
     * @param context 上下文
     */
    void register(ZlmCallbackHandlerContext context);

}
