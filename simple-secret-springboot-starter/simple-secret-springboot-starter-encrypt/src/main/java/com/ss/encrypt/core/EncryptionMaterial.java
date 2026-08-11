package com.ss.encrypt.core;

import java.util.Arrays;

/**
 * 单个 key id 对应的对称或非对称密钥材料。
 *
 * <p>该类型不会在 {@link #toString()} 中输出任何实际密钥内容。</p>
 */
public final class EncryptionMaterial {

    private final byte[] secretKey;
    private final String publicKey;
    private final String privateKey;

    private EncryptionMaterial(
            byte[] secretKey, String publicKey, String privateKey) {
        this.secretKey = secretKey == null ? null : secretKey.clone();
        this.publicKey = trimToNull(publicKey);
        this.privateKey = trimToNull(privateKey);
    }

    /** 创建只包含原始对称密钥字节的材料。 */
    public static EncryptionMaterial symmetric(byte[] secretKey) {
        if (secretKey == null || secretKey.length == 0) {
            throw new IllegalArgumentException("secretKey must not be empty");
        }
        return new EncryptionMaterial(secretKey, null, null);
    }

    /** 创建至少包含公钥或私钥之一的非对称密钥材料。 */
    public static EncryptionMaterial asymmetric(
            String publicKey, String privateKey) {
        if (trimToNull(publicKey) == null && trimToNull(privateKey) == null) {
            throw new IllegalArgumentException(
                    "publicKey or privateKey must be provided");
        }
        return new EncryptionMaterial(null, publicKey, privateKey);
    }

    /** 返回对称密钥的防御副本；未配置时返回 {@code null}。 */
    public byte[] secretKey() {
        return secretKey == null ? null : secretKey.clone();
    }

    /** 返回公钥文本；未配置时返回 {@code null}。 */
    public String publicKey() {
        return publicKey;
    }

    /** 返回私钥文本；未配置时返回 {@code null}。 */
    public String privateKey() {
        return privateKey;
    }

    @Override
    public String toString() {
        return "EncryptionMaterial[secretKey=<redacted>, publicKey=<redacted>, "
                + "privateKey=<redacted>]";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EncryptionMaterial material)) {
            return false;
        }
        return Arrays.equals(secretKey, material.secretKey)
                && java.util.Objects.equals(publicKey, material.publicKey)
                && java.util.Objects.equals(privateKey, material.privateKey);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(secretKey);
        result = 31 * result + java.util.Objects.hashCode(publicKey);
        return 31 * result + java.util.Objects.hashCode(privateKey);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
