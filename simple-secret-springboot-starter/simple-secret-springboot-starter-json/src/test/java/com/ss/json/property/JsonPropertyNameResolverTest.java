package com.ss.json.property;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonPropertyNameResolverTest {
    @Test
    void prefersJsonPropertyAndSupportsInheritedFields() {
        assertEquals("display_name", JsonPropertyNameResolver.resolve(Person::getName));
        assertEquals("display_name", JsonPropertyNameResolver.resolve(
                Person::getName, "-", NameCase.UPPER));
        assertEquals("parent_code", JsonPropertyNameResolver.resolve(
                Person::getParentCode, "_", NameCase.LOWER));
    }

    @Test
    void appliesRequestedCaseToCamelCaseFields() {
        assertEquals("ORDER-ID2", JsonPropertyNameResolver.resolve(
                Person::getOrderId2, "-", NameCase.UPPER));
    }

    @Test
    void ignoresBlankJsonPropertyValues() {
        assertEquals("blank_value", JsonPropertyNameResolver.resolve(
                Person::getBlankValue, "_", NameCase.LOWER));
    }

    @Test
    void validatesSeparatorAndNameCase() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonPropertyNameResolver.resolve(Person::getName, null, NameCase.LOWER));
        assertThrows(IllegalArgumentException.class,
                () -> JsonPropertyNameResolver.resolve(Person::getName, "_", null));
    }

    @Test
    void usesRootLocaleForCaseConversion() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("ID", NameCase.UPPER.apply("id"));
            assertEquals("id", NameCase.LOWER.apply("ID"));
        } finally {
            Locale.setDefault(original);
        }
    }

    static class Parent {
        private String parentCode;

        public String getParentCode() {
            return parentCode;
        }
    }

    static class Person extends Parent {
        @JsonProperty("display_name")
        private String name;
        private String orderId2;
        @JsonProperty("   ")
        private String blankValue;

        public String getName() {
            return name;
        }

        public String getOrderId2() {
            return orderId2;
        }

        public String getBlankValue() {
            return blankValue;
        }
    }
}
