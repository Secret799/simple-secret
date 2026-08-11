package com.ss.encrypt.web;

import com.ss.encrypt.annotation.ApiEncrypt;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** 使用 Spring MVC handler mapping 解析方法优先、类型回退的注解。 */
public final class MvcApiEncryptAnnotationResolver
        implements ApiEncryptAnnotationResolver {

    private final RequestMappingHandlerMapping handlerMapping;

    public MvcApiEncryptAnnotationResolver(
            RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = java.util.Objects.requireNonNull(
                handlerMapping, "handlerMapping");
    }

    @Override
    public ApiEncrypt resolve(HttpServletRequest request) throws Exception {
        HandlerExecutionChain chain = handlerMapping.getHandler(request);
        if (chain == null || !(chain.getHandler() instanceof HandlerMethod method)) {
            return null;
        }
        ApiEncrypt annotation = AnnotatedElementUtils.findMergedAnnotation(
                method.getMethod(), ApiEncrypt.class);
        return annotation != null ? annotation
                : AnnotatedElementUtils.findMergedAnnotation(
                        method.getBeanType(), ApiEncrypt.class);
    }
}
