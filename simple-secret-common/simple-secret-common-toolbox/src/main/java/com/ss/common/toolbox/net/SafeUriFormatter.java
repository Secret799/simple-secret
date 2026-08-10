package com.ss.common.toolbox.net;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * 将绝对网络 URI 格式化为适合日志记录的无凭据形式。
 */
public final class SafeUriFormatter {

    private SafeUriFormatter() {
    }

    /**
     * 移除 URI 的 user-info、查询参数和 fragment。
     *
     * @param value 原始 URI
     * @return 可安全记录的 URI；输入无效时返回固定占位符
     */
    public static String forLog(String value) {
        if (value == null) {
            return "<null-uri>";
        }
        try {
            URI uri = new URI(value).normalize();
            if (!uri.isAbsolute() || uri.getHost() == null) {
                return "<invalid-uri>";
            }
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(),
                    uri.getPath(), null, null).toASCIIString();
        } catch (URISyntaxException exception) {
            return "<invalid-uri>";
        }
    }
}
