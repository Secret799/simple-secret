package com.ss.gb28181;

import com.ss.gb28181.internal.GbXml;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class PresetAccumulatorTest {
    @Test void capsAt255AndRejectsInvalidTotalsBeforeAccumulating() {
        var accumulator = new PresetAccumulator();
        for (int total : List.of(-1, 256, Integer.MAX_VALUE))
            assertThrows(IllegalArgumentException.class, () -> accumulator.accept(page(total, List.of())));
        var presets = IntStream.rangeClosed(1, 255).mapToObj(id -> new PresetItem("" + id, "Preset " + id)).toList();
        assertEquals(presets, accumulator.accept(page(255, presets)));
    }

    @Test void retainsFirstSeenExactIdsAndProducesImmutableSnapshot() {
        var accumulator = new PresetAccumulator();
        var first = new PresetItem("01", "Gate");
        assertNull(accumulator.accept(page(2, List.of(first))));
        assertNull(accumulator.accept(page(2, List.of(first, first))));
        var second = new PresetItem("1", "");
        var result = accumulator.accept(page(2, List.of(second)));
        assertEquals(List.of(first, second), result);
        assertThrows(UnsupportedOperationException.class, () -> result.clear());
        assertEquals(List.of(), new PresetAccumulator().accept(page(0, List.of())));
    }

    @Test void rejectsChangedTotalsConflictingNamesAndUniqueOverflow() {
        var first = new PresetItem("1", "Gate");
        var changed = new PresetAccumulator();
        changed.accept(page(2, List.of(first)));
        assertThrows(IllegalArgumentException.class, () -> changed.accept(page(3, List.of())));
        var conflict = new PresetAccumulator();
        conflict.accept(page(2, List.of(first)));
        assertThrows(IllegalArgumentException.class,
                () -> conflict.accept(page(2, List.of(new PresetItem("1", "Changed")))));
        var overflow = new PresetAccumulator();
        assertThrows(IllegalArgumentException.class,
                () -> overflow.accept(page(1, List.of(first, new PresetItem("2", "Door")))));
    }

    static GbXml.Message page(int total, List<PresetItem> presets) {
        return new GbXml.Message("Response", "PresetQuery", 1, GbPresetServerTest.CHANNEL, null, total,
                List.of(), null, null, null, List.of(), null, presets);
    }
}
