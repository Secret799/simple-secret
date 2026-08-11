package com.ss.doc.customizer;

import io.swagger.v3.oas.models.Operation;
import org.junit.jupiter.api.Test;
import org.springdoc.core.providers.JavadocProvider;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证可选类 Javadoc Operation 标签增强。 */
class JavadocTagOperationCustomizerTest {

    private final HandlerMethod handlerMethod;

    JavadocTagOperationCustomizerTest() throws NoSuchMethodException {
        handlerMethod = new HandlerMethod(new Probe(), Probe.class.getMethod("run"));
    }

    @Test
    void shouldRemainNoopWithoutProviderOrWithBlankJavadoc() {
        Operation operation = new Operation();

        assertThat(new JavadocTagOperationCustomizer(null)
                .customize(operation, handlerMethod)).isSameAs(operation);
        assertThat(operation.getTags()).isNull();

        new JavadocTagOperationCustomizer(new StubJavadocProvider(" \r\n\t"))
                .customize(operation, handlerMethod);
        assertThat(operation.getTags()).isNull();
    }

    @Test
    void shouldUseFirstNonBlankLineAndAvoidDuplicateTags() {
        Operation operation = new Operation().addTagsItem("Existing");
        JavadocTagOperationCustomizer customizer = new JavadocTagOperationCustomizer(
                new StubJavadocProvider("\r\n Device operations \nDetailed description"));

        assertThat(customizer.customize(operation, handlerMethod)).isSameAs(operation);
        assertThat(operation.getTags()).containsExactly("Existing", "Device operations");

        customizer.customize(operation, handlerMethod);
        assertThat(operation.getTags()).containsExactly("Existing", "Device operations");
    }

    @Test
    void shouldAppendTagWhenOperationHasImmutableTags() {
        Operation operation = new Operation().tags(List.of("Existing"));
        JavadocTagOperationCustomizer customizer = new JavadocTagOperationCustomizer(
                new StubJavadocProvider("Device operations"));

        customizer.customize(operation, handlerMethod);

        assertThat(operation.getTags()).containsExactly("Existing", "Device operations");

        customizer.customize(operation, handlerMethod);
        assertThat(operation.getTags()).containsExactly("Existing", "Device operations");
    }

    static class Probe {
        public void run() {
        }
    }

    private record StubJavadocProvider(String classJavadoc) implements JavadocProvider {

        @Override
        public String getClassJavadoc(Class<?> type) {
            return classJavadoc;
        }

        @Override
        public Map<String, String> getRecordClassParamJavadoc(Class<?> type) {
            return Map.of();
        }

        @Override
        public String getMethodJavadocDescription(Method method) {
            return null;
        }

        @Override
        public String getMethodJavadocReturn(Method method) {
            return null;
        }

        @Override
        public Map<String, String> getMethodJavadocThrows(Method method) {
            return Map.of();
        }

        @Override
        public String getParamJavadoc(Method method, String name) {
            return null;
        }

        @Override
        public String getFieldJavadoc(Field field) {
            return null;
        }

        @Override
        public String getFirstSentence(String text) {
            return text;
        }
    }
}
