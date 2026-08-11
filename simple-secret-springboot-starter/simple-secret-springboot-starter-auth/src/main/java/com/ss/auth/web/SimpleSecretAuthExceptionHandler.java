package com.ss.auth.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.ss.auth.exception.AuthException;
import com.ss.core.domain.Result;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Auth starter 可选的 Servlet 认证异常处理器。 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public abstract class SimpleSecretAuthExceptionHandler {

    /**
     * 创建默认认证异常处理器。
     *
     * @return 默认认证异常处理器
     */
    public static SimpleSecretAuthExceptionHandler create() {
        return new DefaultSimpleSecretAuthExceptionHandler();
    }

    /**
     * 将未登录异常转换为固定的未认证响应。
     *
     * @return 不包含认证上下文的固定响应
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLoginException() {
        return response(401, "认证失败，无法访问系统资源");
    }

    /**
     * 将权限不足异常转换为固定的拒绝访问响应。
     *
     * @return 不包含权限信息的固定响应
     */
    @ExceptionHandler(NotPermissionException.class)
    public ResponseEntity<Result<Void>> handleNotPermissionException() {
        return response(403, "没有访问权限");
    }

    /**
     * 将角色不足异常转换为固定的拒绝访问响应。
     *
     * @return 不包含角色信息的固定响应
     */
    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<Result<Void>> handleNotRoleException() {
        return response(403, "没有访问权限");
    }

    /**
     * 将 starter 定义的认证异常转换为对应的固定响应。
     *
     * @param exception 仅用于取得预定义失败原因
     * @return 不包含原始异常消息的固定响应
     */
    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Result<Void>> handleAuthException(AuthException exception) {
        AuthException.Reason reason = exception.getReason();
        return response(reason.getStatus(), reason.getMessage());
    }

    private static ResponseEntity<Result<Void>> response(int status, String message) {
        return ResponseEntity.status(status).body(Result.fail(status, message));
    }

    private static final class DefaultSimpleSecretAuthExceptionHandler
            extends SimpleSecretAuthExceptionHandler {
    }
}
