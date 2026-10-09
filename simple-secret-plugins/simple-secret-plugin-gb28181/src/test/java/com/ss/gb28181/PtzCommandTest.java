package com.ss.gb28181;

import org.junit.jupiter.api.Test;

import static com.ss.gb28181.PtzCommand.Pan;
import static com.ss.gb28181.PtzCommand.Tilt;
import static com.ss.gb28181.PtzCommand.Zoom;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PtzCommandTest {
    @Test
    void encodesExplicitStop() {
        assertEquals("A50F0100000000B5", PtzCommand.stop().hex());
    }

    @Test
    void encodesEachDirectionUsingTheStandardBitLayout() {
        assertEquals("A50F0101200000D6", PtzCommand.move(Pan.RIGHT, Tilt.NONE, 32, 0).hex());
        assertEquals("A50F0102200000D7", PtzCommand.move(Pan.LEFT, Tilt.NONE, 32, 0).hex());
        assertEquals("A50F0108002000DD", PtzCommand.move(Pan.NONE, Tilt.UP, 0, 32).hex());
        assertEquals("A50F0104002000D9", PtzCommand.move(Pan.NONE, Tilt.DOWN, 0, 32).hex());
        assertEquals("A50F0110000020E5", PtzCommand.zoom(Zoom.IN, 2).hex());
        assertEquals("A50F0120000020F5", PtzCommand.zoom(Zoom.OUT, 2).hex());
    }

    @Test
    void combinesThreeAxesAndKeepsOnlyTheLowChecksumByte() {
        // Table A.5 example 8: right + up + zoom out has command byte 29H.
        assertEquals("A50F01292040306E",
                new PtzCommand(Pan.RIGHT, Tilt.UP, Zoom.OUT, 32, 64, 3).hex());
        assertEquals("A50F0116FFFFF0B9",
                new PtzCommand(Pan.LEFT, Tilt.DOWN, Zoom.IN, 255, 255, 15).hex());
    }

    @Test
    void acceptsMinimumSpeedWithoutTurningMotionIntoStop() {
        // Table A.4 defines speeds from 00H/0H (slowest) to FFH/FH (fastest).
        assertEquals("A50F0101000000B6", PtzCommand.move(Pan.RIGHT, Tilt.NONE, 0, 0).hex());
        assertEquals("A50F0108000000BD", PtzCommand.move(Pan.NONE, Tilt.UP, 0, 0).hex());
        assertEquals("A50F0110000000C5", PtzCommand.zoom(Zoom.IN, 0).hex());
    }

    @Test
    void rejectsOutOfRangeSpeedsBeforeEncoding() {
        for (int speed : new int[]{Integer.MIN_VALUE, -1, 256, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> PtzCommand.move(Pan.RIGHT, Tilt.NONE, speed, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> PtzCommand.move(Pan.NONE, Tilt.UP, 0, speed));
        }
        for (int speed : new int[]{Integer.MIN_VALUE, -1, 16, 255, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> PtzCommand.zoom(Zoom.IN, speed));
        }
    }

    @Test
    void rejectsSpeedsForInactiveAxes() {
        assertThrows(IllegalArgumentException.class,
                () -> new PtzCommand(Pan.NONE, Tilt.UP, Zoom.IN, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new PtzCommand(Pan.RIGHT, Tilt.NONE, Zoom.IN, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new PtzCommand(Pan.RIGHT, Tilt.UP, Zoom.NONE, 1, 1, 1));
    }

    @Test
    void rejectsMissingDirections() {
        assertThrows(IllegalArgumentException.class,
                () -> new PtzCommand(null, Tilt.NONE, Zoom.NONE, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PtzCommand(Pan.NONE, null, Zoom.NONE, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new PtzCommand(Pan.NONE, Tilt.NONE, null, 0, 0, 0));
    }
}
