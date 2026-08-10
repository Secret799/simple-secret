package com.ss.easymedia.webrtc.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 摘要工具。
 */
public final class Sha256Utils {

    private Sha256Utils() {
    }

    /**
     * 计算输入字符串的 SHA-256 十六进制摘要。
     *
     * @param value 输入值
     * @return 64 位小写十六进制摘要
     */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            // JDK 必定提供 SHA-256
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
