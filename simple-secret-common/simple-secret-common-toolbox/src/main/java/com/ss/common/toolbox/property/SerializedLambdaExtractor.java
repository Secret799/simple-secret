package com.ss.common.toolbox.property;

import com.ss.common.toolbox.function.SerializableFunction;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 从可序列化方法引用中提取 JDK {@link SerializedLambda} 元数据。
 */
final class SerializedLambdaExtractor {
    private SerializedLambdaExtractor() {
    }

    /**
     * 优先通过合成的 {@code writeReplace} 方法提取元数据，失败时使用 Java 序列化回退。
     *
     * @param function 可序列化方法引用
     * @return lambda 元数据
     */
    static SerializedLambda extract(SerializableFunction<?, ?> function) {
        if (function == null) {
            throw new LambdaResolutionException("Getter function must not be null");
        }
        try {
            Method method = function.getClass().getDeclaredMethod("writeReplace");
            method.setAccessible(true);
            return (SerializedLambda) method.invoke(function);
        } catch (ReflectiveOperationException | RuntimeException reflectionFailure) {
            try {
                return extractUsingSerialization(function);
            } catch (RuntimeException serializationFailure) {
                serializationFailure.addSuppressed(reflectionFailure);
                throw serializationFailure;
            }
        }
    }

    /**
     * 通过对象替换钩子捕获序列化过程中的 lambda 元数据。
     *
     * @param function 可序列化方法引用
     * @return lambda 元数据
     */
    static SerializedLambda extractUsingSerialization(SerializableFunction<?, ?> function) {
        AtomicReference<SerializedLambda> extracted = new AtomicReference<>();
        try (ObjectOutputStream output = new ObjectOutputStream(OutputStream.nullOutputStream()) {
            {
                enableReplaceObject(true);
            }

            @Override
            protected Object replaceObject(Object object) {
                if (object instanceof SerializedLambda lambda) {
                    extracted.compareAndSet(null, lambda);
                }
                return object;
            }
        }) {
            output.writeObject(function);
        } catch (IOException e) {
            throw new LambdaResolutionException("Unable to serialize getter lambda", e);
        }
        SerializedLambda lambda = extracted.get();
        if (lambda == null) {
            throw new LambdaResolutionException("Serialized getter did not expose lambda metadata");
        }
        return lambda;
    }
}
