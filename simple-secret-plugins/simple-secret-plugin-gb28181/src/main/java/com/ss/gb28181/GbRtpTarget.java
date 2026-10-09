package com.ss.gb28181;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** A host-owned RTP receiver, reachable by the device. Constructing a target opens no sockets. */
public record GbRtpTarget(String address, int port, Transport transport) {
    public GbRtpTarget {
        if (address == null || address.length() > 45 || !address.matches("[0-9a-fA-F:.]+")
                || (!address.contains(":") && !address.matches("[0-9]{1,3}(?:\\.[0-9]{1,3}){3}")))
            throw new IllegalArgumentException("RTP address must be a numeric IP address");
        try {
            // The restricted literal syntax above prevents hostname resolution.
            InetAddress ip = InetAddress.getByName(address);
            if (ip.isAnyLocalAddress() || ip.isMulticastAddress()
                    || ip.getHostAddress().equals("255.255.255.255"))
                throw new IllegalArgumentException("RTP address must be a unicast endpoint");
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid RTP IP address");
        }
        if (port < 1 || port > 65535) throw new IllegalArgumentException("RTP port outside supported range");
        if (transport == null) throw new IllegalArgumentException("RTP transport is required");
    }

    public enum Transport {
        UDP,
        /** The receiver listens; the device initiates the TCP connection (GB/T 28181-2022 Annex D). */
        TCP_PASSIVE
    }
}
