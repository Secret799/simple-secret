package com.ss.json.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmptyObjectFilterTest {
    private final EmptyObjectFilter filter = new EmptyObjectFilter();

    @Test
    void handlesNullInheritedAndStaticFields() {
        assertTrue(filter.equals(null));
        assertTrue(filter.equals(new Child()));
        Child child = new Child();
        child.parentValue = "set";
        assertFalse(filter.equals(child));
    }

    @Test
    void treatsInaccessibleJdkStateAsNonEmpty() {
        assertFalse(filter.equals(""));
    }
}

class Parent {
    String parentValue;
}

class Child extends Parent {
    private static final String CONSTANT = "ignored";
    private String childValue;
}
