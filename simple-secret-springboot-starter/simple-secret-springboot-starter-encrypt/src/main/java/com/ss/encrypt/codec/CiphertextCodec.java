package com.ss.encrypt.codec;

import com.ss.encrypt.core.CipherEncoding;
import com.ss.encrypt.core.EncryptionException;

import java.util.Base64;

/** 密文字节与外部文本格式之间的严格编码器。 */
public final class CiphertextCodec {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private CiphertextCodec() {
    }

    /** 按指定格式编码密文字节。 */
    public static String encode(byte[] value, CipherEncoding encoding) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        if (encoding == CipherEncoding.BASE64) {
            return Base64.getEncoder().encodeToString(value);
        }
        if (encoding == CipherEncoding.HEX) {
            char[] result = new char[value.length * 2];
            for (int index = 0; index < value.length; index++) {
                int current = value[index] & 0xff;
                result[index * 2] = HEX[current >>> 4];
                result[index * 2 + 1] = HEX[current & 0x0f];
            }
            return new String(result);
        }
        throw new EncryptionException("Concrete cipher encoding is required");
    }

    /** 按指定格式解码密文，错误消息不会回显输入内容。 */
    public static byte[] decode(String value, CipherEncoding encoding) {
        if (value == null) {
            throw new EncryptionException("Ciphertext must not be null");
        }
        try {
            if (encoding == CipherEncoding.BASE64) {
                return Base64.getDecoder().decode(value);
            }
            if (encoding == CipherEncoding.HEX) {
                if ((value.length() & 1) != 0) {
                    throw new IllegalArgumentException("odd hex length");
                }
                byte[] result = new byte[value.length() / 2];
                for (int index = 0; index < value.length(); index += 2) {
                    int high = Character.digit(value.charAt(index), 16);
                    int low = Character.digit(value.charAt(index + 1), 16);
                    if (high < 0 || low < 0) {
                        throw new IllegalArgumentException("invalid hex digit");
                    }
                    result[index / 2] = (byte) ((high << 4) | low);
                }
                return result;
            }
        } catch (IllegalArgumentException exception) {
            throw new EncryptionException("Ciphertext encoding is invalid", exception);
        }
        throw new EncryptionException("Concrete cipher encoding is required");
    }
}
