package com.ss.camera.service;

import com.ss.camera.domain.StreamUrlAssemblyDomain;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 厂商 RTSP 地址组装器的公共参数校验和编码实现。
 */
public abstract class BaseUrlAssemblyServiceImpl implements UrlAssemblyService {
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    /**
     * 校验公共参数并组装 URI authority。
     *
     * @param domain 地址参数
     * @return 已编码的 {@code user:password@host:port}
     */
    protected final String authority(StreamUrlAssemblyDomain domain) {
        requireDomain(domain);
        String host = requiredTrimmed(domain.getIp(), "ip");
        if (containsAuthorityDelimiter(host)) {
            throw invalid("ip");
        }
        String port = requiredTrimmed(domain.getPort(), "port");
        int portNumber;
        try {
            portNumber = Integer.parseInt(port);
        } catch (NumberFormatException exception) {
            throw invalid("port");
        }
        if (portNumber < 1 || portNumber > 65535) {
            throw invalid("port");
        }
        String account = requiredCredential(domain.getAccount(), "account");
        String password = requiredCredential(domain.getPassword(), "password");
        return encodeUserInfo(account) + ":" + encodeUserInfo(password)
                + "@" + formatAuthority(host, portNumber);
    }

    /**
     * 校验并返回数字通道号。
     *
     * @param domain 地址参数
     * @return 通道号
     */
    protected final String channelNo(StreamUrlAssemblyDomain domain) {
        requireDomain(domain);
        String channelNo = requiredTrimmed(domain.getChannelNo(), "channelNo");
        for (int index = 0; index < channelNo.length(); index++) {
            char value = channelNo.charAt(index);
            if (value < '0' || value > '9') {
                throw invalid("channelNo");
            }
        }
        return channelNo;
    }

    /**
     * 校验并规范化码流类型。
     *
     * @param domain 地址参数
     * @return 使用小写表示的码流类型
     */
    protected final String streamType(StreamUrlAssemblyDomain domain) {
        requireDomain(domain);
        String streamType = requiredTrimmed(domain.getStreamType(), "streamType");
        for (int index = 0; index < streamType.length(); index++) {
            char value = streamType.charAt(index);
            if (!isAsciiLetterOrDigit(value) && value != '-' && value != '_') {
                throw invalid("streamType");
            }
        }
        return streamType.toLowerCase(Locale.ROOT);
    }

    private static void requireDomain(StreamUrlAssemblyDomain domain) {
        if (domain == null) {
            throw new IllegalArgumentException("domain must not be null");
        }
    }

    private static String requiredTrimmed(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field);
        }
        return value.trim();
    }

    private static String requiredCredential(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalid(field);
        }
        return value;
    }

    private static boolean containsAuthorityDelimiter(String host) {
        for (int index = 0; index < host.length(); index++) {
            char value = host.charAt(index);
            if (Character.isWhitespace(value) || value == '@' || value == '/'
                    || value == '?' || value == '#') {
                return true;
            }
        }
        return false;
    }

    private static String formatAuthority(String host, int port) {
        boolean startsBracket = host.startsWith("[");
        boolean endsBracket = host.endsWith("]");
        if (startsBracket != endsBracket) {
            throw invalid("ip");
        }
        String normalizedHost = startsBracket ? host.substring(1, host.length() - 1) : host;
        if (normalizedHost.isEmpty()) {
            throw invalid("ip");
        }
        try {
            URI uri = new URI("rtsp", null, normalizedHost, port, null, null, null);
            if (uri.getHost() == null) {
                throw invalid("ip");
            }
            return uri.getRawAuthority();
        } catch (URISyntaxException exception) {
            throw invalid("ip");
        }
    }

    private static String encodeUserInfo(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder result = new StringBuilder(bytes.length);
        for (byte current : bytes) {
            int unsigned = current & 0xff;
            if (isUnreserved(unsigned)) {
                result.append((char) unsigned);
            } else {
                result.append('%')
                        .append(HEX[unsigned >>> 4])
                        .append(HEX[unsigned & 0x0f]);
            }
        }
        return result.toString();
    }

    private static boolean isUnreserved(int value) {
        return value >= 'a' && value <= 'z'
                || value >= 'A' && value <= 'Z'
                || value >= '0' && value <= '9'
                || value == '-' || value == '.' || value == '_' || value == '~';
    }

    private static boolean isAsciiLetterOrDigit(char value) {
        return value >= 'a' && value <= 'z'
                || value >= 'A' && value <= 'Z'
                || value >= '0' && value <= '9';
    }

    private static IllegalArgumentException invalid(String field) {
        return new IllegalArgumentException(field + " is invalid");
    }
}
