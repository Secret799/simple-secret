package com.ss.encrypt.mybatis;

import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

import java.sql.PreparedStatement;

/** 在 MyBatis 参数绑定窗口内加密注解字段，并保证恢复调用方对象。 */
@Intercepts(@Signature(
        type = ParameterHandler.class,
        method = "setParameters",
        args = PreparedStatement.class))
public final class MybatisEncryptInterceptor implements Interceptor {

    private final EncryptedObjectProcessor processor;

    public MybatisEncryptInterceptor(EncryptedObjectProcessor processor) {
        this.processor = java.util.Objects.requireNonNull(processor, "processor");
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        ParameterHandler handler = (ParameterHandler) invocation.getTarget();
        EncryptedObjectProcessor.RestorationScope scope =
                processor.encrypt(handler.getParameterObject());
        Throwable failure = null;
        try {
            return invocation.proceed();
        } catch (Throwable exception) {
            failure = exception;
            throw exception;
        } finally {
            try {
                scope.close();
            } catch (RuntimeException restoreFailure) {
                if (failure != null) {
                    failure.addSuppressed(restoreFailure);
                } else {
                    throw restoreFailure;
                }
            }
        }
    }
}
