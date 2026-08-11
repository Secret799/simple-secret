package com.ss.mybatis.cache;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ss.common.toolbox.cache.DatabaseExpiringCacheManager;
import com.ss.common.toolbox.cache.ValueComparator;
import org.apache.ibatis.reflection.property.PropertyNamer;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 使用 MyBatis-Plus {@link IService} 更新指定实体字段的过期缓存管理器。
 *
 * @param <T> 实体类型
 * @param <S> MyBatis-Plus Service 类型
 * @param <K> key 类型
 * @param <V> value 类型
 */
public abstract class MybatisPlusFieldCacheManager<T, S extends IService<T>, K, V>
        extends DatabaseExpiringCacheManager<K, V> {
    private final Class<T> entityType;
    private final Clock clock;

    private volatile String keyColumn;
    private volatile String valueColumn;
    private volatile String updateTimeColumn;
    private volatile boolean updateTimeResolved;

    /**
     * 创建字段缓存管理器。
     *
     * @param entityType 实体类型
     * @param defaultTtl 默认过期时间
     * @param comparator 值比较器
     */
    protected MybatisPlusFieldCacheManager(Class<T> entityType, Duration defaultTtl,
                                           ValueComparator<V> comparator) {
        this(entityType, defaultTtl, comparator, Clock.systemDefaultZone());
    }

    /**
     * 使用指定时钟创建字段缓存管理器，便于业务统一时间来源。
     *
     * @param entityType 实体类型
     * @param defaultTtl 默认过期时间
     * @param comparator 值比较器
     * @param clock      更新时间字段使用的时钟
     */
    protected MybatisPlusFieldCacheManager(Class<T> entityType, Duration defaultTtl,
                                           ValueComparator<V> comparator, Clock clock) {
        super(defaultTtl, comparator);
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 获取更新数据库使用的 Service。
     *
     * @return MyBatis-Plus Service
     */
    protected abstract S service();

    /**
     * 获取缓存 key 对应的实体 getter。
     *
     * @return key 字段 getter
     */
    protected abstract SFunction<T, ?> keyField();

    /**
     * 获取缓存值对应的实体 getter。
     *
     * @return value 字段 getter
     */
    protected abstract SFunction<T, ?> valueField();

    /**
     * 获取更新时间字段 getter。返回空值表示不更新该字段。
     *
     * @return 更新时间字段 getter 或 {@code null}
     */
    protected SFunction<T, LocalDateTime> updateTimeField() {
        return null;
    }

    /**
     * 按 key 更新值字段和可选更新时间字段。
     */
    @Override
    protected boolean updateStore(K key, V value) {
        UpdateWrapper<T> wrapper = new UpdateWrapper<>();
        wrapper.eq(keyColumn(), key).set(valueColumn(), value);
        String timeColumn = updateTimeColumn();
        if (timeColumn != null) {
            wrapper.set(timeColumn, LocalDateTime.now(clock));
        }
        return Objects.requireNonNull(service(), "service").update(wrapper);
    }

    private String keyColumn() {
        String resolved = keyColumn;
        if (resolved == null) {
            synchronized (this) {
                resolved = keyColumn;
                if (resolved == null) {
                    keyColumn = resolved = resolveColumn(keyField());
                }
            }
        }
        return resolved;
    }

    private String valueColumn() {
        String resolved = valueColumn;
        if (resolved == null) {
            synchronized (this) {
                resolved = valueColumn;
                if (resolved == null) {
                    valueColumn = resolved = resolveColumn(valueField());
                }
            }
        }
        return resolved;
    }

    private String updateTimeColumn() {
        if (!updateTimeResolved) {
            synchronized (this) {
                if (!updateTimeResolved) {
                    SFunction<T, LocalDateTime> getter = updateTimeField();
                    updateTimeColumn = getter == null ? null : resolveColumn(getter);
                    updateTimeResolved = true;
                }
            }
        }
        return updateTimeColumn;
    }

    private String resolveColumn(SFunction<T, ?> getter) {
        Objects.requireNonNull(getter, "field getter");
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entityType);
        if (tableInfo == null) {
            throw new IllegalStateException("MyBatis-Plus table metadata is not initialized for "
                    + entityType.getName());
        }
        String property = PropertyNamer.methodToProperty(
                LambdaUtils.extract(getter).getImplMethodName());
        if (property.equals(tableInfo.getKeyProperty())) {
            return tableInfo.getKeyColumn();
        }
        return tableInfo.getFieldList().stream()
                .filter(field -> property.equals(field.getProperty()))
                .map(TableFieldInfo::getColumn)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No MyBatis-Plus column mapping for property '" + property
                                + "' on " + entityType.getName()));
    }
}
