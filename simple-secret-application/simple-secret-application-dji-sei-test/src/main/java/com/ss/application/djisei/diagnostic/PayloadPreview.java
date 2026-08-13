package com.ss.application.djisei.diagnostic;

import java.util.Arrays;
import java.util.Objects;

/**
 * 单条 SEI 负载的有界十六进制和可打印 ASCII 预览。
 *
 * @author junpzx
 * @since 2026-08-13
 */
public final class PayloadPreview {

    /** 小写十六进制字符表。 */
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    /** 有界十六进制预览。 */
    private final String hex;

    /** 有界可打印 ASCII 预览。 */
    private final String text;

    /** 原始负载是否因上限而被截断。 */
    private final boolean truncated;

    /**
     * 创建已经渲染的负载预览。
     *
     * @param hex 十六进制预览
     * @param text 可打印 ASCII 预览
     * @param truncated 是否被截断
     */
    private PayloadPreview(String hex, String text, boolean truncated) {
        this.hex = hex;
        this.text = text;
        this.truncated = truncated;
    }

    /**
     * 从负载前缀创建有界预览。
     *
     * @param payload 原始负载
     * @param previewBytes 最大预览字节数
     * @return 有界十六进制和 ASCII 预览
     */
    public static PayloadPreview from(byte[] payload, int previewBytes) {
        Objects.requireNonNull(payload, "payload");
        if (previewBytes <= 0) {
            throw new IllegalArgumentException("previewBytes must be greater than zero");
        }
        int previewLength = Math.min(payload.length, previewBytes);
        byte[] prefix = Arrays.copyOf(payload, previewLength);
        boolean truncated = payload.length > previewLength;
        return new PayloadPreview(toHex(prefix, truncated), toPrintableAscii(prefix), truncated);
    }

    /**
     * 渲染有界十六进制前缀。
     *
     * @param payload 原始负载
     * @param truncated 是否追加截断标识
     * @return 小写十六进制预览
     */
    private static String toHex(byte[] payload, boolean truncated) {
        char[] characters = new char[payload.length * 2];
        for (int index = 0; index < payload.length; index++) {
            int value = payload[index] & 0xFF;
            characters[index * 2] = HEX_DIGITS[value >>> 4];
            characters[index * 2 + 1] = HEX_DIGITS[value & 0x0F];
        }
        String hexPrefix = new String(characters);
        return truncated ? hexPrefix + "..." : hexPrefix;
    }

    /**
     * 渲染有界可打印 ASCII 前缀。
     *
     * @param payload 原始负载
     * @return 不可打印字节替换为点号的预览
     */
    private static String toPrintableAscii(byte[] payload) {
        char[] characters = new char[payload.length];
        for (int index = 0; index < payload.length; index++) {
            int value = payload[index] & 0xFF;
            characters[index] = value >= 0x20 && value <= 0x7E ? (char) value : '.';
        }
        return new String(characters);
    }

    /** @return 小写十六进制预览 */
    public String hex() {
        return hex;
    }

    /** @return 可打印 ASCII 预览 */
    public String text() {
        return text;
    }

    /** @return 原始负载是否被截断 */
    public boolean truncated() {
        return truncated;
    }
}
