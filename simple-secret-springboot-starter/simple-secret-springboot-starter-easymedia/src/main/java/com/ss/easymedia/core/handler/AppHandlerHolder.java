package com.ss.easymedia.core.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.OrderComparator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * app处理器程序持有者
 *
 * @author JunPzx
 * @since 2025/8/21 16:44
 */
public class AppHandlerHolder {

    private static final Logger log = LoggerFactory.getLogger(AppHandlerHolder.class);

    private final Map<String, List<AppHandler>> APP_HANDLER = new ConcurrentHashMap<>();

    private final Map<String, Map<Class<?>, AppHandler>> APP_HANDLER_CACHE = new ConcurrentHashMap<>();

    /**
     * 注册app处理器
     *
     * @param appHandlers app处理器
     */
    public void register(List<AppHandler> appHandlers) {
        if (appHandlers == null || appHandlers.isEmpty()) {
            return;
        }
        APP_HANDLER.putAll(appHandlers.stream().collect(Collectors.groupingBy(AppHandler::app)));
    }

    /**
     * 获取app处理器
     *
     * @param clazz app处理器类
     * @param app   app作用域
     * @return app处理器
     */
    @SuppressWarnings("unchecked")
    public <T extends AppHandler> T getHandler(Class<T> clazz, String app) {
        if (APP_HANDLER_CACHE.getOrDefault(app, Collections.emptyMap()).containsKey(clazz)) {
            return (T) APP_HANDLER_CACHE.get(app).get(clazz);
        }
        List<AppHandler> handlers = APP_HANDLER.getOrDefault(app, Collections.emptyList());
        List<T> clazzHandlers = (List<T>) handlers.stream()
                .filter(t -> clazz.isAssignableFrom(t.getClass()))
                .collect(Collectors.toCollection(ArrayList::new));
        if (clazzHandlers.isEmpty()) {
            throw new IllegalArgumentException(String.format(
                    "找不到对应app:%s的%s消息处理器", app, clazz.getSimpleName()));
        }
        OrderComparator.sort(clazzHandlers);
        T handler = clazzHandlers.get(0);
        if (clazzHandlers.size() > 1) {
            log.warn("找到多个app:{}的class:{}处理器,但是只会执行第一个,待执行处理器:{}",
                    app, clazz.getSimpleName(), handler.getClass().getSimpleName());
        }
        APP_HANDLER_CACHE.computeIfAbsent(app, k -> new ConcurrentHashMap<>());
        APP_HANDLER_CACHE.get(app).put(clazz, handler);
        return handler;
    }

}
