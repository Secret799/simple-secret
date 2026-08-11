package com.ss.encrypt.mybatis;

import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

import java.sql.Statement;

/** 解密 MyBatis 返回实体中的注解字段。 */
@Intercepts(@Signature(
        type = ResultSetHandler.class,
        method = "handleResultSets",
        args = Statement.class))
public final class MybatisDecryptInterceptor implements Interceptor {

    private final EncryptedObjectProcessor processor;

    public MybatisDecryptInterceptor(EncryptedObjectProcessor processor) {
        this.processor = java.util.Objects.requireNonNull(processor, "processor");
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object result = invocation.proceed();
        processor.decrypt(result);
        return result;
    }
}
