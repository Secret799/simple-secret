package com.ss.auth.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ss.auth.exception.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** 验证 Auth starter 默认异常处理器的固定公开响应边界。 */
class SimpleSecretAuthExceptionHandlerTest {

    private static final String SECRET_PERMISSION = "secret-permission";
    private static final String SECRET_ROLE = "secret-role";
    private static final String SECRET_TOKEN = "secret-token";
    private static final String SECRET_LOGIN_TYPE = "secret-login-type";
    private static final String EXCEPTION_MESSAGE_MARKER = "secret-exception-marker";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(SimpleSecretAuthExceptionHandler.create())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void shouldReturnFixedSafeResponsesForAuthenticationFailures() throws Exception {
        assertResult("/auth/not-login", 401, "认证失败，无法访问系统资源");
        assertResult("/auth/not-permission", 403, "没有访问权限");
        assertResult("/auth/not-role", 403, "没有访问权限");
        assertResult("/auth/invalid-request", 400, "认证请求无效");
        assertResult("/auth/unsupported-grant", 400, "认证方式不受支持");
        assertResult("/auth/client-unavailable", 401, "认证失败");
    }

    private void assertResult(String path, int status, String message) throws Exception {
        MvcResult result = mockMvc.perform(get(path)).andReturn();
        String body = result.getResponse().getContentAsString();
        JsonNode response = objectMapper.readTree(body);

        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(response.path("code").asInt()).isEqualTo(status);
        assertThat(response.path("message").asText()).isEqualTo(message);
        assertThat(body)
                .doesNotContain(SECRET_PERMISSION)
                .doesNotContain(SECRET_ROLE)
                .doesNotContain(SECRET_TOKEN)
                .doesNotContain(SECRET_LOGIN_TYPE)
                .doesNotContain(EXCEPTION_MESSAGE_MARKER);
    }

    @RestController
    static class FailureController {

        @GetMapping("/auth/not-login")
        void notLogin() {
            throw NotLoginException.newInstance(
                    SECRET_LOGIN_TYPE,
                    SECRET_TOKEN,
                    EXCEPTION_MESSAGE_MARKER,
                    EXCEPTION_MESSAGE_MARKER);
        }

        @GetMapping("/auth/not-permission")
        void notPermission() {
            throw new NotPermissionException(SECRET_PERMISSION, SECRET_LOGIN_TYPE);
        }

        @GetMapping("/auth/not-role")
        void notRole() {
            throw new NotRoleException(SECRET_ROLE, SECRET_LOGIN_TYPE);
        }

        @GetMapping("/auth/invalid-request")
        void invalidRequest() {
            throw new AuthException(AuthException.Reason.INVALID_REQUEST);
        }

        @GetMapping("/auth/unsupported-grant")
        void unsupportedGrant() {
            throw new AuthException(AuthException.Reason.UNSUPPORTED_GRANT);
        }

        @GetMapping("/auth/client-unavailable")
        void clientUnavailable() {
            throw new AuthException(AuthException.Reason.CLIENT_UNAVAILABLE);
        }
    }
}
