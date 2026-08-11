package com.ss.web.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.HandlerMapping;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证请求耗时日志只包含固定的非敏感字段。 */
@ExtendWith(OutputCaptureExtension.class)
class RequestTimingInterceptorTest {

    private static final String URI_SECRET = "uri-secret-marker";
    private static final String QUERY_SECRET = "query-secret-marker";
    private static final String HEADER_SECRET = "header-secret-marker";
    private static final String COOKIE_SECRET = "cookie-secret-marker";
    private static final String PARAMETER_SECRET = "parameter-secret-marker";
    private static final String BODY_SECRET = "body-secret-marker";
    private static final String EXCEPTION_SECRET = "exception-secret-marker";

    private static final Logger TIMING_LOGGER =
            (Logger) LoggerFactory.getLogger(RequestTimingInterceptor.class);

    private Level previousLogLevel;

    @BeforeEach
    void enableDebugLogging() {
        previousLogLevel = TIMING_LOGGER.getLevel();
        TIMING_LOGGER.setLevel(Level.DEBUG);
    }

    @AfterEach
    void restoreLogging() {
        TIMING_LOGGER.setLevel(previousLogLevel);
    }

    @Test
    void shouldLogExistingErrorStatusWithoutMutatingResponseOrSensitiveData(CapturedOutput output)
            throws Exception {
        RequestTimingInterceptor interceptor = new RequestTimingInterceptor(Duration.ofMinutes(1));
        MockHttpServletRequest request = sensitiveRequest();
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/orders/{id}");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(422);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        assertThat(request.getAttribute(startTimeAttribute())).isInstanceOf(Long.class);

        interceptor.afterCompletion(request, response, new Object(),
                new IllegalStateException(EXCEPTION_SECRET));

        assertThat(request.getAttribute(startTimeAttribute())).isNull();
        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(output).contains("DEBUG")
                .contains("method=POST")
                .contains("route=/orders/{id}")
                .contains("status=422")
                .containsPattern("duration=\\d+ms")
                .doesNotContain(URI_SECRET, QUERY_SECRET, HEADER_SECRET, COOKIE_SECRET,
                        PARAMETER_SECRET, BODY_SECRET, EXCEPTION_SECRET);
    }

    @Test
    void shouldUseUnmappedRouteForRequestWithoutBestMatchingPattern(CapturedOutput output)
            throws Exception {
        RequestTimingInterceptor interceptor = new RequestTimingInterceptor(Duration.ofMinutes(1));
        MockHttpServletRequest request = sensitiveRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(output).contains("DEBUG").contains("route=<unmapped>")
                .doesNotContain(URI_SECRET, QUERY_SECRET, HEADER_SECRET, COOKIE_SECRET,
                        PARAMETER_SECRET, BODY_SECRET);
    }

    @Test
    void shouldUseUnmappedRouteForNonStringBestMatchingPattern(CapturedOutput output)
            throws Exception {
        RequestTimingInterceptor interceptor = new RequestTimingInterceptor(Duration.ofMinutes(1));
        MockHttpServletRequest request = sensitiveRequest();
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, 42);
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(output).contains("DEBUG").contains("route=<unmapped>")
                .doesNotContain(URI_SECRET, QUERY_SECRET, HEADER_SECRET, COOKIE_SECRET,
                        PARAMETER_SECRET, BODY_SECRET);
    }

    @Test
    void shouldIgnoreMissingOrWrongTypeStartTimeAttribute() {
        RequestTimingInterceptor interceptor = new RequestTimingInterceptor(Duration.ZERO);
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockHttpServletRequest missingStartTime = new MockHttpServletRequest("GET", "/ignored");
        MockHttpServletRequest wrongTypeStartTime = new MockHttpServletRequest("GET", "/ignored");
        wrongTypeStartTime.setAttribute(
                RequestTimingInterceptor.class.getName() + ".startNanos", "not-a-long");

        assertDoesNotThrow(() -> interceptor.afterCompletion(
                missingStartTime, response, new Object(), null));
        assertDoesNotThrow(() -> interceptor.afterCompletion(
                wrongTypeStartTime, response, new Object(), null));
        assertThat(missingStartTime.getAttribute(startTimeAttribute())).isNull();
        assertThat(wrongTypeStartTime.getAttribute(startTimeAttribute())).isNull();
    }

    @Test
    void shouldLogWarnWhenDurationReachesSlowRequestThreshold(CapturedOutput output)
            throws Exception {
        RequestTimingInterceptor interceptor = new RequestTimingInterceptor(Duration.ZERO);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ignored");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(output).contains("WARN").contains("method=GET")
                .contains("route=/health").contains("status=200");
    }

    @Test
    void shouldKeepOriginalStartTimeWhenPreHandleRunsAgain() throws Exception {
        RequestTimingInterceptor interceptor = new RequestTimingInterceptor(Duration.ofDays(1));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ignored");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        Object firstStartTime = request.getAttribute(startTimeAttribute());
        interceptor.preHandle(request, response, new Object());

        assertThat(request.getAttribute(startTimeAttribute())).isSameAs(firstStartTime);
    }

    @Test
    void shouldRemoveStartTimeAfterFinalCompletion() throws Exception {
        RequestTimingInterceptor interceptor = new RequestTimingInterceptor(Duration.ofDays(1));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ignored");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(request.getAttribute(startTimeAttribute())).isNull();
    }

    @Test
    void shouldLogOneEndToEndWarnForAsyncRequestWithoutSensitiveData(CapturedOutput output)
            throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AsyncTimingController())
                .addInterceptors(new RequestTimingInterceptor(Duration.ofMillis(25)))
                .build();

        MvcResult asyncResult = mockMvc.perform(get("/async/" + URI_SECRET)
                        .queryParam("token", QUERY_SECRET)
                        .header("Authorization", HEADER_SECRET)
                        .cookie(new Cookie("session", COOKIE_SECRET))
                        .param("password", PARAMETER_SECRET)
                        .content(("{\"secret\":\"" + BODY_SECRET + "\"}")
                                .getBytes(StandardCharsets.UTF_8)))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk());

        String logOutput = output.getOut();
        String requestLog = "method=GET, route=/async/{secret}, status=200";
        assertThat(logOutput).contains("WARN").contains(requestLog)
                .containsPattern("duration=(?:[2-9]\\d|[1-9]\\d{2,})ms")
                .doesNotContain(URI_SECRET, QUERY_SECRET, HEADER_SECRET, COOKIE_SECRET,
                        PARAMETER_SECRET, BODY_SECRET, EXCEPTION_SECRET);
        assertThat(countOccurrences(logOutput, requestLog)).isEqualTo(1);
    }

    @Test
    void shouldLogEffectiveServerErrorForUnhandledSynchronousControllerFailureOnce(
            CapturedOutput output) {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FailingTimingController())
                .addInterceptors(new RequestTimingInterceptor(Duration.ZERO))
                .build();

        assertThatThrownBy(() -> mockMvc.perform(get("/sync/" + URI_SECRET)
                        .queryParam("token", QUERY_SECRET)))
                .hasRootCauseInstanceOf(IllegalStateException.class);

        String requestLog = "method=GET, route=/sync/{secret}, status=500";
        String logOutput = output.getOut();
        assertThat(logOutput).contains("WARN").contains(requestLog)
                .doesNotContain(URI_SECRET, QUERY_SECRET, EXCEPTION_SECRET);
        assertThat(countOccurrences(logOutput, requestLog)).isEqualTo(1);
    }

    @Test
    void shouldNotResetTimingOrLogAgainDuringErrorRedispatch(CapturedOutput output)
            throws Exception {
        RequestTimingInterceptor interceptor = new RequestTimingInterceptor(Duration.ZERO);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ignored");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/orders/{id}");
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(),
                new IllegalStateException(EXCEPTION_SECRET));

        request.setDispatcherType(DispatcherType.ERROR);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/error");
        interceptor.preHandle(request, response, new Object());
        interceptor.afterCompletion(request, response, new Object(), null);

        String logOutput = output.getOut();
        assertThat(request.getAttribute(startTimeAttribute())).isNull();
        assertThat(request.getAttribute(completionAttribute())).isEqualTo(Boolean.TRUE);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(logOutput).contains("route=/orders/{id}", "status=500")
                .doesNotContain("route=/error", EXCEPTION_SECRET);
        assertThat(countOccurrences(logOutput, "method=GET")).isEqualTo(1);
    }

    @Test
    void shouldAvoidLogFieldAccessForFastRequestWhenDebugIsDisabled() throws Exception {
        RequestTimingInterceptor interceptor = new RequestTimingInterceptor(Duration.ofDays(1));
        FailOnLogFieldRequest request = new FailOnLogFieldRequest();
        FailOnLogFieldResponse response = new FailOnLogFieldResponse();

        interceptor.preHandle(request, response, new Object());
        TIMING_LOGGER.setLevel(Level.INFO);

        assertDoesNotThrow(() -> interceptor.afterCompletion(request, response, new Object(), null));
        assertThat(request.getAttribute(startTimeAttribute())).isNull();
    }

    @Test
    void shouldSaturateLargePositiveThresholdWithoutReadingLogFields() throws Exception {
        RequestTimingInterceptor interceptor =
                new RequestTimingInterceptor(Duration.ofSeconds(Long.MAX_VALUE));
        FailOnLogFieldRequest request = new FailOnLogFieldRequest();
        FailOnLogFieldResponse response = new FailOnLogFieldResponse();

        interceptor.preHandle(request, response, new Object());
        TIMING_LOGGER.setLevel(Level.INFO);

        assertDoesNotThrow(() -> interceptor.afterCompletion(request, response, new Object(), null));
    }

    private MockHttpServletRequest sensitiveRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/" + URI_SECRET);
        request.setQueryString("token=" + QUERY_SECRET);
        request.addHeader("Authorization", HEADER_SECRET);
        request.setCookies(new Cookie("session", COOKIE_SECRET));
        request.addParameter("password", PARAMETER_SECRET);
        request.setContent(("{\\\"secret\\\":\\\"" + BODY_SECRET + "\\\"}")
                .getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static String startTimeAttribute() {
        return RequestTimingInterceptor.class.getName() + ".startNanos";
    }

    private static String completionAttribute() {
        return RequestTimingInterceptor.class.getName() + ".completionLogged";
    }

    private static int countOccurrences(String value, String target) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }

    @Controller
    private static class AsyncTimingController {

        @GetMapping("/async/{secret}")
        Callable<String> async(@PathVariable String secret) {
            return () -> {
                Thread.sleep(80);
                return "done";
            };
        }
    }

    @Controller
    private static class FailingTimingController {

        @GetMapping("/sync/{secret}")
        String fail(@PathVariable String secret) {
            throw new IllegalStateException(EXCEPTION_SECRET);
        }
    }

    private static class FailOnLogFieldRequest extends MockHttpServletRequest {

        FailOnLogFieldRequest() {
            super("GET", "/ignored");
        }

        @Override
        public String getMethod() {
            throw new AssertionError("Method must not be read when DEBUG is disabled.");
        }

        @Override
        public Object getAttribute(String name) {
            if (HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE.equals(name)) {
                throw new AssertionError("Route must not be read when DEBUG is disabled.");
            }
            return super.getAttribute(name);
        }
    }

    private static class FailOnLogFieldResponse extends MockHttpServletResponse {

        @Override
        public int getStatus() {
            throw new AssertionError("Status must not be read when DEBUG is disabled.");
        }
    }
}
