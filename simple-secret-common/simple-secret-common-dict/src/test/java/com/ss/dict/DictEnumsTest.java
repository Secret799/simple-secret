package com.ss.dict;

import com.ss.dict.model.DictElement;
import com.ss.dict.model.DictValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 验证实现 DictValue 的枚举可以安全转换和查询。 */
class DictEnumsTest {

    @Test
    void findsEnumByCodeAndOptionalType() {
        assertEquals(Status.ENABLED, DictEnums.find(Status.class, "1"));
        assertEquals(Status.DISABLED, DictEnums.find(Status.class, "system", "0"));
        assertNull(DictEnums.find(Status.class, "missing"));
        assertNull(DictEnums.find(Status.class, "other", "1"));
    }

    @Test
    void returnsImmutableElementsInDeclarationOrder() {
        List<DictElement> elements = DictEnums.elements(Status.class);

        assertEquals(List.of("1", "0"), elements.stream().map(DictElement::code).toList());
        assertThrows(UnsupportedOperationException.class,
                () -> elements.add(DictElement.from(Status.ENABLED)));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rejectsNonEnumValueTypes() {
        assertThrows(IllegalArgumentException.class,
                () -> DictEnums.elements((Class) NonEnumValue.class));
    }

    private enum Status implements DictValue {
        ENABLED("1", "启用"),
        DISABLED("0", "禁用");

        private final String code;
        private final String label;

        Status(String code, String label) {
            this.code = code;
            this.label = label;
        }

        @Override
        public String getDictCode() {
            return code;
        }

        @Override
        public String getDictLabel() {
            return label;
        }

        @Override
        public String getDictType() {
            return "system";
        }
    }

    private static final class NonEnumValue implements DictValue {
        @Override
        public String getDictCode() {
            return "1";
        }

        @Override
        public String getDictLabel() {
            return "启用";
        }
    }
}
