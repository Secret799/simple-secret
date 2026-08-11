package com.ss.encrypt.web;

/** API v1 协议生成的加密 key header 和正文。 */
public record ApiEncryptedPayload(String keyHeader, String body) {

    @Override
    public String toString() {
        return "ApiEncryptedPayload[keyHeader=<redacted>, body=<redacted>]";
    }
}
