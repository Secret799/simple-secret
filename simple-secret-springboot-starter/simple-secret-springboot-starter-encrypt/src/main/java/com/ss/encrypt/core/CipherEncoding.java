package com.ss.encrypt.core;

/** 二进制密文的文本编码格式。 */
public enum CipherEncoding {

    /** 由上层配置选择实际编码。 */
    DEFAULT,

    /** RFC 4648 Base64 编码。 */
    BASE64,

    /** 小写十六进制编码。 */
    HEX
}
