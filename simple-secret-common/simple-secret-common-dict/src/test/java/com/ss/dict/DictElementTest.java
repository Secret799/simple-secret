package com.ss.dict;

import com.ss.dict.model.DictElement;
import com.ss.dict.model.DictScope;
import com.ss.dict.model.DictValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证公开字典值模型的默认值、复制和不可变约束。 */
class DictElementTest {

    @Test
    void suppliesFrameworkNeutralDefaults() {
        DictValue value = value("1", "启用");

        assertEquals("default", value.getDictType());
        assertEquals(DictScope.GLOBAL, value.getDictScope());
        assertEquals("global", value.getDictScopeCode());
    }

    @Test
    void copiesValuesIntoAnImmutableElement() {
        DictElement element = DictElement.from(new DictValue() {
            @Override
            public String getDictCode() {
                return "1";
            }

            @Override
            public String getDictLabel() {
                return "启用";
            }

            @Override
            public String getDictType() {
                return "status";
            }

            @Override
            public DictScope getDictScope() {
                return DictScope.TENANT;
            }

            @Override
            public String getDictScopeCode() {
                return "tenant-a";
            }
        });

        assertTrue(DictElement.class.isRecord());
        assertEquals("1", element.code());
        assertEquals("启用", element.label());
        assertEquals("status", element.type());
        assertEquals(DictScope.TENANT, element.scope());
        assertEquals("tenant-a", element.scopeCode());
    }

    @Test
    void rejectsNullValuesAndBlankRequiredFields() {
        assertThrows(NullPointerException.class, () -> DictElement.from(null));
        assertThrows(IllegalArgumentException.class,
                () -> DictElement.from(value(" ", "启用")));
        assertThrows(IllegalArgumentException.class,
                () -> DictElement.from(value("1", " ")));
    }

    private static DictValue value(String code, String label) {
        return new DictValue() {
            @Override
            public String getDictCode() {
                return code;
            }

            @Override
            public String getDictLabel() {
                return label;
            }
        };
    }
}
