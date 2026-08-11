package com.ss.encrypt.mybatis;

import com.ss.encrypt.annotation.EncryptField;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisDecryptInterceptorTest {

    @Test
    void shouldDecryptReturnedEntities() throws Throwable {
        Entity entity = new Entity();
        EncryptedObjectProcessor processor = TestProcessors.processor();
        EncryptedObjectProcessor.RestorationScope scope = processor.encrypt(entity);
        String encrypted = entity.secret;
        scope.close();
        entity.secret = encrypted;
        ResultSetHandler handler = new FixedResultSetHandler(List.of(entity));
        Method method = ResultSetHandler.class.getMethod(
                "handleResultSets", Statement.class);

        Object result = new MybatisDecryptInterceptor(processor).intercept(
                new Invocation(handler, method, new Object[] {null}));

        assertThat(result).isEqualTo(List.of(entity));
        assertThat(entity.secret).isEqualTo("plain");
    }

    static final class Entity {
        @EncryptField
        String secret = "plain";
    }

    static final class FixedResultSetHandler implements ResultSetHandler {
        private final List<?> result;

        FixedResultSetHandler(List<?> result) {
            this.result = result;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <E> List<E> handleResultSets(Statement statement) {
            return (List<E>) result;
        }

        @Override
        public <E> Cursor<E> handleCursorResultSets(Statement statement) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void handleOutputParameters(CallableStatement callableStatement) {
            throw new UnsupportedOperationException();
        }
    }
}
