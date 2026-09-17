package com.ss.gb28181;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class GbPlaybackModelsTest {
    @Test
    void acceptsWholeSecondUtcRangeIncludingEpochAndYear9999() {
        var range = new GbPlaybackRange(Instant.EPOCH, Instant.ofEpochSecond(253402300799L));
        assertEquals(Instant.EPOCH, range.startTime());
        assertEquals(Instant.parse("9999-12-31T23:59:59Z"), range.endTime());
        assertEquals(range, new GbPlaybackRange(range.startTime(), range.endTime()));
    }

    @Test
    void rejectsFractionalReversedEmptyAndUnrepresentableRanges() {
        Instant start = Instant.parse("2026-09-15T00:00:00Z");
        Instant end = start.plusSeconds(1);
        assertThrows(NullPointerException.class, () -> new GbPlaybackRange(null, end));
        assertThrows(NullPointerException.class, () -> new GbPlaybackRange(start, null));
        assertThrows(IllegalArgumentException.class, () -> new GbPlaybackRange(start, start));
        assertThrows(IllegalArgumentException.class, () -> new GbPlaybackRange(end, start));
        assertThrows(IllegalArgumentException.class, () -> new GbPlaybackRange(start.plusNanos(1), end));
        assertThrows(IllegalArgumentException.class, () -> new GbPlaybackRange(start, end.plusNanos(1)));
        assertThrows(IllegalArgumentException.class, () -> new GbPlaybackRange(Instant.ofEpochSecond(-1), end));
        assertThrows(IllegalArgumentException.class, () -> new GbPlaybackRange(start, Instant.ofEpochSecond(253402300800L)));
        assertThrows(IllegalArgumentException.class, () -> new GbPlaybackRange(Instant.MIN, Instant.MAX));
    }

    @Test
    void createsPauseResumeSeekAndSupportedScales() {
        assertEquals(GbPlaybackControl.Type.PAUSE, GbPlaybackControl.pause().type());
        assertNull(GbPlaybackControl.pause().position());
        assertEquals(1.0, GbPlaybackControl.pause().scale());
        assertEquals(GbPlaybackControl.Type.RESUME, GbPlaybackControl.resume().type());
        assertNull(GbPlaybackControl.resume().position());
        assertEquals(1.0, GbPlaybackControl.resume().scale());
        for (long seconds : new long[]{0, 100, Long.MAX_VALUE}) {
            var seek = GbPlaybackControl.seek(Duration.ofSeconds(seconds));
            assertEquals(GbPlaybackControl.Type.SEEK, seek.type());
            assertEquals(Duration.ofSeconds(seconds), seek.position());
            assertEquals(1.0, seek.scale());
        }
        for (double scale : new double[]{0.25, 0.5, 1, 2, 4}) {
            var control = GbPlaybackControl.speed(scale);
            assertEquals(GbPlaybackControl.Type.SPEED, control.type());
            assertNull(control.position());
            assertEquals(scale, control.scale());
        }
    }

    @Test
    void rejectsUnsupportedSpeedAndInvalidSeek() {
        assertThrows(NullPointerException.class, () -> GbPlaybackControl.seek(null));
        for (Duration duration : new Duration[]{Duration.ofSeconds(-1), Duration.ofNanos(-1), Duration.ofNanos(1)}) {
            assertThrows(IllegalArgumentException.class, () -> GbPlaybackControl.seek(duration));
        }
        for (double scale : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -1, 0, -0.0, 0.1, 3, 8}) {
            assertThrows(IllegalArgumentException.class, () -> GbPlaybackControl.speed(scale));
        }
    }
}
