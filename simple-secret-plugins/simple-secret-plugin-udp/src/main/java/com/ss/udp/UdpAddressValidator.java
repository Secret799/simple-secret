package com.ss.udp;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;

/** UDP 地址与参数校验。 */
final class UdpAddressValidator {

    static final int MAX_UDP_PAYLOAD_LENGTH = 65_507;

    private UdpAddressValidator() {
    }

    static int requirePort(int port) {
        if (port <= 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        return port;
    }

    static int requirePayloadLength(Integer length) {
        int value = java.util.Objects.requireNonNull(length, "maxMessageLength");
        if (value <= 0 || value > MAX_UDP_PAYLOAD_LENGTH) {
            throw new IllegalArgumentException(
                    "maxMessageLength must be between 1 and " + MAX_UDP_PAYLOAD_LENGTH);
        }
        return value;
    }

    static InetAddress requireMulticastAddress(String value) {
        InetAddress address = parseNumericAddress(value, "groupIp");
        if (!address.isMulticastAddress()) {
            throw new IllegalArgumentException("groupIp must be a multicast address");
        }
        return address;
    }

    static InetAddress requireBindAddress(String value) {
        InetAddress address = parseNumericAddress(value, "bindIp");
        if (address.isMulticastAddress()) {
            throw new IllegalArgumentException("bindIp must be a unicast address");
        }
        return address;
    }

    static InetAddress requireLocalInterfaceAddress(String value) {
        InetAddress address = parseNumericAddress(value, "localIp");
        if (address.isMulticastAddress() || address.isAnyLocalAddress()) {
            throw new IllegalArgumentException("localIp must identify a local unicast address");
        }
        return address;
    }

    static NetworkInterface requireNetworkInterface(InetAddress localAddress) {
        try {
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(localAddress);
            if (networkInterface == null) {
                throw new IllegalArgumentException("localIp must belong to a local network interface");
            }
            return networkInterface;
        } catch (SocketException exception) {
            throw new IllegalArgumentException("unable to inspect local network interface", exception);
        }
    }

    static void requireSameAddressFamily(InetAddress first, InetAddress second) {
        boolean bothIpv4 = first instanceof Inet4Address && second instanceof Inet4Address;
        boolean bothIpv6 = first instanceof Inet6Address && second instanceof Inet6Address;
        if (!bothIpv4 && !bothIpv6) {
            throw new IllegalArgumentException("groupIp and localIp must use the same address family");
        }
    }

    private static InetAddress parseNumericAddress(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String normalized = value.trim();
        if (!isIpv4Literal(normalized) && !isIpv6Literal(normalized)) {
            throw new IllegalArgumentException(name + " must be a numeric IP address");
        }
        try {
            return InetAddress.getByName(normalized);
        } catch (UnknownHostException exception) {
            throw new IllegalArgumentException(name + " must be a valid numeric IP address", exception);
        }
    }

    private static boolean isIpv4Literal(String value) {
        String[] segments = value.split("\\.", -1);
        if (segments.length != 4) {
            return false;
        }
        for (String segment : segments) {
            if (segment.isEmpty() || segment.length() > 3
                    || !segment.chars().allMatch(Character::isDigit)) {
                return false;
            }
            if (Integer.parseInt(segment) > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpv6Literal(String value) {
        int scopeIndex = value.indexOf('%');
        String addressPart = scopeIndex >= 0 ? value.substring(0, scopeIndex) : value;
        String scopePart = scopeIndex >= 0 ? value.substring(scopeIndex + 1) : "";
        if (scopeIndex >= 0 && (scopePart.isEmpty() || !scopePart.chars().allMatch(character ->
                Character.isLetterOrDigit(character) || character == '_' || character == '-' || character == '.'))) {
            return false;
        }
        return addressPart.indexOf(':') >= 0 && addressPart.chars().allMatch(character ->
                Character.digit(character, 16) >= 0 || character == ':' || character == '.');
    }
}
