package com.ss.gb28181;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GbDragZoomCommandTest {
    @Test
    void preservesValuesAndRejectsValuesOutsidePixelRange() {
        var command = new GbDragZoomCommand(640, 360, 320, 180, 640, 360);
        assertEquals(640, command.length());
        assertEquals(360, command.width());
        assertEquals(new GbDragZoomCommand(640, 360, 320, 180, 640, 360), command);
        for (int value : new int[]{-1, 1_000_001, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new GbDragZoomCommand(value, 1, 1, 1, 1, 1));
        }
    }
}
