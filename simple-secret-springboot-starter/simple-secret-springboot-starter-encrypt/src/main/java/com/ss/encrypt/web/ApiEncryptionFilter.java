package com.ss.encrypt.web;

import com.ss.encrypt.annotation.ApiEncrypt;
import com.ss.encrypt.config.EncryptProperties;
import com.ss.encrypt.core.EncryptionException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** 仅处理显式 {@link ApiEncrypt} 端点的 Servlet filter。 */
public final class ApiEncryptionFilter implements Filter {

    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");

    private final ApiEncryptAnnotationResolver annotationResolver;
    private final ApiEncryptionProtocol protocol;
    private final EncryptProperties.Api properties;
    private final ApiEncryptionFailureHandler failureHandler;

    public ApiEncryptionFilter(
            ApiEncryptAnnotationResolver annotationResolver,
            ApiEncryptionProtocol protocol,
            EncryptProperties.Api properties,
            ApiEncryptionFailureHandler failureHandler) {
        this.annotationResolver = java.util.Objects.requireNonNull(
                annotationResolver, "annotationResolver");
        this.protocol = java.util.Objects.requireNonNull(protocol, "protocol");
        this.properties = java.util.Objects.requireNonNull(properties, "properties");
        this.failureHandler = java.util.Objects.requireNonNull(
                failureHandler, "failureHandler");
    }

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        ApiEncrypt annotation;
        try {
            annotation = annotationResolver.resolve(httpRequest);
        } catch (Exception exception) {
            throw new ServletException("Cannot resolve API encryption annotation", exception);
        }
        if (annotation == null) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest requestToUse = httpRequest;
        if (annotation.request() && BODY_METHODS.contains(httpRequest.getMethod())) {
            String keyHeader = httpRequest.getHeader(properties.getHeaderName());
            if (keyHeader == null || keyHeader.isBlank()) {
                failureHandler.handle(httpRequest, httpResponse,
                        ApiEncryptionFailureReason.MISSING_KEY_HEADER);
                return;
            }
            try {
                byte[] encryptedBody = readLimited(httpRequest,
                        properties.getMaxRequestSize().toBytes());
                String plaintext = protocol.decrypt(
                        keyHeader,
                        new String(encryptedBody, StandardCharsets.UTF_8),
                        properties.getRequestKeyId());
                requestToUse = new DecryptRequestBodyWrapper(
                        httpRequest, plaintext.getBytes(StandardCharsets.UTF_8));
            } catch (ApiPayloadTooLargeException exception) {
                failureHandler.handle(httpRequest, httpResponse,
                        ApiEncryptionFailureReason.PAYLOAD_TOO_LARGE);
                return;
            } catch (EncryptionException exception) {
                failureHandler.handle(httpRequest, httpResponse,
                        ApiEncryptionFailureReason.INVALID_REQUEST_PAYLOAD);
                return;
            }
        }

        if (!annotation.response()) {
            chain.doFilter(requestToUse, response);
            return;
        }

        EncryptResponseBodyWrapper wrapper = new EncryptResponseBodyWrapper(
                httpResponse, properties.getMaxResponseSize().toBytes());
        try {
            chain.doFilter(requestToUse, wrapper);
            ApiEncryptedPayload encrypted = protocol.encrypt(
                    wrapper.bodyAsString(), properties.getResponseKeyId());
            byte[] body = encrypted.body().getBytes(StandardCharsets.UTF_8);
            httpResponse.setHeader(properties.getHeaderName(), encrypted.keyHeader());
            httpResponse.setContentType("text/plain");
            httpResponse.setCharacterEncoding(StandardCharsets.UTF_8.name());
            httpResponse.setContentLength(body.length);
            httpResponse.getOutputStream().write(body);
        } catch (ApiPayloadTooLargeException exception) {
            failureHandler.handle(httpRequest, httpResponse,
                    ApiEncryptionFailureReason.PAYLOAD_TOO_LARGE);
        } catch (EncryptionException exception) {
            failureHandler.handle(httpRequest, httpResponse,
                    ApiEncryptionFailureReason.RESPONSE_ENCRYPTION_FAILED);
        }
    }

    private static byte[] readLimited(
            HttpServletRequest request, long maxBytes) throws IOException {
        if (maxBytes <= 0 || maxBytes >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "max request size must be between 1 and Integer.MAX_VALUE - 1");
        }
        byte[] value = request.getInputStream().readNBytes((int) maxBytes + 1);
        if (value.length > maxBytes) {
            throw new ApiPayloadTooLargeException();
        }
        return value;
    }
}
