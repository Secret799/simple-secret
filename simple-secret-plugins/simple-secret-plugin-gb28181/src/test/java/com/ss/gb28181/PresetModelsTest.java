package com.ss.gb28181;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PresetModelsTest {
    @Test void encodesStandardPresetOperationsAndChecksum() {
        assertEquals("A50F018100010037", PresetCommand.set(1).hex());
        assertEquals("A50F018200010038", PresetCommand.goTo(1).hex());
        assertEquals("A50F018300FF0037", PresetCommand.remove(255).hex());
        assertEquals("A50F018100FF0035", PresetCommand.set(255).hex());
        assertEquals("A50F018200FF0036", PresetCommand.goTo(255).hex());
        assertEquals("A50F018300010039", PresetCommand.remove(1).hex());
        assertEquals(new PresetCommand(PresetCommand.Action.GOTO, 17), PresetCommand.goTo(17));
    }

    @Test void rejectsReservedAndUnencodablePresetNumbers() {
        for (int id : new int[]{Integer.MIN_VALUE, -1, 0, 256, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> PresetCommand.set(id));
            assertThrows(IllegalArgumentException.class, () -> PresetCommand.goTo(id));
            assertThrows(IllegalArgumentException.class, () -> PresetCommand.remove(id));
        }
        assertThrows(IllegalArgumentException.class, () -> new PresetCommand(null, 1));
    }

    @Test void preservesStringIdsAndRequiredButEmptyNames() {
        var item = new PresetItem("001", "入口 & 北门");
        assertEquals("001", item.presetId());
        assertEquals("入口 & 北门", item.presetName());
        assertEquals("", new PresetItem("vendor-id", "").presetName());
        assertEquals(64, new PresetItem("p".repeat(64), "n".repeat(256)).presetId().length());
    }

    @Test void rejectsInvalidOrUnboundedPresetMetadata() {
        for (String id : new String[]{null, "", "  ", "p".repeat(65)}) {
            assertThrows(IllegalArgumentException.class, () -> new PresetItem(id, "Gate"));
        }
        assertThrows(IllegalArgumentException.class, () -> new PresetItem("1", null));
        assertThrows(IllegalArgumentException.class, () -> new PresetItem("1", "n".repeat(257)));
    }
}
