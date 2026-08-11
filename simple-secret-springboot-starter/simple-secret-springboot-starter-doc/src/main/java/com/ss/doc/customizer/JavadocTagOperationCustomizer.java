package com.ss.doc.customizer;

import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.providers.JavadocProvider;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.List;

/** 使用类 Javadoc 第一条非空文本增强 Operation 标签。 */
public final class JavadocTagOperationCustomizer implements OperationCustomizer {

    private final JavadocProvider javadocProvider;

    /**
     * 创建 customizer。
     *
     * @param javadocProvider 可选的 springdoc Javadoc provider
     */
    public JavadocTagOperationCustomizer(JavadocProvider javadocProvider) {
        this.javadocProvider = javadocProvider;
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (javadocProvider == null) {
            return operation;
        }
        String documentation = javadocProvider.getClassJavadoc(handlerMethod.getBeanType());
        String tag = firstNonBlankLine(documentation);
        if (tag == null) {
            return operation;
        }
        List<String> tags = operation.getTags();
        if (tags == null) {
            tags = new ArrayList<>();
        } else if (tags.contains(tag)) {
            return operation;
        } else {
            tags = new ArrayList<>(tags);
        }
        tags.add(tag);
        operation.setTags(tags);
        return operation;
    }

    private static String firstNonBlankLine(String documentation) {
        if (documentation == null) {
            return null;
        }
        return documentation.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElse(null);
    }
}
