package com.ss.tenant.domain;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.ss.mybatis.domain.BaseEntity;

/** 带租户标识的持久化基础实体。 */
public class TenantEntity extends BaseEntity {
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private String tenantId;

    /**
     * 返回实体所属租户。
     *
     * @return 租户标识
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * 设置实体所属租户。
     *
     * @param tenantId 租户标识
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
