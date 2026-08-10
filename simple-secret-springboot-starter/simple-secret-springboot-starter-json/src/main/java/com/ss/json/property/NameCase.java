package com.ss.json.property;

import java.util.Locale;

/**
 * 属性名大小写转换规则。
 */
public enum NameCase {
    /** 保持原始大小写。 */
    PRESERVE {
        @Override
        public String apply(String value) {
            return value;
        }
    },
    /** 使用 {@link Locale#ROOT} 转为大写。 */
    UPPER {
        @Override
        public String apply(String value) {
            return value.toUpperCase(Locale.ROOT);
        }
    },
    /** 使用 {@link Locale#ROOT} 转为小写。 */
    LOWER {
        @Override
        public String apply(String value) {
            return value.toLowerCase(Locale.ROOT);
        }
    };

    /**
     * 应用大小写转换。
     *
     * @param value 原始属性名
     * @return 转换后的属性名
     */
    public abstract String apply(String value);
}
