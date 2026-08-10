package com.ss.easymedia.webrtc.id;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecureWebRtcSessionIdGeneratorTest {

    @Test
    void shouldGenerateUrlSafeUnique192BitIds() {
        WebRtcSessionIdGenerator generator = new SecureWebRtcSessionIdGenerator(new SecureRandom());
        Set<String> ids = IntStream.range(0, 1000)
                .mapToObj(index -> generator.generate())
                .collect(Collectors.toSet());

        assertEquals(1000, ids.size());
        assertTrue(ids.stream().allMatch(id -> id.matches("[A-Za-z0-9_-]{32}")));
    }
}
