package com.ss.sensitive.core;

import java.util.Objects;
import java.util.function.UnaryOperator;

/** Simple Secret 支持的内置字符串脱敏策略。 */
public enum SensitiveStrategy {

    /** 身份证号保留前三位和后四位。 */
    ID_CARD(value -> maskIdCard(value, 3, 4)),

    /** 手机号保留前三位和后四位。 */
    PHONE(SensitiveStrategy::maskPhone),

    /** 地址隐藏最后八个字符。 */
    ADDRESS(SensitiveStrategy::maskAddress),

    /** 邮箱仅保留前缀首字符和完整域名。 */
    EMAIL(SensitiveStrategy::maskEmail),

    /** 银行卡保留前四位和最后一组。 */
    BANK_CARD(SensitiveStrategy::maskBankCard);

    private final UnaryOperator<String> masker;

    SensitiveStrategy(UnaryOperator<String> masker) {
        this.masker = masker;
    }

    /**
     * 对值执行当前策略。
     *
     * @param value 原始值
     * @return 脱敏结果；输入为 {@code null} 时返回 {@code null}
     */
    public String mask(String value) {
        return value == null ? null : masker.apply(value);
    }

    private static String maskIdCard(String value, int front, int end) {
        if (value.isBlank()) {
            return "";
        }
        int length = codePointLength(value);
        if (front < 0 || end < 0 || front + end > length) {
            return "";
        }
        return hide(value, front, length - end);
    }

    private static String maskPhone(String value) {
        if (value.isBlank()) {
            return "";
        }
        int length = codePointLength(value);
        return hide(value, 3, length - 4);
    }

    private static String maskAddress(String value) {
        if (value.isBlank()) {
            return "";
        }
        int length = codePointLength(value);
        return hide(value, length - 8, length);
    }

    private static String maskEmail(String value) {
        if (value.isBlank()) {
            return "";
        }
        int[] codePoints = value.codePoints().toArray();
        int atIndex = -1;
        for (int index = 0; index < codePoints.length; index++) {
            if (codePoints[index] == '@') {
                atIndex = index;
                break;
            }
        }
        return atIndex <= 1 ? value : hide(value, 1, atIndex);
    }

    private static String maskBankCard(String value) {
        if (value.isBlank()) {
            return value;
        }
        String compact = value.codePoints()
                .filter(codePoint -> !Character.isWhitespace(codePoint))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        int length = compact.length();
        if (length < 9) {
            return compact;
        }
        int endLength = length % 4 == 0 ? 4 : length % 4;
        int middleLength = length - 4 - endLength;
        StringBuilder masked = new StringBuilder(length + middleLength / 4 + 1);
        masked.append(compact, 0, 4);
        for (int index = 0; index < middleLength; index++) {
            if (index % 4 == 0) {
                masked.append(' ');
            }
            masked.append('*');
        }
        return masked.append(' ')
                .append(compact, length - endLength, length)
                .toString();
    }

    private static String hide(String value, int startInclusive, int endExclusive) {
        Objects.requireNonNull(value, "value must not be null");
        int[] codePoints = value.codePoints().toArray();
        if (startInclusive > codePoints.length || startInclusive > endExclusive) {
            return value;
        }
        int boundedEnd = Math.min(endExclusive, codePoints.length);
        StringBuilder masked = new StringBuilder(value.length());
        for (int index = 0; index < codePoints.length; index++) {
            masked.appendCodePoint(index >= startInclusive && index < boundedEnd
                    ? '*'
                    : codePoints[index]);
        }
        return masked.toString();
    }

    private static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }
}
