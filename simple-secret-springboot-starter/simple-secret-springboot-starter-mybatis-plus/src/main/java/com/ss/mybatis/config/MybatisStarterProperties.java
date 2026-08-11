package com.ss.mybatis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Simple Secret MyBatis-Plus 增强配置。 */
@ConfigurationProperties("simple-secret.mybatis")
public final class MybatisStarterProperties {
    private boolean enabled = true;
    private boolean paginationEnabled = true;
    private boolean optimisticLockerEnabled = true;
    private long maxPageSize = 500L;
    private boolean overflow;

    /**
     * 返回是否启用 Simple Secret MyBatis-Plus 增强。
     *
     * @return 启用状态
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用增强。
     *
     * @param enabled 启用状态
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回是否启用分页拦截器。
     *
     * @return 分页拦截器状态
     */
    public boolean isPaginationEnabled() {
        return paginationEnabled;
    }

    /**
     * 设置是否启用分页拦截器。
     *
     * @param paginationEnabled 分页拦截器状态
     */
    public void setPaginationEnabled(boolean paginationEnabled) {
        this.paginationEnabled = paginationEnabled;
    }

    /**
     * 返回是否启用乐观锁拦截器。
     *
     * @return 乐观锁拦截器状态
     */
    public boolean isOptimisticLockerEnabled() {
        return optimisticLockerEnabled;
    }

    /**
     * 设置是否启用乐观锁拦截器。
     *
     * @param optimisticLockerEnabled 乐观锁拦截器状态
     */
    public void setOptimisticLockerEnabled(boolean optimisticLockerEnabled) {
        this.optimisticLockerEnabled = optimisticLockerEnabled;
    }

    /**
     * 返回允许的最大分页大小。
     *
     * @return 最大分页大小
     */
    public long getMaxPageSize() {
        return maxPageSize;
    }

    /**
     * 设置允许的最大分页大小。
     *
     * @param maxPageSize 最大分页大小，必须大于零
     */
    public void setMaxPageSize(long maxPageSize) {
        if (maxPageSize <= 0L) {
            throw new IllegalArgumentException("maxPageSize must be greater than zero");
        }
        this.maxPageSize = maxPageSize;
    }

    /**
     * 返回页码越界时是否回到第一页。
     *
     * @return 越界处理状态
     */
    public boolean isOverflow() {
        return overflow;
    }

    /**
     * 设置页码越界时是否回到第一页。
     *
     * @param overflow 越界处理状态
     */
    public void setOverflow(boolean overflow) {
        this.overflow = overflow;
    }
}
