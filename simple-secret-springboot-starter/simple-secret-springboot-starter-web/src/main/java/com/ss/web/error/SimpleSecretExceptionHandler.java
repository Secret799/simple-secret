package com.ss.web.error;

import com.ss.core.domain.Result;
import com.ss.core.exception.BaseException;
import com.ss.core.exception.ServiceException;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/** Simple Secret WebMVC 默认异常处理器。 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public abstract class SimpleSecretExceptionHandler {

    private static final String INVALID_REQUEST_MESSAGE = "请求参数无效";

    private final MessageSource messageSource;

    /**
     * 创建默认异常处理器。
     *
     * @param messageSource 国际化消息源
     */
    protected SimpleSecretExceptionHandler(MessageSource messageSource) {
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource");
    }

    /** 创建默认异常处理器。 */
    public static SimpleSecretExceptionHandler create(MessageSource messageSource) {
        return new DefaultSimpleSecretExceptionHandler(messageSource);
    }

    /** 处理服务执行异常。 */
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<Result<Void>> handleServiceException(ServiceException exception) {
        Integer code = exception.getCode();
        if (code == null) {
            return response(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "服务执行失败");
        }
        int httpStatus = code >= 400 && code <= 599 ? code : HttpStatus.BAD_REQUEST.value();
        String message = httpStatus >= 500
                ? "服务执行失败"
                : publicMessage(exception.getMessage(), "服务执行失败");
        return response(httpStatus, code, message);
    }

    /** 处理可国际化的基础业务异常。 */
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<Result<Void>> handleBaseException(
            BaseException exception, Locale locale) {
        String fallback = baseExceptionFallback(exception);
        String message = fallback;
        if (exception.getCode() != null && !exception.getCode().isBlank()) {
            message = messageSource.getMessage(
                    exception.getCode(), exception.getArguments(), fallback, locale);
        }
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                publicMessage(message, fallback));
    }

    /** 处理请求参数类型不匹配。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch() {
        return response(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.value(),
                "请求参数类型错误");
    }

    /** 处理参数绑定与方法参数校验异常。 */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException exception) {
        if (hasBindingFailure(exception)) {
            return response(
                    HttpStatus.BAD_REQUEST.value(),
                    HttpStatus.BAD_REQUEST.value(),
                    INVALID_REQUEST_MESSAGE);
        }
        return response(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.value(),
                validationMessage(exception.getAllErrors().stream()
                        .map(MessageSourceResolvable::getDefaultMessage)));
    }

    /** 处理缺失路径变量。 */
    @ExceptionHandler(MissingPathVariableException.class)
    public ResponseEntity<Result<Void>> handleMissingPathVariable() {
        return response(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "请求处理失败");
    }

    /** 处理控制器方法级参数校验异常。 */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleMethodValidationException(
            HandlerMethodValidationException exception) {
        return response(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.value(),
                validationMessage(exception.getAllErrors().stream()
                        .map(MessageSourceResolvable::getDefaultMessage)));
    }

    /** 处理缺失参数和不可读取的请求体。 */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<Result<Void>> handleInvalidRequest() {
        return response(
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.value(),
                INVALID_REQUEST_MESSAGE);
    }

    /** 处理不支持的请求方法。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported() {
        return response(
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                HttpStatus.METHOD_NOT_ALLOWED.value(),
                "请求方法不支持");
    }

    /** 处理未找到的静态或控制器资源。 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleMissingResource() {
        return response(
                HttpStatus.NOT_FOUND.value(),
                HttpStatus.NOT_FOUND.value(),
                "请求资源不存在");
    }

    static String validationMessage(Stream<String> messages) {
        String message = messages
                .filter(Objects::nonNull)
                .filter(candidate -> !candidate.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
        return publicMessage(message, INVALID_REQUEST_MESSAGE);
    }

    private static boolean hasBindingFailure(BindException exception) {
        return exception.getAllErrors().stream()
                .filter(FieldError.class::isInstance)
                .map(FieldError.class::cast)
                .anyMatch(FieldError::isBindingFailure);
    }

    private static String publicMessage(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : message;
    }

    private static String baseExceptionFallback(BaseException exception) {
        String message = exception.getMessage();
        String code = exception.getCode();
        if (code != null && !code.isBlank() && code.equals(message)) {
            return "请求处理失败";
        }
        return publicMessage(message, "请求处理失败");
    }

    static ResponseEntity<Result<Void>> response(int httpStatus, int code, String message) {
        return ResponseEntity.status(httpStatus).body(Result.fail(code, message));
    }

    private static final class DefaultSimpleSecretExceptionHandler
            extends SimpleSecretExceptionHandler {

        private DefaultSimpleSecretExceptionHandler(MessageSource messageSource) {
            super(messageSource);
        }
    }
}
