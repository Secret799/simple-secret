package com.ss.encrypt.mybatis;

import com.ss.encrypt.annotation.EncryptField;
import com.ss.encrypt.config.EncryptProperties;
import com.ss.encrypt.core.EncryptionException;
import com.ss.encrypt.core.EncryptionRequest;
import com.ss.encrypt.core.EncryptionService;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** 按运行时对象发现加密字段，并安全处理 MyBatis 参数或结果对象。 */
public final class EncryptedObjectProcessor {

    private final EncryptionService encryptionService;
    private final EncryptProperties.Mybatis defaults;
    private final ConcurrentMap<Class<?>, List<EncryptedFieldMetadata>> fieldCache =
            new ConcurrentHashMap<>();

    public EncryptedObjectProcessor(
            EncryptionService encryptionService,
            EncryptProperties.Mybatis defaults) {
        this.encryptionService = java.util.Objects.requireNonNull(
                encryptionService, "encryptionService");
        this.defaults = java.util.Objects.requireNonNull(defaults, "defaults");
    }

    /**
     * 加密对象中的字段并返回恢复作用域。
     *
     * <p>调用方必须在 MyBatis 完成参数绑定后关闭作用域。</p>
     */
    public RestorationScope encrypt(Object root) {
        List<Mutation> mutations = new ArrayList<>();
        try {
            visit(root, true, mutations, identitySet());
            return new DefaultRestorationScope(mutations);
        } catch (RuntimeException exception) {
            try {
                restore(mutations);
            } catch (RuntimeException restoreFailure) {
                exception.addSuppressed(restoreFailure);
            }
            throw exception;
        }
    }

    /** 解密 MyBatis 返回对象中的全部注解字段。 */
    public void decrypt(Object root) {
        visit(root, false, new ArrayList<>(), identitySet());
    }

    private void visit(
            Object value,
            boolean encrypt,
            List<Mutation> mutations,
            Set<Object> visited) {
        if (value == null || !visited.add(value)) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(item -> visit(item, encrypt, mutations, visited));
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> visit(item, encrypt, mutations, visited));
            return;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                visit(Array.get(value, index), encrypt, mutations, visited);
            }
            return;
        }
        for (EncryptedFieldMetadata metadata : fields(value.getClass())) {
            transform(value, metadata, encrypt, mutations);
        }
    }

    private void transform(
            Object target,
            EncryptedFieldMetadata metadata,
            boolean encrypt,
            List<Mutation> mutations) {
        Field field = metadata.field();
        try {
            String current = (String) field.get(target);
            if (current == null) {
                return;
            }
            EncryptionRequest request = metadata.request(defaults);
            String transformed = encrypt
                    ? encryptionService.encrypt(current, request)
                    : encryptionService.decrypt(current, request);
            if (encrypt) {
                mutations.add(new Mutation(target, field, current));
            }
            field.set(target, transformed);
        } catch (IllegalAccessException exception) {
            throw new EncryptionException(
                    "Cannot access encrypted field '" + field.getName() + "'", exception);
        }
    }

    private List<EncryptedFieldMetadata> fields(Class<?> type) {
        return fieldCache.computeIfAbsent(type, this::inspectFields);
    }

    private List<EncryptedFieldMetadata> inspectFields(Class<?> sourceType) {
        List<EncryptedFieldMetadata> result = new ArrayList<>();
        for (Class<?> type = sourceType;
                type != null && type != Object.class;
                type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                EncryptField annotation = field.getAnnotation(EncryptField.class);
                if (annotation == null) {
                    continue;
                }
                if (field.getType() != String.class) {
                    throw new IllegalStateException("@EncryptField field '"
                            + field.getName() + "' must be a String");
                }
                if (Modifier.isStatic(field.getModifiers())) {
                    throw new IllegalStateException("@EncryptField field '"
                            + field.getName() + "' must not be static");
                }
                if (Modifier.isFinal(field.getModifiers())) {
                    throw new IllegalStateException("@EncryptField field '"
                            + field.getName() + "' must not be final");
                }
                if (!field.trySetAccessible()) {
                    throw new IllegalStateException("@EncryptField field '"
                            + field.getName() + "' is not accessible");
                }
                result.add(new EncryptedFieldMetadata(field, annotation));
            }
        }
        return List.copyOf(result);
    }

    private static Set<Object> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static void restore(List<Mutation> mutations) {
        for (int index = mutations.size() - 1; index >= 0; index--) {
            Mutation mutation = mutations.get(index);
            try {
                mutation.field().set(mutation.target(), mutation.originalValue());
            } catch (IllegalAccessException exception) {
                throw new EncryptionException("Cannot restore encrypted field '"
                        + mutation.field().getName() + "'", exception);
            }
        }
    }

    /** MyBatis 参数绑定完成后恢复业务对象原值的作用域。 */
    @FunctionalInterface
    public interface RestorationScope extends AutoCloseable {

        @Override
        void close();
    }

    private record Mutation(Object target, Field field, String originalValue) {
    }

    private static final class DefaultRestorationScope implements RestorationScope {
        private final List<Mutation> mutations;
        private final AtomicBoolean closed = new AtomicBoolean();

        private DefaultRestorationScope(List<Mutation> mutations) {
            this.mutations = List.copyOf(mutations);
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                restore(mutations);
            }
        }
    }
}
