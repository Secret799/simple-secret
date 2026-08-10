package com.ss.easymedia.webrtc.metrics;

import com.ss.easymedia.webrtc.domain.WebRtcOperation;
import com.ss.easymedia.webrtc.domain.WebRtcSessionType;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MicrometerWebRtcSessionMetricsTest {

    @Test
    void shouldRecordBoundedCreateMutationAndCleanupMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerWebRtcSessionMetrics metrics = new MicrometerWebRtcSessionMetrics(registry);

        metrics.recordCreate(WebRtcSessionType.WHEP, "success", Duration.ofMillis(25));
        metrics.recordMutation(WebRtcOperation.DELETE, "retry", Duration.ofMillis(5));
        metrics.incrementCleanupRetry("retry");

        assertEquals(1, registry.get("simple-secret.webrtc.session.create")
                .tag("type", "whep").tag("outcome", "success").timer().count());
        assertEquals(1, registry.get("simple-secret.webrtc.session.mutation")
                .tag("operation", "delete").tag("outcome", "retry").timer().count());
        assertEquals(1D, registry.get("simple-secret.webrtc.cleanup.retry")
                .tag("outcome", "retry").counter().count());

        Set<String> tagKeys = registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(io.micrometer.core.instrument.Tag::getKey)
                .collect(Collectors.toSet());
        assertFalse(tagKeys.contains("sessionId"));
        assertFalse(tagKeys.contains("app"));
        assertFalse(tagKeys.contains("stream"));
        assertFalse(tagKeys.contains("user"));
        assertFalse(tagKeys.contains("tenant"));
        assertEquals(Set.of("type", "operation", "outcome"), tagKeys);
    }
}
