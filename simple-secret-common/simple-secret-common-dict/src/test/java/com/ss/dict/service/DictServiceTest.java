package com.ss.dict.service;

import com.ss.dict.model.DictElement;
import com.ss.dict.model.DictScope;
import com.ss.dict.model.DictValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证字典服务默认的编码与标签双向转换。 */
class DictServiceTest {

    private final DictService service = dictType -> {
        if (!"status".equals(dictType)) {
            return List.of();
        }
        return List.of(value("1", "启用"), value("0", "禁用"));
    };

    @Test
    void convertsCodesToLabelsAndPreservesUnknownValues() {
        assertEquals("启用,missing,禁用", service.getDictLabel("status", "1,missing,0"));
        assertEquals("启用|禁用", service.getDictLabel("status", "1|0", "|"));
    }

    @Test
    void convertsLabelsToCodesInTheCorrectDirection() {
        assertEquals("0,unknown,1", service.getDictCode("status", "禁用,unknown,启用"));
        assertEquals("1|0", service.getDictCode("status", "启用|禁用", "|"));
    }

    @Test
    void reportsWhetherATypeHasAnyValues() {
        assertTrue(service.existDictType("status"));
        assertFalse(service.existDictType("missing"));
    }

    @Test
    void validatesTypeInputAndSeparator() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getDictLabel(" ", "1"));
        assertThrows(IllegalArgumentException.class,
                () -> service.getDictCode("status", "启用", ""));
    }

    private static DictValue value(String code, String label) {
        return new DictElement(DictScope.GLOBAL, "global", code, label, "status");
    }
}
