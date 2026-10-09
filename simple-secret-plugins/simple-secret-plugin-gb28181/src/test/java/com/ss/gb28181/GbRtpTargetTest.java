package com.ss.gb28181;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GbRtpTargetTest {
    @Test
    void acceptsNumericUnicastAddressesAndPortBoundaries() {
        assertEquals(1, new GbRtpTarget("192.0.2.1", 1, GbRtpTarget.Transport.UDP).port());
        assertEquals("2001:db8::1", new GbRtpTarget("2001:db8::1", 65535,
                GbRtpTarget.Transport.TCP_PASSIVE).address());
    }

    @Test
    void rejectsNonUnicastAddressesAndSdpInjectionWithoutResolvingNames() {
        for (String address : new String[]{null, "", "localhost", "127.1", "0.0.0.0", "::",
                "224.0.0.1", "ff02::1", "255.255.255.255", "192.0.2.256", "[::1]", "fe80::1%lo0",
                "192.0.2.1\r\na=sendrecv", "1".repeat(10000)}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new GbRtpTarget(address, 30000, GbRtpTarget.Transport.UDP));
        }
    }

    @Test
    void rejectsInvalidPortAndMissingTransport() {
        for (int port : new int[]{-1, 0, 65536}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new GbRtpTarget("192.0.2.1", port, GbRtpTarget.Transport.UDP));
        }
        assertThrows(IllegalArgumentException.class, () -> new GbRtpTarget("192.0.2.1", 30000, null));
    }
}
