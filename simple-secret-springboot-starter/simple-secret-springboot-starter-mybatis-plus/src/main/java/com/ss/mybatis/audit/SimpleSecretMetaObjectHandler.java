package com.ss.mybatis.audit;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.ss.mybatis.domain.BaseEntity;
import org.apache.ibatis.reflection.MetaObject;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;

/** 使用可替换审计上下文填充 {@link BaseEntity}。 */
public final class SimpleSecretMetaObjectHandler implements MetaObjectHandler {
    private final AuditContextProvider contextProvider;
    private final Clock clock;

    /**
     * 使用系统时钟创建处理器。
     *
     * @param contextProvider 审计上下文 provider
     */
    public SimpleSecretMetaObjectHandler(AuditContextProvider contextProvider) {
        this(contextProvider, Clock.systemDefaultZone());
    }

    /**
     * 使用指定时钟创建处理器。
     *
     * @param contextProvider 审计上下文 provider
     * @param clock 时间来源
     */
    public SimpleSecretMetaObjectHandler(AuditContextProvider contextProvider, Clock clock) {
        this.contextProvider = Objects.requireNonNull(contextProvider, "contextProvider");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** {@inheritDoc} */
    @Override
    public void insertFill(MetaObject metaObject) {
        BaseEntity entity = entity(metaObject);
        if (entity == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (entity.getCreateTime() == null) {
            entity.setCreateTime(now);
        }
        if (entity.getUpdateTime() == null) {
            entity.setUpdateTime(entity.getCreateTime());
        }

        AuditContext context = currentContext();
        if (context.actorId() != null) {
            if (entity.getCreateBy() == null) {
                entity.setCreateBy(context.actorId());
            }
            if (entity.getUpdateBy() == null) {
                entity.setUpdateBy(context.actorId());
            }
        }
        if (entity.getCreateDept() == null && context.departmentId() != null) {
            entity.setCreateDept(context.departmentId());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void updateFill(MetaObject metaObject) {
        BaseEntity entity = entity(metaObject);
        if (entity == null) {
            return;
        }
        entity.setUpdateTime(LocalDateTime.now(clock));
        Long actorId = currentContext().actorId();
        if (actorId != null) {
            entity.setUpdateBy(actorId);
        }
    }

    private AuditContext currentContext() {
        AuditContext context = contextProvider.current();
        return context == null ? AuditContext.empty() : context;
    }

    private static BaseEntity entity(MetaObject metaObject) {
        if (metaObject == null) {
            return null;
        }
        Object original = metaObject.getOriginalObject();
        return original instanceof BaseEntity baseEntity ? baseEntity : null;
    }
}
