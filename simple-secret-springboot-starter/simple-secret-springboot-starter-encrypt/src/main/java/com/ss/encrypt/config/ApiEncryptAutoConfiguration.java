package com.ss.encrypt.config;

import com.ss.encrypt.core.EncryptionAlgorithm;
import com.ss.encrypt.core.EncryptionMaterial;
import com.ss.encrypt.core.EncryptionService;
import com.ss.encrypt.key.EncryptionKeyProvider;
import com.ss.encrypt.web.ApiEncryptAnnotationResolver;
import com.ss.encrypt.web.ApiEncryptionFailureHandler;
import com.ss.encrypt.web.ApiEncryptionFilter;
import com.ss.encrypt.web.ApiEncryptionProtocol;
import com.ss.encrypt.web.DefaultApiEncryptionFailureHandler;
import com.ss.encrypt.web.MvcApiEncryptAnnotationResolver;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** 可选 Servlet API v1 密文传输自动配置。 */
@AutoConfiguration(after = SimpleSecretEncryptAutoConfiguration.class)
@ConditionalOnClass({Filter.class, RequestMappingHandlerMapping.class})
@ConditionalOnBean(EncryptionService.class)
@ConditionalOnProperty(
        prefix = "simple-secret.encrypt",
        name = {"enabled", "api.enabled"},
        havingValue = "true")
public class ApiEncryptAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ApiEncryptionProtocol apiEncryptionProtocol(
            EncryptionService encryptionService) {
        return new ApiEncryptionProtocol(encryptionService);
    }

    @Bean
    @ConditionalOnMissingBean
    ApiEncryptionFailureHandler apiEncryptionFailureHandler() {
        return new DefaultApiEncryptionFailureHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    ApiEncryptAnnotationResolver apiEncryptAnnotationResolver(
            RequestMappingHandlerMapping handlerMapping) {
        return new MvcApiEncryptAnnotationResolver(handlerMapping);
    }

    @Bean
    @ConditionalOnMissingBean
    ApiEncryptionFilter apiEncryptionFilter(
            ApiEncryptAnnotationResolver annotationResolver,
            ApiEncryptionProtocol protocol,
            ApiEncryptionFailureHandler failureHandler,
            EncryptionKeyProvider keyProvider,
            EncryptProperties properties) {
        validate(properties.getApi(), keyProvider);
        return new ApiEncryptionFilter(annotationResolver, protocol,
                properties.getApi(), failureHandler);
    }

    @Bean
    @ConditionalOnMissingBean(name = "simpleSecretApiEncryptionFilterRegistration")
    FilterRegistrationBean<ApiEncryptionFilter>
            simpleSecretApiEncryptionFilterRegistration(ApiEncryptionFilter filter) {
        FilterRegistrationBean<ApiEncryptionFilter> registration =
                new FilterRegistrationBean<>();
        registration.setName("simpleSecretApiEncryptionFilter");
        registration.setFilter(filter);
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.addUrlPatterns("/*");
        registration.setOrder(FilterRegistrationBean.HIGHEST_PRECEDENCE);
        return registration;
    }

    private static void validate(
            EncryptProperties.Api api,
            EncryptionKeyProvider keyProvider) {
        if (api.getRequestKeyId() == null || api.getRequestKeyId().isBlank()) {
            throw new IllegalArgumentException(
                    "simple-secret.encrypt.api.request-key-id is required");
        }
        if (api.getResponseKeyId() == null || api.getResponseKeyId().isBlank()) {
            throw new IllegalArgumentException(
                    "simple-secret.encrypt.api.response-key-id is required");
        }
        if (api.getHeaderName() == null || api.getHeaderName().isBlank()) {
            throw new IllegalArgumentException(
                    "simple-secret.encrypt.api.header-name is required");
        }
        if (api.getMaxRequestSize() == null
                || api.getMaxRequestSize().toBytes() <= 0
                || api.getMaxResponseSize() == null
                || api.getMaxResponseSize().toBytes() <= 0) {
            throw new IllegalArgumentException(
                    "API encryption size limits must be positive");
        }
        EncryptionMaterial request = keyProvider.resolve(
                api.getRequestKeyId(), EncryptionAlgorithm.RSA_OAEP_SHA256);
        EncryptionMaterial response = keyProvider.resolve(
                api.getResponseKeyId(), EncryptionAlgorithm.RSA_OAEP_SHA256);
        if (request.privateKey() == null) {
            throw new IllegalArgumentException(
                    "API request key id must provide an RSA private key");
        }
        if (response.publicKey() == null) {
            throw new IllegalArgumentException(
                    "API response key id must provide an RSA public key");
        }
        validatePrivateKey(request.privateKey(), api.getRequestKeyId());
        validatePublicKey(response.publicKey(), api.getResponseKeyId());
    }

    private static void validatePrivateKey(String value, String keyId) {
        try {
            KeyFactory.getInstance("RSA").generatePrivate(
                    new PKCS8EncodedKeySpec(decodeKey(value)));
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "RSA private key format is invalid for key id '" + keyId + "'",
                    exception);
        }
    }

    private static void validatePublicKey(String value, String keyId) {
        try {
            KeyFactory.getInstance("RSA").generatePublic(
                    new X509EncodedKeySpec(decodeKey(value)));
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "RSA public key format is invalid for key id '" + keyId + "'",
                    exception);
        }
    }

    private static byte[] decodeKey(String value) {
        String normalized = value
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }
}
