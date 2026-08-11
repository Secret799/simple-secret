package com.ss.security.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpLogic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 验证路由登录拦截器只执行 Sa-Token 登录校验且不记录请求数据。 */
@ExtendWith(OutputCaptureExtension.class)
class LoginRequiredInterceptorTest {

    @Test
    void shouldCheckLoginOnceAndAllowAuthenticatedRequest(CapturedOutput output) {
        CountingStpLogic stpLogic = new CountingStpLogic();
        LoginRequiredInterceptor interceptor = new LoginRequiredInterceptor(stpLogic);
        MockHttpServletRequest request = secretRequest();

        boolean allowed = interceptor.preHandle(
                request, new MockHttpServletResponse(), new Object());

        assertThat(allowed).isTrue();
        assertThat(stpLogic.checkCount()).isOne();
        assertThat(output.getAll()).isBlank();
    }

    @Test
    void shouldPropagateOriginalNotLoginExceptionWithoutLogging(CapturedOutput output) {
        NotLoginException failure = new NotLoginException(
                "exception-marker", "login-type-marker", NotLoginException.INVALID_TOKEN);
        CountingStpLogic stpLogic = new CountingStpLogic(failure);
        LoginRequiredInterceptor interceptor = new LoginRequiredInterceptor(stpLogic);

        assertThatThrownBy(() -> interceptor.preHandle(
                secretRequest(), new MockHttpServletResponse(), new Object()))
                .isSameAs(failure);
        assertThat(stpLogic.checkCount()).isOne();
        assertThat(output.getAll()).isBlank();
    }

    private static MockHttpServletRequest secretRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/uri-marker");
        request.addHeader("X-Token-Marker", "header-marker");
        request.addParameter("parameter-marker", "value-marker");
        return request;
    }

    private static final class CountingStpLogic extends StpLogic {
        private final NotLoginException failure;
        private int checkCount;

        private CountingStpLogic() {
            this(null);
        }

        private CountingStpLogic(NotLoginException failure) {
            super("test");
            this.failure = failure;
        }

        @Override
        public void checkLogin() {
            checkCount++;
            if (failure != null) {
                throw failure;
            }
        }

        private int checkCount() {
            return checkCount;
        }
    }
}
