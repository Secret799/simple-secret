package com.ss.common.toolbox.property;

import com.ss.common.toolbox.function.SerializableFunction;

import java.beans.Introspector;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodType;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Field;

/**
 * 将实体 getter 方法引用解析为 JavaBean 属性名和字段。
 *
 * <p>仅接受可在实体实例上调用的零参数 getter，并验证字段确实存在。</p>
 */
public final class LambdaPropertyResolver {
    private LambdaPropertyResolver() {
    }

    /**
     * 解析 getter 对应的 JavaBean 属性名。
     *
     * @param getter getter 方法引用
     * @param <T>    getter 所属类型
     * @return JavaBean 属性名
     * @throws LambdaResolutionException 方法引用不是合法 getter 时抛出
     */
    public static <T> String resolvePropertyName(SerializableFunction<T, ?> getter) {
        return resolve(getter).propertyName();
    }

    /**
     * 解析 getter 对应的字段，包含继承层级中的字段。
     *
     * @param getter getter 方法引用
     * @param <T>    getter 所属类型
     * @return 对应字段
     * @throws LambdaResolutionException 方法引用非法或字段不存在时抛出
     */
    public static <T> Field resolveField(SerializableFunction<T, ?> getter) {
        return resolve(getter).field();
    }

    private static Field findField(Class<?> ownerType, String propertyName) {
        Class<?> type = ownerType;
        while (type != null && type != Object.class) {
            try {
                return type.getDeclaredField(propertyName);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new LambdaResolutionException("No field named '" + propertyName
                + "' exists on " + ownerType.getName() + " or its superclasses");
    }

    private static Resolution resolve(SerializableFunction<?, ?> getter) {
        SerializedLambda lambda = SerializedLambdaExtractor.extract(getter);
        String methodName = lambda.getImplMethodName();
        try {
            ClassLoader classLoader = getter.getClass().getClassLoader();
            MethodType instantiatedType = MethodType.fromMethodDescriptorString(
                    lambda.getInstantiatedMethodType(), getter.getClass().getClassLoader());
            if (instantiatedType.parameterCount() != 1) {
                throw new LambdaResolutionException("Getter lambda must accept exactly one receiver");
            }

            int methodKind = lambda.getImplMethodKind();
            if (methodKind != MethodHandleInfo.REF_invokeVirtual
                    && methodKind != MethodHandleInfo.REF_invokeInterface
                    && methodKind != MethodHandleInfo.REF_invokeSpecial) {
                throw new LambdaResolutionException("Method reference must target an instance getter: "
                        + methodName);
            }

            Class<?> ownerType = instantiatedType.parameterType(0);
            Class<?> implementationType = Class.forName(
                    lambda.getImplClass().replace('/', '.'), false, classLoader);
            if (!implementationType.isAssignableFrom(ownerType)) {
                throw new LambdaResolutionException("Getter implementation " + implementationType.getName()
                        + " cannot be invoked on " + ownerType.getName());
            }

            MethodType implementationMethodType = MethodType.fromMethodDescriptorString(
                    lambda.getImplMethodSignature(), classLoader);
            if (implementationMethodType.parameterCount() != 0
                    || implementationMethodType.returnType() == Void.TYPE) {
                throw new LambdaResolutionException("Getter must be a zero-argument instance method: "
                        + methodName);
            }

            String propertyName;
            if (methodName.startsWith("get") && methodName.length() > 3) {
                propertyName = Introspector.decapitalize(methodName.substring(3));
            } else if (methodName.startsWith("is") && methodName.length() > 2) {
                Class<?> returnType = implementationMethodType.returnType();
                if (returnType != Boolean.TYPE && returnType != Boolean.class) {
                    throw new LambdaResolutionException("Boolean getter must return boolean or Boolean: "
                            + methodName);
                }
                propertyName = Introspector.decapitalize(methodName.substring(2));
            } else {
                throw new LambdaResolutionException(
                        "Method reference must target a JavaBean getter: " + methodName);
            }

            return new Resolution(propertyName, findField(implementationType, propertyName));
        } catch (LambdaResolutionException e) {
            throw e;
        } catch (ClassNotFoundException | IllegalArgumentException | LinkageError e) {
            throw new LambdaResolutionException("Unable to resolve getter owner type", e);
        }
    }

    private record Resolution(String propertyName, Field field) {
    }
}
