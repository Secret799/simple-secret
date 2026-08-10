package com.ss.common.toolbox.property;

import com.ss.common.toolbox.function.SerializableFunction;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LambdaPropertyResolverTest {

    @Test
    void resolvesNormalBooleanAndInheritedGetters() {
        assertEquals("name", LambdaPropertyResolver.resolvePropertyName(Person::getName));
        assertEquals("active", LambdaPropertyResolver.resolvePropertyName(Person::isActive));
        assertEquals("enabled", LambdaPropertyResolver.resolvePropertyName(Person::isEnabled));
        assertEquals("parentCode", LambdaPropertyResolver.resolvePropertyName(Person::getParentCode));
        Field field = LambdaPropertyResolver.resolveField(Person::getParentCode);
        assertEquals(Parent.class, field.getDeclaringClass());
    }

    @Test
    void followsJavaBeansAcronymRule() {
        assertEquals("URL", LambdaPropertyResolver.resolvePropertyName(Person::getURL));
    }

    @Test
    void rejectsLambdaBodiesThatAreNotGetterMethodReferences() {
        SerializableFunction<Person, String> function = person -> person.getName();
        assertThrows(LambdaResolutionException.class,
                () -> LambdaPropertyResolver.resolvePropertyName(function));
    }

    @Test
    void rejectsStaticMethodsThatMimicGetters() {
        assertThrows(LambdaResolutionException.class,
                () -> LambdaPropertyResolver.resolvePropertyName(Helpers::getName));
    }

    @Test
    void rejectsBoundHelperMethodsWhoseReceiverIsNotTheEntity() {
        InstanceHelpers helpers = new InstanceHelpers();
        SerializableFunction<Person, String> function = helpers::getName;
        assertThrows(LambdaResolutionException.class,
                () -> LambdaPropertyResolver.resolvePropertyName(function));
    }

    @Test
    void rejectsBoundEntityMethodsThatConsumeTheFunctionArgument() {
        Person person = new Person();
        SerializableFunction<Person, String> function = person::getNameFrom;
        assertThrows(LambdaResolutionException.class,
                () -> LambdaPropertyResolver.resolvePropertyName(function));
    }

    @Test
    void rejectsIsMethodsWithNonBooleanReturnTypes() {
        assertThrows(LambdaResolutionException.class,
                () -> LambdaPropertyResolver.resolvePropertyName(Person::isName));
    }

    @Test
    void rejectsGettersWithoutBackingFields() {
        assertThrows(LambdaResolutionException.class,
                () -> LambdaPropertyResolver.resolvePropertyName(Person::getDisplayName));
    }

    @Test
    void supportsGenericAndBridgeGetters() {
        assertEquals("value", LambdaPropertyResolver.resolvePropertyName(GenericPerson::getValue));
        assertEquals("code", LambdaPropertyResolver.resolvePropertyName(StringCode::getCode));
    }

    @Test
    void resolvesFieldFromGetterDeclaringClassWhenChildShadowsTheName() {
        Field field = LambdaPropertyResolver.resolveField(ShadowChild::getCode);

        assertEquals(ShadowParent.class, field.getDeclaringClass());
    }

    static class Parent {
        private String parentCode;

        public String getParentCode() {
            return parentCode;
        }
    }

    static class Person extends Parent {
        private String name;
        private String nameFrom;
        private boolean active;
        private Boolean enabled;
        private String URL;

        public String getName() {
            return name;
        }

        public String getNameFrom(Person person) {
            return person.getName();
        }

        public boolean isActive() {
            return active;
        }

        public Boolean isEnabled() {
            return enabled;
        }

        public String isName() {
            return name;
        }

        public String getDisplayName() {
            return name;
        }

        public String getURL() {
            return URL;
        }
    }

    static final class Helpers {
        private Helpers() {
        }

        static String getName(Person person) {
            return person.getName();
        }
    }

    static final class InstanceHelpers {
        String getName(Person person) {
            return person.getName();
        }
    }

    static class GenericHolder<T> {
        private T value;

        public T getValue() {
            return value;
        }
    }

    static final class GenericPerson extends GenericHolder<String> {
    }

    interface Code<T> {
        T getCode();
    }

    static final class StringCode implements Code<String> {
        private String code;

        @Override
        public String getCode() {
            return code;
        }
    }

    static class ShadowParent {
        private String code;

        public String getCode() {
            return code;
        }
    }

    static final class ShadowChild extends ShadowParent {
        @SuppressWarnings("unused")
        private String code;
    }
}
