package com.ss.zlm4j.security;

import com.ss.zlm4j.config.properties.MediaResourcePolicyProperties;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 默认媒体资源策略。未显式加入白名单的本机、内网和特殊地址均被拒绝。
 */
public class DefaultMediaResourcePolicy implements MediaResourcePolicy {

    private final Set<String> allowedSchemes;
    private final Set<String> allowedHosts;
    private final List<CidrBlock> allowedCidrs;
    private final Path recordingRoot;
    private final HostAddressResolver resolver;

    /**
     * 创建并初始化实例。
     *
     * @param properties 模块配置
     */
    public DefaultMediaResourcePolicy(MediaResourcePolicyProperties properties) {
        this(properties, InetAddress::getAllByName);
    }

    DefaultMediaResourcePolicy(MediaResourcePolicyProperties properties, HostAddressResolver resolver) {
        this.allowedSchemes = lowerCase(properties.getAllowedSchemes());
        this.allowedHosts = lowerCase(properties.getAllowedHosts());
        this.allowedCidrs = parseCidrs(properties.getAllowedCidrs());
        this.recordingRoot = Path.of(properties.getRecordingRoot()).toAbsolutePath().normalize();
        this.resolver = resolver;
    }

    @Override
    public URI requireAllowed(String value, MediaResourceUsage usage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(usage + " media URL must not be blank");
        }
        URI uri;
        try {
            uri = new URI(value).normalize();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(usage + " media URL is invalid", exception);
        }
        String scheme = normalize(uri.getScheme());
        if (uri.isOpaque() || scheme == null || !allowedSchemes.contains(scheme)) {
            throw new IllegalArgumentException(usage + " media URL scheme is not allowed");
        }
        if (uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException(usage + " media URL must not contain user info");
        }
        String host = normalizeHost(uri.getHost());
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException(usage + " media URL host is required");
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException(usage + " media URL host cannot be resolved", exception);
        }
        if (addresses == null || addresses.length == 0) {
            throw new IllegalArgumentException(usage + " media URL host has no addresses");
        }
        boolean hostAllowed = allowedHosts.contains(host);
        for (InetAddress address : addresses) {
            if (!hostAllowed && isUnsafe(address) && !isCidrAllowed(address)) {
                throw new IllegalArgumentException(usage + " media URL resolves to a blocked address");
            }
        }
        return uri;
    }

    @Override
    public Path requireRecordingPath(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Recording path must not be blank");
        }
        Path supplied = Path.of(value);
        Path candidate = (supplied.isAbsolute() ? supplied : recordingRoot.resolve(supplied))
                .toAbsolutePath().normalize();
        if (!candidate.startsWith(recordingRoot)) {
            throw new IllegalArgumentException("Recording path escapes the configured root");
        }
        rejectEscapingSymlink(candidate);
        return candidate;
    }

    private void rejectEscapingSymlink(Path candidate) {
        try {
            Path realRoot = Files.exists(recordingRoot, LinkOption.NOFOLLOW_LINKS)
                    ? recordingRoot.toRealPath()
                    : recordingRoot;
            Path current = recordingRoot;
            for (Path segment : recordingRoot.relativize(candidate)) {
                current = current.resolve(segment);
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                        && !current.toRealPath().startsWith(realRoot)) {
                    throw new IllegalArgumentException("Recording path escapes the configured root through a symbolic link");
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Recording path cannot be validated", exception);
        }
    }

    private boolean isCidrAllowed(InetAddress address) {
        return allowedCidrs.stream().anyMatch(cidr -> cidr.contains(address));
    }

    private static boolean isUnsafe(InetAddress address) {
        byte[] bytes = address.getAddress();
        boolean uniqueLocalV6 = bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
        boolean carrierGradeNatV4 = bytes.length == 4
                && (bytes[0] & 0xFF) == 100
                && ((bytes[1] & 0xC0) == 0x40);
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || uniqueLocalV6
                || carrierGradeNatV4;
    }

    private static Set<String> lowerCase(Set<String> values) {
        Set<String> result = new HashSet<>();
        if (values != null) {
            for (String value : values) {
                String normalized = normalize(value);
                if (normalized != null && !normalized.isBlank()) {
                    result.add(normalized);
                }
            }
        }
        return Set.copyOf(result);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeHost(String value) {
        String host = normalize(value);
        if (host != null && host.endsWith(".")) {
            return host.substring(0, host.length() - 1);
        }
        if (host != null && host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    private static List<CidrBlock> parseCidrs(Set<String> values) {
        List<CidrBlock> result = new ArrayList<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    result.add(CidrBlock.parse(value.trim()));
                }
            }
        }
        return List.copyOf(result);
    }

    @FunctionalInterface
    interface HostAddressResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private record CidrBlock(byte[] network, int prefixLength) {

        private static CidrBlock parse(String value) {
            String[] parts = value.split("/", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid CIDR: " + value);
            }
            try {
                byte[] network = InetAddress.getByName(parts[0]).getAddress();
                int prefix = Integer.parseInt(parts[1]);
                if (prefix < 0 || prefix > network.length * Byte.SIZE) {
                    throw new IllegalArgumentException("Invalid CIDR prefix: " + value);
                }
                return new CidrBlock(network, prefix);
            } catch (UnknownHostException | NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid CIDR: " + value, exception);
            }
        }

        private boolean contains(InetAddress address) {
            byte[] candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / Byte.SIZE;
            int remainingBits = prefixLength % Byte.SIZE;
            for (int index = 0; index < fullBytes; index++) {
                if (candidate[index] != network[index]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (Byte.SIZE - remainingBits);
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
