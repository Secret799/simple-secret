package com.ss.mybatis.cache;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ss.common.toolbox.cache.ValueComparator;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 面向字符串状态字段的 MyBatis-Plus 过期缓存管理器。
 *
 * @param <T> 实体类型
 * @param <S> MyBatis-Plus Service 类型
 */
public abstract class MybatisPlusStatusFieldCacheManager<T, S extends IService<T>>
        extends MybatisPlusFieldCacheManager<T, S, String, String> {
    /** 默认上线状态。 */
    public static final String DEFAULT_UP_STATUS = "1";
    /** 默认下线状态。 */
    public static final String DEFAULT_DOWN_STATUS = "0";

    private Consumer<String> upCallback;
    private Consumer<String> downCallback;

    /**
     * 创建状态字段缓存管理器。
     *
     * @param entityType 实体类型
     * @param defaultTtl 默认过期时间
     */
    protected MybatisPlusStatusFieldCacheManager(Class<T> entityType, Duration defaultTtl) {
        super(entityType, defaultTtl, ValueComparator.natural());
        onValueChanged((key, value) -> {
            if (Objects.equals(value, upStatus())) {
                if (upCallback != null) {
                    upCallback.accept(key);
                }
            } else if (Objects.equals(value, downStatus()) && downCallback != null) {
                downCallback.accept(key);
            }
        });
    }

    /**
     * 记录上线状态。
     *
     * @param key                 实体 key
     * @param updateStoreOnChange 是否同步更新数据库
     */
    public void record(String key, boolean updateStoreOnChange) {
        put(key, upStatus(), updateStoreOnChange);
    }

    /**
     * 记录下线状态。
     *
     * @param key                 实体 key
     * @param updateStoreOnChange 是否同步更新数据库
     */
    public void cancel(String key, boolean updateStoreOnChange) {
        put(key, downStatus(), updateStoreOnChange);
    }

    /**
     * 注册上线回调。
     *
     * @param callback 回调
     */
    public final void onUp(Consumer<String> callback) {
        this.upCallback = callback;
    }

    /**
     * 注册下线回调。
     *
     * @param callback 回调
     */
    public final void onDown(Consumer<String> callback) {
        this.downCallback = callback;
    }

    /**
     * 返回上线状态值。
     *
     * @return 上线状态值
     */
    protected String upStatus() {
        return DEFAULT_UP_STATUS;
    }

    /**
     * 返回下线状态值。
     *
     * @return 下线状态值
     */
    protected String downStatus() {
        return DEFAULT_DOWN_STATUS;
    }
}
