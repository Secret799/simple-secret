package com.ss.dict;

import com.ss.dict.annotation.DictField;
import com.ss.dict.exception.DictionaryMappingException;
import com.ss.dict.model.DictElement;
import com.ss.dict.model.DictScope;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 验证注解字典字段的确定性翻译和元数据校验。 */
class DictionaryParserTest {

    @Test
    void parsesDefaultAndExplicitLabelFields() {
        try (DictionaryRegistry registry = registry()) {
            DictionaryParser parser = new DictionaryParser(registry);
            Person person = new Person("1");
            AliasView alias = new AliasView("0");

            assertSame(person, parser.parse(person));
            parser.parse(alias);

            assertEquals("男", person.sexDisplayLabel);
            assertEquals("女", alias.displayName);
        }
    }

    @Test
    void filtersByFixedOrDynamicType() {
        try (DictionaryRegistry registry = registry()) {
            DictionaryParser parser = new DictionaryParser(registry);
            FixedTypeView fixed = new FixedTypeView("1");
            DynamicTypeView dynamic = new DynamicTypeView("ANIMAL", "1");

            parser.parse(fixed);
            parser.parse(dynamic, false);

            assertEquals("男人", fixed.sexDisplayLabel);
            assertEquals("雄性", dynamic.sexDisplayLabel);
        }
    }

    @Test
    void preservesInputOrderAndUnknownCodesForMultipleValues() {
        try (DictionaryRegistry registry = registry()) {
            MultipleView view = new MultipleView("0,missing,1");

            new DictionaryParser(registry).parse(view);

            assertEquals("女,missing,男", view.sexDisplayLabel);
        }
    }

    @Test
    void leavesExistingLabelUntouchedWhenSourceValueIsNull() {
        try (DictionaryRegistry registry = registry()) {
            Person person = new Person(null);
            person.sexDisplayLabel = "已有值";

            new DictionaryParser(registry).parse(person);

            assertEquals("已有值", person.sexDisplayLabel);
        }
    }

    @Test
    void doesNotLoadDictionaryWhenSourceValueIsNull() {
        AtomicInteger loads = new AtomicInteger();
        try (DictionaryRegistry registry = new DictionaryRegistry(Duration.ofMinutes(1))) {
            registry.register("sex", () -> {
                loads.incrementAndGet();
                throw new IllegalStateException("should not be called");
            });
            Person person = new Person(null);

            new DictionaryParser(registry).parse(person);

            assertEquals(0, loads.get());
        }
    }

    @Test
    void discoversInheritedFieldsAndSupportsMixedRuntimeTypes() {
        try (DictionaryRegistry registry = registry()) {
            ChildView child = new ChildView("1");
            AliasView alias = new AliasView("0");
            List<Object> values = List.of(child, alias);

            assertSame(values, new DictionaryParser(registry).parseAll(values));

            assertEquals("男", ((Person) child).sexDisplayLabel);
            assertEquals("女", alias.displayName);
        }
    }

    @Test
    void rejectsAmbiguousOrInvalidMetadata() {
        try (DictionaryRegistry registry = registry()) {
            DictionaryParser parser = new DictionaryParser(registry);

            assertThrows(DictionaryMappingException.class,
                    () -> parser.parse(new AmbiguousTypeView()));
            assertThrows(DictionaryMappingException.class,
                    () -> parser.parse(new MissingLabelView()));
            assertThrows(DictionaryMappingException.class,
                    () -> parser.parse(new NumericLabelView()));
            assertThrows(DictionaryMappingException.class,
                    () -> parser.parse(new BlankSeparatorView()));
            assertThrows(DictionaryMappingException.class,
                    () -> parser.parse(new StaticFieldView()));
        }
    }

    private static DictionaryRegistry registry() {
        DictionaryRegistry registry = new DictionaryRegistry(Duration.ofMinutes(1));
        registry.register("sex", () -> List.of(
                value("1", "男", "default"),
                value("0", "女", "default"),
                value("1", "男人", "PERSON"),
                value("0", "女人", "PERSON"),
                value("1", "雄性", "ANIMAL"),
                value("0", "雌性", "ANIMAL")));
        return registry;
    }

    private static DictElement value(String code, String label, String type) {
        return new DictElement(DictScope.GLOBAL, "global", code, label, type);
    }

    private static class Person {
        @DictField("sex")
        private final String sex;
        private String sexDisplayLabel;

        private Person(String sex) {
            this.sex = sex;
        }
    }

    private static final class AliasView {
        @DictField(value = "sex", labelField = "displayName")
        private final String sex;
        private String displayName;

        private AliasView(String sex) {
            this.sex = sex;
        }
    }

    private static final class FixedTypeView {
        @DictField(value = "sex", type = "PERSON")
        private final String sex;
        private String sexDisplayLabel;

        private FixedTypeView(String sex) {
            this.sex = sex;
        }
    }

    private static final class DynamicTypeView {
        private final String category;
        @DictField(value = "sex", typeField = "category")
        private final String sex;
        private String sexDisplayLabel;

        private DynamicTypeView(String category, String sex) {
            this.category = category;
            this.sex = sex;
        }
    }

    private static final class MultipleView {
        @DictField(value = "sex", multiple = true)
        private final String sex;
        private String sexDisplayLabel;

        private MultipleView(String sex) {
            this.sex = sex;
        }
    }

    private static final class ChildView extends Person {
        private ChildView(String sex) {
            super(sex);
        }
    }

    private static final class AmbiguousTypeView {
        private String category = "PERSON";
        @DictField(value = "sex", type = "PERSON", typeField = "category")
        private String sex = "1";
        private String sexDisplayLabel;
    }

    private static final class MissingLabelView {
        @DictField("sex")
        private String sex = "1";
    }

    private static final class NumericLabelView {
        @DictField("sex")
        private String sex = "1";
        private int sexDisplayLabel;
    }

    private static final class BlankSeparatorView {
        @DictField(value = "sex", multiple = true, separator = " ")
        private String sex = "1";
        private String sexDisplayLabel;
    }

    private static final class StaticFieldView {
        @DictField("sex")
        private static String sex = "1";
        private static String sexDisplayLabel;
    }
}
