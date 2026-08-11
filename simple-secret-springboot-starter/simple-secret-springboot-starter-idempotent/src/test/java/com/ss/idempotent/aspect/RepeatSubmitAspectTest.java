package com.ss.idempotent.aspect;

import com.ss.idempotent.annotation.RepeatSubmit;
import com.ss.idempotent.exception.RepeatSubmitException;
import com.ss.idempotent.key.IdempotencyKeyGenerator;
import com.ss.idempotent.store.IdempotencyStore;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证切面使用调用栈局部租约，并按返回或异常决定释放。 */
class RepeatSubmitAspectTest {

    @AfterEach
    void resetRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldKeepSuccessfulLeaseAndRejectDuplicate() throws Throwable {
        RecordingStore store = new RecordingStore(true);
        RepeatSubmitAspect aspect = aspect(store, messageSource());
        Method method = Sample.class.getDeclaredMethod("submit");

        assertThat(aspect.around(joinPoint(method, "created"), annotation(method)))
                .isEqualTo("created");
        assertThat(store.releases).isEmpty();

        RecordingStore duplicate = new RecordingStore(false);
        RepeatSubmitAspect duplicateAspect = aspect(duplicate, messageSource());
        assertThatThrownBy(() -> duplicateAspect.around(
                joinPoint(method, "ignored"), annotation(method)))
                .isInstanceOf(RepeatSubmitException.class)
                .hasMessage("Please do not submit repeatedly");
    }

    @Test
    void shouldReleaseOnlyOwnedLeaseWhenInvocationFails() throws Throwable {
        RecordingStore store = new RecordingStore(true);
        RepeatSubmitAspect aspect = aspect(store, messageSource());
        Method method = Sample.class.getDeclaredMethod("submit");
        ProceedingJoinPoint point = joinPointThrowing(method, new IllegalStateException("failed"));

        assertThatThrownBy(() -> aspect.around(point, annotation(method)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("failed");
        assertThat(store.releases).hasSize(1);
        assertThat(store.releases.get(0).key()).isEqualTo("fixed-key");
        assertThat(store.releases.get(0).owner()).isEqualTo(store.owners.get(0));
    }

    @Test
    void shouldHonorNoReleaseAndRejectIntervalsBelowOneSecond() throws Exception {
        RecordingStore store = new RecordingStore(true);
        RepeatSubmitAspect aspect = aspect(store, messageSource());
        Method noRelease = Sample.class.getDeclaredMethod("noRelease");

        assertThatThrownBy(() -> aspect.around(
                joinPointThrowing(noRelease, new IllegalArgumentException("failed")),
                annotation(noRelease))).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.releases).isEmpty();

        Method tooShort = Sample.class.getDeclaredMethod("tooShort");
        assertThatThrownBy(() -> aspect.around(joinPoint(tooShort, "ignored"), annotation(tooShort)))
                .isInstanceOf(RepeatSubmitException.class)
                .hasMessageContaining("1 second");
    }

    private static RepeatSubmitAspect aspect(
            IdempotencyStore store,
            StaticMessageSource messageSource) {
        IdempotencyKeyGenerator generator = (method, args, request) -> "fixed-key";
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest("POST", "/orders")));
        return new RepeatSubmitAspect(store, generator, messageSource);
    }

    private static StaticMessageSource messageSource() {
        StaticMessageSource source = new StaticMessageSource();
        source.addMessage("repeat.submit.message", Locale.getDefault(),
                "Please do not submit repeatedly");
        return source;
    }

    private static RepeatSubmit annotation(Method method) {
        return method.getAnnotation(RepeatSubmit.class);
    }

    private static ProceedingJoinPoint joinPoint(Method method, Object result) throws Throwable {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(point.getSignature()).thenReturn(signature);
        when(point.getArgs()).thenReturn(new Object[0]);
        when(point.proceed()).thenReturn(result);
        return point;
    }

    private static ProceedingJoinPoint joinPointThrowing(Method method, Throwable failure)
            throws Throwable {
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(point.getSignature()).thenReturn(signature);
        when(point.getArgs()).thenReturn(new Object[0]);
        when(point.proceed()).thenThrow(failure);
        return point;
    }

    private record Release(String key, String owner) {
    }

    private static final class RecordingStore implements IdempotencyStore {

        private final boolean acquire;
        private final List<String> owners = new ArrayList<>();
        private final List<Release> releases = new ArrayList<>();

        private RecordingStore(boolean acquire) {
            this.acquire = acquire;
        }

        @Override
        public boolean tryAcquire(String key, String owner, Duration ttl) {
            owners.add(owner);
            return acquire;
        }

        @Override
        public boolean release(String key, String owner) {
            releases.add(new Release(key, owner));
            return true;
        }
    }

    private static class Sample {

        @RepeatSubmit
        void submit() {
        }

        @RepeatSubmit(releaseOnException = false)
        void noRelease() {
        }

        @RepeatSubmit(interval = 500)
        void tooShort() {
        }
    }
}
