package com.ss.idempotent.key;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.ss.idempotent.exception.RepeatSubmitException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** 使用稳定 JSON 和 SHA-256 生成不含请求原文的幂等 key。 */
public final class DefaultIdempotencyKeyGenerator implements IdempotencyKeyGenerator {

    private static final Object FILTERED = new Object();

    private final JsonMapper objectMapper;
    private final RequestIdentityResolver identityResolver;
    private final String keyPrefix;

    /**
     * 创建 key generator。
     *
     * @param identityResolver 请求身份 resolver
     * @param keyPrefix 存储 key 前缀
     */
    public DefaultIdempotencyKeyGenerator(
            RequestIdentityResolver identityResolver,
            String keyPrefix) {
        this.objectMapper = JsonMapper.builder()
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .findAndAddModules()
                .build();
        this.identityResolver = Objects.requireNonNull(identityResolver, "identityResolver");
        String prefix = Objects.requireNonNull(keyPrefix, "keyPrefix").trim();
        if (prefix.isEmpty()) {
            throw new IllegalArgumentException("Idempotency key prefix must not be blank.");
        }
        this.keyPrefix = prefix;
    }

    @Override
    public String generate(Method method, Object[] args, HttpServletRequest request) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(request, "request");
        try {
            List<Object> sanitizedArguments = sanitizeArguments(args);
            String serializedArguments = objectMapper.writeValueAsString(sanitizedArguments);
            String material = join(
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getQueryString(),
                    method.toGenericString(),
                    identityResolver.resolve(request),
                    serializedArguments);
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return keyPrefix + HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException exception) {
            throw new RepeatSubmitException("Unable to generate idempotency key.", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private static List<Object> sanitizeArguments(Object[] args) {
        List<Object> values = new ArrayList<>();
        if (args == null) {
            return values;
        }
        for (Object argument : args) {
            Object sanitized = sanitize(argument);
            if (sanitized != FILTERED) {
                values.add(sanitized);
            }
        }
        return values;
    }

    private static Object sanitize(Object value) {
        if (value == null) {
            return null;
        }
        if (isInfrastructureValue(value)) {
            return FILTERED;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            List<Object> values = new ArrayList<>(Array.getLength(value));
            for (int index = 0; index < Array.getLength(value); index++) {
                addSanitized(values, Array.get(value, index));
            }
            return values;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> values = new ArrayList<>(collection.size());
            collection.forEach(item -> addSanitized(values, item));
            return values;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> values = new TreeMap<>();
            map.forEach((key, item) -> {
                Object sanitized = sanitize(item);
                if (sanitized != FILTERED) {
                    values.put(String.valueOf(key), sanitized);
                }
            });
            return values;
        }
        return value;
    }

    private static void addSanitized(List<Object> values, Object value) {
        Object sanitized = sanitize(value);
        if (sanitized != FILTERED) {
            values.add(sanitized);
        }
    }

    private static boolean isInfrastructureValue(Object value) {
        return value instanceof MultipartFile
                || value instanceof HttpServletRequest
                || value instanceof HttpServletResponse
                || value instanceof BindingResult;
    }

    private static String join(String... parts) {
        StringBuilder value = new StringBuilder();
        for (String part : parts) {
            String normalized = part == null ? "" : part;
            value.append(normalized.length()).append(':').append(normalized);
        }
        return value.toString();
    }
}
