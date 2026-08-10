package com.ss.easymedia.core.handler;

import com.ss.zlm4j.support.SpringUtils;
import org.springframework.core.ResolvableType;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 抽象APP处理器
 *
 * @author JunPzx
 * @since 2025/8/21 17:08
 */
public abstract class AbstractAppHandler<T extends AppHandler> {

    private AppHandlerHolder appHandlerHolder;

    @SuppressWarnings("unchecked")
    public Class<T> getHandlerClass() {
        Class<?> type = ResolvableType.forClass(this.getClass())
                .as(AbstractAppHandler.class)
                .getGeneric(0)
                .resolve();
        if (type == null) {
            throw new IllegalStateException("无法解析APP处理器泛型类型:" + this.getClass().getName());
        }
        return (Class<T>) type;
    }

    /**
     * 获取app处理器
     *
     * @param app app作用域
     * @return app处理器
     */
    public T getHandler(String app) {
        if (null == appHandlerHolder) {
            appHandlerHolder = SpringUtils.getBean(AppHandlerHolder.class);
        }
        return appHandlerHolder.getHandler(getHandlerClass(), app);
    }

    /**
     * 获取app处理器并执行
     *
     * @param app         app作用域
     * @param doSomething 执行方法
     * @param <R>         返回值
     * @return 返回值
     */
    public <R> R doSomethingWithResult(String app, Function<T, R> doSomething) {
        T handler = getHandler(app);
        if (null == handler) {
            return null;
        }
        return doSomething.apply(handler);
    }

    /**
     * 获取app处理器并执行
     *
     * @param app         app作用域
     * @param doSomething 执行方法
     */
    public void doSomething(String app, Consumer<T> doSomething) {
        T handler = getHandler(app);
        if (null == handler) {
            return;
        }
        doSomething.accept(handler);
    }

    /**
     * 获取app处理器并执行
     *
     * @param app          app作用域
     * @param doSomething  执行方法
     * @param defaultValue 默认值
     * @return 默认值
     */
    public <R> R doSomething(String app, Function<T, R> doSomething, R defaultValue) {
        T handler = getHandler(app);
        if (null == handler) {
            return defaultValue;
        }
        return doSomething.apply(handler);
    }
}
