package com.ss.gb28181.zlm.autoconfigure;

import com.ss.gb28181.GbRtpTarget;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Explicit configuration for the optional RTP adapter; does not enable SIP or ZLM. */
@ConfigurationProperties("simple-secret.gb28181-zlm")
public class GbZlmProperties {
    /** Enable the managed RTP adapter. */
    private boolean enabled;
    /** Numeric unicast IP address the device can reach. Required when enabled. */
    private String advertisedAddress;
    /** Media transport, independent of SIP transport. Required when enabled. */
    private GbRtpTarget.Transport transport;
    /** Maximum receivers, including allocation and failed cleanup. */
    private int maxSessions = 256;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getAdvertisedAddress() { return advertisedAddress; }
    public void setAdvertisedAddress(String advertisedAddress) { this.advertisedAddress = advertisedAddress; }
    public GbRtpTarget.Transport getTransport() { return transport; }
    public void setTransport(GbRtpTarget.Transport transport) { this.transport = transport; }
    public int getMaxSessions() { return maxSessions; }
    public void setMaxSessions(int maxSessions) { this.maxSessions = maxSessions; }
}
