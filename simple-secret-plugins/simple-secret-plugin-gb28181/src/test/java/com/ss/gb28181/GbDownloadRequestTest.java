package com.ss.gb28181;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class GbDownloadRequestTest {
    private static final GbPlaybackRange RANGE = new GbPlaybackRange(Instant.EPOCH, Instant.ofEpochSecond(3600));

    @Test
    void defaultsToNormalSpeedAndAcceptsSpeedBoundaries() {
        assertEquals(new GbDownloadRequest(RANGE, 1), GbDownloadRequest.normal(RANGE));
        assertEquals(128, new GbDownloadRequest(RANGE, 128).speed());
        assertEquals(RANGE, new GbDownloadRequest(RANGE, 2).range());
    }

    @Test
    void requiresRangeAndBoundsRequestedSpeed() {
        assertThrows(NullPointerException.class, () -> new GbDownloadRequest(null, 1));
        assertThrows(NullPointerException.class, () -> GbDownloadRequest.normal(null));
        for (int speed : new int[]{Integer.MIN_VALUE, -1, 0, 129, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new GbDownloadRequest(RANGE, speed));
        }
    }
}
