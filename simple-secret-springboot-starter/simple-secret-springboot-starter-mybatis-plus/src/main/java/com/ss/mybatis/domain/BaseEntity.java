package com.ss.mybatis.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/** 仅承载持久化审计字段的实体基类。 */
public abstract class BaseEntity implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableField(fill = FieldFill.INSERT)
    private Long createDept;

    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** @return 创建部门标识 */
    public Long getCreateDept() {
        return createDept;
    }

    /** @param createDept 创建部门标识 */
    public void setCreateDept(Long createDept) {
        this.createDept = createDept;
    }

    /** @return 创建人标识 */
    public Long getCreateBy() {
        return createBy;
    }

    /** @param createBy 创建人标识 */
    public void setCreateBy(Long createBy) {
        this.createBy = createBy;
    }

    /** @return 创建时间 */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /** @param createTime 创建时间 */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    /** @return 更新人标识 */
    public Long getUpdateBy() {
        return updateBy;
    }

    /** @param updateBy 更新人标识 */
    public void setUpdateBy(Long updateBy) {
        this.updateBy = updateBy;
    }

    /** @return 更新时间 */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    /** @param updateTime 更新时间 */
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
