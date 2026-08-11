package com.ss.encrypt.mybatis;

import com.ss.encrypt.annotation.EncryptField;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MybatisEncryptInterceptorTest {

    @Test
    void shouldExposeCiphertextOnlyDuringSetParametersAndRestoreAfterSuccess()
            throws Throwable {
        Entity entity = new Entity();
        RecordingParameterHandler handler = new RecordingParameterHandler(entity, false);
        Invocation invocation = invocation(handler);

        new MybatisEncryptInterceptor(TestProcessors.processor()).intercept(invocation);

        assertThat(handler.observed).isNotEqualTo("plain");
        assertThat(entity.secret).isEqualTo("plain");
    }

    @Test
    void shouldRestorePlaintextWhenMybatisThrows() throws Exception {
        Entity entity = new Entity();
        RecordingParameterHandler handler = new RecordingParameterHandler(entity, true);

        assertThatThrownBy(() -> new MybatisEncryptInterceptor(
                        TestProcessors.processor()).intercept(invocation(handler)))
                .isInstanceOf(Throwable.class);
        assertThat(entity.secret).isEqualTo("plain");
    }

    private static Invocation invocation(ParameterHandler handler) throws Exception {
        Method method = ParameterHandler.class.getMethod(
                "setParameters", PreparedStatement.class);
        return new Invocation(handler, method, new Object[] {null});
    }

    static final class Entity {
        @EncryptField
        String secret = "plain";
    }

    static final class RecordingParameterHandler implements ParameterHandler {
        private final Entity entity;
        private final boolean fail;
        private String observed;

        RecordingParameterHandler(Entity entity, boolean fail) {
            this.entity = entity;
            this.fail = fail;
        }

        @Override
        public Object getParameterObject() {
            return entity;
        }

        @Override
        public void setParameters(PreparedStatement preparedStatement) {
            observed = entity.secret;
            if (fail) {
                throw new IllegalStateException("database failed");
            }
        }
    }
}
