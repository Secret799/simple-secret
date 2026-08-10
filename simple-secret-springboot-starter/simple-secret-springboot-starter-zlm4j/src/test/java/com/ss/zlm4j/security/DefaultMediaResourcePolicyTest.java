package com.ss.zlm4j.security;

import com.ss.zlm4j.config.properties.MediaResourcePolicyProperties;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultMediaResourcePolicyTest {

    @Test
    void allowsPublicHttpResource() throws Exception {
        DefaultMediaResourcePolicy policy = policy(new MediaResourcePolicyProperties(), "93.184.216.34");

        assertThat(policy.requireAllowed("https://example.test/live.m3u8", MediaResourceUsage.PULL))
                .hasScheme("https")
                .hasHost("example.test");
    }

    @Test
    void rejectsUnsupportedSchemeAndUrlUserInfo() throws Exception {
        DefaultMediaResourcePolicy policy = policy(new MediaResourcePolicyProperties(), "93.184.216.34");

        assertThatThrownBy(() -> policy.requireAllowed("file:///etc/passwd", MediaResourceUsage.SNAPSHOT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.requireAllowed("https://user:secret@example.test/live", MediaResourceUsage.PULL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsLoopbackPrivateLinkLocalAndMulticastAddresses() throws Exception {
        for (String address : List.of("127.0.0.1", "10.0.0.1", "169.254.1.1", "224.0.0.1", "fc00::1")) {
            DefaultMediaResourcePolicy policy = policy(new MediaResourcePolicyProperties(), address);

            assertThatThrownBy(() -> policy.requireAllowed("http://camera.test/live", MediaResourceUsage.PULL))
                    .as(address)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void rejectsHostnameWhenAnyDnsResultIsUnsafe() throws Exception {
        MediaResourcePolicyProperties properties = new MediaResourcePolicyProperties();
        DefaultMediaResourcePolicy policy = new DefaultMediaResourcePolicy(properties, host -> new InetAddress[]{
                InetAddress.getByName("93.184.216.34"),
                InetAddress.getByName("127.0.0.1")
        });

        assertThatThrownBy(() -> policy.requireAllowed("http://mixed.test/live", MediaResourceUsage.PULL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void explicitHostAndCidrAllowlistsPermitPrivateResources() throws Exception {
        MediaResourcePolicyProperties hostProperties = new MediaResourcePolicyProperties();
        hostProperties.setAllowedHosts(Set.of("camera.internal"));
        DefaultMediaResourcePolicy hostPolicy = policy(hostProperties, "10.0.0.9");

        assertThat(hostPolicy.requireAllowed("rtsp://camera.internal/live", MediaResourceUsage.PULL))
                .hasHost("camera.internal");

        MediaResourcePolicyProperties cidrProperties = new MediaResourcePolicyProperties();
        cidrProperties.setAllowedCidrs(Set.of("10.20.0.0/16"));
        DefaultMediaResourcePolicy cidrPolicy = policy(cidrProperties, "10.20.3.4");

        assertThat(cidrPolicy.requireAllowed("rtsp://camera.test/live", MediaResourceUsage.PULL))
                .hasHost("camera.test");
    }

    private static DefaultMediaResourcePolicy policy(MediaResourcePolicyProperties properties, String address)
            throws Exception {
        InetAddress resolved = InetAddress.getByName(address);
        return new DefaultMediaResourcePolicy(properties, host -> new InetAddress[]{resolved});
    }
}
