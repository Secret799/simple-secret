package com.ss.web.error;

import com.ss.core.domain.Result;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

/** Jakarta Validation 存在时启用的约束校验异常处理器。 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public abstract class SimpleSecretValidationExceptionHandler {

    /** 创建 Jakarta Validation 约束校验异常处理器。 */
    public static SimpleSecretValidationExceptionHandler create() {
        return new DefaultSimpleSecretValidationExceptionHandler();
    }

    /** 处理方法级约束校验异常。 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolationException(
            ConstraintViolationException exception) {
        String message = SimpleSecretExceptionHandler.validationMessage(
                exception.getConstraintViolations().stream()
                        .map(ConstraintViolation::getMessage)
                        .filter(Objects::nonNull)
                        .filter(candidate -> !candidate.isBlank())
                        .sorted());
        return SimpleSecretExceptionHandler.response(
                HttpStatus.BAD_REQUEST.value(), HttpStatus.BAD_REQUEST.value(), message);
    }

    private static final class DefaultSimpleSecretValidationExceptionHandler
            extends SimpleSecretValidationExceptionHandler {
    }
}
