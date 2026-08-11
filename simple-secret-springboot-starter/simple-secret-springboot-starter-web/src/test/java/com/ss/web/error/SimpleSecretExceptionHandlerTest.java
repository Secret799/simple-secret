package com.ss.web.error;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ss.core.exception.BaseException;
import com.ss.core.exception.BusinessException;
import com.ss.core.exception.ServiceException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.context.MessageSource;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证默认异常处理器的公开响应边界。 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleSecretExceptionHandlerTest {

    private static final String BASE_HANDLER =
            "com.ss.web.error.SimpleSecretExceptionHandler";
    private static final String VALIDATION_HANDLER =
            "com.ss.web.error.SimpleSecretValidationExceptionHandler";

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeAll
    void setUp() throws Exception {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("web.invalid", Locale.SIMPLIFIED_CHINESE, "参数 {0} 错误");
        messageSource.addMessage("web.invalid", Locale.ENGLISH, "Invalid {0}");
        objectMapper = new ObjectMapper();

        mockMvc = MockMvcBuilders.standaloneSetup(new FailureController())
                .setControllerAdvice(
                        SimpleSecretExceptionHandler.create(messageSource),
                        SimpleSecretValidationExceptionHandler.create())
                .setValidator(new ValidationRequestValidator())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void shouldPreserveServiceHttpStatusCode() throws Exception {
        assertResult(mockMvc.perform(get("/fail/service-http"))
                .andExpect(status().isUnprocessableEntity())
                .andReturn(), 422, "bad");
    }

    @Test
    void shouldUseBadRequestForNonHttpServiceCode() throws Exception {
        assertResult(mockMvc.perform(get("/fail/service-domain"))
                .andExpect(status().isBadRequest())
                .andReturn(), 9001, "bad");
    }

    @Test
    void shouldPreserveNonStandardClientErrorStatusCode() throws Exception {
        assertResult(mockMvc.perform(get("/fail/service-499"))
                .andExpect(status().is(499))
                .andReturn(), 499, "bad");
    }

    @Test
    void shouldPreserveNonStandardServerErrorStatusCode() throws Exception {
        String response = mockMvc.perform(get("/fail/service-599"))
                .andExpect(status().is(599))
                .andReturn().getResponse().getContentAsString();

        assertSafeServerError(response, 599);
    }

    @Test
    void shouldNeverExposeServiceMessageForInternalServerError() throws Exception {
        String response = mockMvc.perform(get("/fail/service-500"))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString();

        assertSafeServerError(response, 500);
    }

    @Test
    void shouldNeverExposeServiceMessageWithoutCodeOrCause() throws Exception {
        String response = mockMvc.perform(get("/fail/service-no-code"))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString();

        assertSafeServerError(response, 500);
    }

    @Test
    void shouldUseSafeServiceFallbackWithoutCodeOrPublicMessage() throws Exception {
        assertResult(mockMvc.perform(get("/fail/service-default"))
                .andExpect(status().isInternalServerError())
                .andReturn(), 500, "服务执行失败");
    }

    @Test
    void shouldNeverExposeServiceCauseMessage() throws Exception {
        String response = mockMvc.perform(get("/fail/service-cause"))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString();

        JsonNode result = objectMapper.readTree(response);
        assertThat(result.path("code").asInt()).isEqualTo(500);
        assertThat(result.path("message").asText()).isEqualTo("服务执行失败");
        assertThat(response).doesNotContain("secret-marker");
    }

    @Test
    void shouldNeverExposeServiceDetailMessage() throws Exception {
        String response = mockMvc.perform(get("/fail/service-detail"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        JsonNode result = objectMapper.readTree(response);
        assertThat(result.path("code").asInt()).isEqualTo(400);
        assertThat(result.path("message").asText()).isEqualTo("公开消息");
        assertThat(response).doesNotContain("内部诊断详情");
    }

    @Test
    void shouldResolveBaseExceptionWithRequestLocale() throws Exception {
        assertResult(mockMvc.perform(get("/fail/base-localized")
                        .locale(Locale.SIMPLIFIED_CHINESE))
                .andExpect(status().isInternalServerError())
                .andReturn(), 500, "参数 name 错误");
    }

    @Test
    void shouldFallBackToSafeBaseExceptionMessage() throws Exception {
        assertResult(mockMvc.perform(get("/fail/base-fallback"))
                .andExpect(status().isInternalServerError())
                .andReturn(), 500, "安全回退消息");
    }

    @Test
    void shouldNotEchoMissingInternationalizationCode() throws Exception {
        String response = mockMvc.perform(get("/fail/base-i18n-missing"))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString();

        JsonNode result = objectMapper.readTree(response);
        assertThat(result.path("code").asInt()).isEqualTo(500);
        assertThat(result.path("message").asText()).isEqualTo("请求处理失败");
        assertThat(response).doesNotContain("internal.message.key");
    }

    @Test
    void shouldPreserveNormalBaseExceptionPublicMessage() throws Exception {
        assertResult(mockMvc.perform(get("/fail/base-normal"))
                .andExpect(status().isInternalServerError())
                .andReturn(), 500, "公开业务消息");
    }

    @Test
    void shouldHandleTypeMismatchWithoutEchoingRequestValue() throws Exception {
        String response = mockMvc.perform(get("/fail/type-mismatch")
                        .param("count", "private-request-value"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        JsonNode result = objectMapper.readTree(response);
        assertThat(result.path("code").asInt()).isEqualTo(400);
        assertThat(result.path("message").asText()).isEqualTo("请求参数类型错误");
        assertThat(response).doesNotContain("private-request-value");
    }

    @Test
    void shouldJoinBindingMessagesInDeclarationOrder() throws Exception {
        assertResult(mockMvc.perform(get("/fail/binding"))
                .andExpect(status().isBadRequest())
                .andReturn(), 400, "第一个错误, 第二个错误");
    }

    @Test
    void shouldSanitizeRealModelAttributeBindingFailure() throws Exception {
        String response = mockMvc.perform(get("/fail/model-binding")
                        .param("count", "secret-non-numeric-value"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        JsonNode result = objectMapper.readTree(response);
        assertThat(result.path("code").asInt()).isEqualTo(400);
        assertThat(result.path("message").asText()).isEqualTo("请求参数无效");
        assertThat(response)
                .doesNotContain("secret-non-numeric-value")
                .doesNotContain("count")
                .doesNotContain("int")
                .doesNotContain("ConversionFailed")
                .doesNotContain("NumberFormat");
    }

    @Test
    void shouldJoinRealModelAttributeValidationMessagesInBindingOrder() throws Exception {
        assertResult(mockMvc.perform(get("/fail/model-validation")
                        .param("first", "")
                        .param("second", ""))
                .andExpect(status().isBadRequest())
                .andReturn(), 400, "乙校验错误, 甲校验错误");
    }

    @Test
    void shouldJoinMethodValidationMessagesInDeclarationOrder() throws Exception {
        assertResult(mockMvc.perform(get("/fail/method-validation"))
                .andExpect(status().isBadRequest())
                .andReturn(), 400, "校验一, 校验二");
    }

    @Test
    void shouldSortAndJoinNonblankConstraintViolationMessages() throws Exception {
        assertResult(mockMvc.perform(get("/fail/constraint"))
                .andExpect(status().isBadRequest())
                .andReturn(), 400, "约束一, 约束二");
    }

    @Test
    void shouldUseValidationFallbackWhenMessagesAreEmpty() throws Exception {
        assertResult(mockMvc.perform(get("/fail/constraint-empty"))
                .andExpect(status().isBadRequest())
                .andReturn(), 400, "请求参数无效");
    }

    @Test
    void shouldHandleMethodNotSupported() throws Exception {
        assertResult(mockMvc.perform(post("/fail/only-get"))
                .andExpect(status().isMethodNotAllowed())
                .andReturn(), 405, "请求方法不支持");
    }

    @Test
    void shouldHandleMissingResource() throws Exception {
        assertResult(mockMvc.perform(get("/fail/missing-resource"))
                .andExpect(status().isNotFound())
                .andReturn(), 404, "请求资源不存在");
    }

    @Test
    void shouldSanitizeMissingPathVariable() throws Exception {
        String response = mockMvc.perform(get("/fail/missing-path-variable"))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString();

        JsonNode result = objectMapper.readTree(response);
        assertThat(result.path("code").asInt()).isEqualTo(500);
        assertThat(result.path("message").asText()).isEqualTo("请求处理失败");
        assertThat(response)
                .doesNotContain("private-path-variable")
                .doesNotContain("MissingPathVariableException")
                .doesNotContain("Required URI template variable");
    }

    @Test
    void shouldNotHandleArbitraryRuntimeException() {
        assertThatThrownBy(() -> mockMvc.perform(get("/fail/runtime")))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("internal-stack-marker");
    }

    @Test
    void shouldUseFocusedLowPriorityControllerAdvice() throws Exception {
        for (String className : Set.of(BASE_HANDLER, VALIDATION_HANDLER)) {
            Class<?> handlerType = Class.forName(className);
            assertThat(handlerType).hasAnnotation(RestControllerAdvice.class);
            assertThat(handlerType.getAnnotation(Order.class).value())
                    .isEqualTo(Ordered.LOWEST_PRECEDENCE);
            assertThat(handlerType.getDeclaredMethods())
                    .filteredOn(method -> method.isAnnotationPresent(ExceptionHandler.class))
                    .flatExtracting(SimpleSecretExceptionHandlerTest::handledTypes)
                    .doesNotContain(Exception.class, RuntimeException.class);
        }
    }

    private static Set<Class<?>> handledTypes(Method method) {
        ExceptionHandler annotation = method.getAnnotation(ExceptionHandler.class);
        Set<Class<?>> types = new LinkedHashSet<>(Set.of(annotation.value()));
        if (types.isEmpty()) {
            for (Class<?> parameterType : method.getParameterTypes()) {
                if (Throwable.class.isAssignableFrom(parameterType)) {
                    types.add(parameterType);
                }
            }
        }
        return types;
    }

    private void assertResult(
            org.springframework.test.web.servlet.MvcResult mvcResult, int code, String message)
            throws Exception {
        JsonNode result = objectMapper.readTree(mvcResult.getResponse().getContentAsString());
        assertThat(result.path("code").asInt()).isEqualTo(code);
        assertThat(result.path("message").asText()).isEqualTo(message);
        assertThat(result.path("data").isNull()).isTrue();
    }

    private void assertSafeServerError(String response, int code) throws Exception {
        JsonNode result = objectMapper.readTree(response);
        assertThat(result.path("code").asInt()).isEqualTo(code);
        assertThat(result.path("message").asText()).isEqualTo("服务执行失败");
        assertThat(response).doesNotContain("internal-service-marker");
    }

    @RestController
    static class FailureController {

        @GetMapping("/fail/service-http")
        void serviceHttp() {
            throw new ServiceException("bad", 422);
        }

        @GetMapping("/fail/service-domain")
        void serviceDomain() {
            throw new ServiceException("bad", 9001);
        }

        @GetMapping("/fail/service-499")
        void service499() {
            throw new ServiceException("bad", 499);
        }

        @GetMapping("/fail/service-599")
        void service599() {
            throw new ServiceException("internal-service-marker", 599);
        }

        @GetMapping("/fail/service-500")
        void service500() {
            throw new ServiceException("internal-service-marker", 500);
        }

        @GetMapping("/fail/service-no-code")
        void serviceWithoutCode() {
            throw new ServiceException("internal-service-marker");
        }

        @GetMapping("/fail/service-default")
        void serviceDefault() {
            throw new ServiceException(" ");
        }

        @GetMapping("/fail/service-cause")
        void serviceCause() {
            throw new ServiceException(new IllegalStateException("secret-marker"));
        }

        @GetMapping("/fail/service-detail")
        void serviceDetail() {
            throw new ServiceException("公开消息", 400).setDetailMessage("内部诊断详情");
        }

        @GetMapping("/fail/base-localized")
        void baseLocalized() {
            throw new TestBaseException("web.invalid", new Object[]{"name"}, "安全默认消息");
        }

        @GetMapping("/fail/base-fallback")
        void baseFallback() {
            throw new TestBaseException("web.missing", new Object[0], "安全回退消息");
        }

        @GetMapping("/fail/base-i18n-missing")
        void baseI18nMissing() {
            throw BusinessException.i18n("internal.message.key");
        }

        @GetMapping("/fail/base-normal")
        void baseNormal() {
            throw BusinessException.normal("公开业务消息");
        }

        @GetMapping("/fail/type-mismatch")
        void typeMismatch(@RequestParam int count) {
        }

        @GetMapping("/fail/binding")
        void binding() throws BindException {
            BindException exception = new BindException(new Object(), "request");
            addValidationMessages(exception, "第一个错误", null, " ", "第二个错误");
            throw exception;
        }

        @GetMapping("/fail/model-binding")
        void modelBinding(@ModelAttribute IntegerRequest request) {
        }

        @GetMapping("/fail/model-validation")
        void modelValidation(@Valid @ModelAttribute ValidationRequest request) {
        }

        @GetMapping("/fail/method-validation")
        void methodValidation() throws Exception {
            BeanPropertyBindingResult errors =
                    new BeanPropertyBindingResult(new Object(), "request");
            addValidationMessages(errors, "校验一", "", "校验二");
            Method method = FailureController.class.getDeclaredMethod("methodValidation");
            throw new MethodArgumentNotValidException(new MethodParameter(method, -1), errors);
        }

        @GetMapping("/fail/constraint")
        void constraint() {
            throw constraintViolation("约束二", null, "  ", "约束一");
        }

        @GetMapping("/fail/constraint-empty")
        void constraintEmpty() {
            throw constraintViolation(null, " ");
        }

        @GetMapping("/fail/only-get")
        void onlyGet() {
        }

        @GetMapping("/fail/missing-resource")
        void missingResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/private/path");
        }

        @GetMapping("/fail/missing-path-variable")
        void missingPathVariable(@PathVariable("private-path-variable") String ignored) {
        }

        @GetMapping("/fail/runtime")
        void runtime() {
            throw new IllegalStateException("internal-stack-marker");
        }

        private static ConstraintViolationException constraintViolation(String... messages) {
            Set<ConstraintViolation<?>> violations = new LinkedHashSet<>();
            for (String message : messages) {
                violations.add(constraintViolation(message));
            }
            return new ConstraintViolationException(violations);
        }

        private static ConstraintViolation<?> constraintViolation(String message) {
            InvocationHandler handler = (proxy, method, arguments) -> switch (method.getName()) {
                case "getMessage" -> message;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                case "toString" -> String.valueOf(message);
                default -> defaultValue(method.getReturnType());
            };
            return (ConstraintViolation<?>) Proxy.newProxyInstance(
                    ConstraintViolation.class.getClassLoader(),
                    new Class<?>[]{ConstraintViolation.class}, handler);
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == char.class) {
                return '\0';
            }
            return 0;
        }

        private static void addValidationMessages(
                BindingResult errors, String... messages) {
            for (String message : messages) {
                errors.addError(new ObjectError("request", message));
            }
        }
    }

    static class IntegerRequest {

        private int count;

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    static class ValidationRequest {

        private String first;

        private String second;

        public String getFirst() {
            return first;
        }

        public void setFirst(String first) {
            this.first = first;
        }

        public String getSecond() {
            return second;
        }

        public void setSecond(String second) {
            this.second = second;
        }
    }

    private static final class ValidationRequestValidator implements Validator {

        @Override
        public boolean supports(Class<?> clazz) {
            return ValidationRequest.class == clazz;
        }

        @Override
        public void validate(Object target, Errors errors) {
            errors.rejectValue("first", "validation.first", "乙校验错误");
            errors.rejectValue("second", "validation.second", "甲校验错误");
        }
    }

    private static final class TestBaseException extends BaseException {

        private TestBaseException(
                String code, Object[] arguments, String defaultMessage) {
            super("web", code, arguments, defaultMessage);
        }
    }

}
