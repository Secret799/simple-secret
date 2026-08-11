package com.ss.core.exception;

import com.ss.core.validation.AddGroup;
import com.ss.core.validation.EditGroup;
import com.ss.core.validation.QueryGroup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证核心异常与校验分组的稳定公共契约。 */
class CoreExceptionTest {

    @Test
    void shouldKeepI18nCodeArgumentsAndModuleWithoutResolvingSpringMessages() {
        Object[] arguments = {"camera-1", 3};
        BusinessException exception = BusinessException.i18nForModule(
                "media", "stream.offline", arguments);
        BusinessException stringArgument = BusinessException.i18n("stream.offline", "camera-2");
        arguments[0] = "changed";

        assertThat(exception.getModule()).isEqualTo("media");
        assertThat(exception.getCode()).isEqualTo("stream.offline");
        assertThat(exception.getArguments()).containsExactly("camera-1", 3);
        assertThat(exception.getMessage()).isEqualTo("stream.offline");
        assertThat(stringArgument.getModule()).isNull();
        assertThat(stringArgument.getCode()).isEqualTo("stream.offline");
        assertThat(stringArgument.getArguments()).containsExactly("camera-2");

        Object[] returned = exception.getArguments();
        returned[0] = "mutated";
        assertThat(exception.getArguments()).containsExactly("camera-1", 3);
    }

    @Test
    void shouldFormatNormalBusinessMessagesWithJdkOnly() {
        BusinessException exception = BusinessException.normalForModule(
                "mqtt", "topic {} rejected after {} attempts", "devices/1", 2);
        BusinessException extraArguments = BusinessException.normal("failed", 1, 2);
        BusinessException stringArgument = BusinessException.normal("order {} invalid", "A-1");

        assertThat(exception.getModule()).isEqualTo("mqtt");
        assertThat(exception.getCode()).isNull();
        assertThat(exception.getMessage()).isEqualTo("topic devices/1 rejected after 2 attempts");
        assertThat(extraArguments.getMessage()).isEqualTo("failed [1, 2]");
        assertThat(stringArgument.getModule()).isNull();
        assertThat(stringArgument.getMessage()).isEqualTo("order A-1 invalid");
    }

    @Test
    void shouldPreserveServiceExceptionMessageCodeDetailAndCause() {
        IllegalStateException cause = new IllegalStateException("downstream");
        ServiceException formatted = new ServiceException("request {} failed", 503, "a-1")
                .setDetailMessage("connection refused");
        ServiceException wrapped = new ServiceException("operation failed", cause);
        ServiceException nullableCause = new ServiceException("operation failed", null);

        assertThat(formatted.getMessage()).isEqualTo("request a-1 failed");
        assertThat(formatted.getCode()).isEqualTo(503);
        assertThat(formatted.getDetailMessage()).isEqualTo("connection refused");
        assertThat(wrapped.getMessage()).isEqualTo("operation failed");
        assertThat(wrapped.getCause()).isSameAs(cause);
        assertThat(nullableCause.getCause()).isNull();
    }

    @Test
    void shouldExposeDependencyFreeValidationGroups() {
        assertThat(AddGroup.class).isInterface();
        assertThat(EditGroup.class).isInterface();
        assertThat(QueryGroup.class).isInterface();
    }
}
