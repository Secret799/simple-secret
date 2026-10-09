package com.ss.gb28181;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GbAlarmResetCommandTest {
    @Test
    void representsAllAndCopiesMethods() {
        assertEquals(Set.of(), GbAlarmResetCommand.all().methods());
        assertNull(GbAlarmResetCommand.all().type());

        var methods = new HashSet<>(Set.of(7, 2));
        var command = new GbAlarmResetCommand(methods, null);
        methods.clear();
        assertEquals(Set.of(2, 7), command.methods());
        assertThrows(UnsupportedOperationException.class, () -> command.methods().add(1));
    }

    @Test
    void acceptsSingleTypedMethodsAtStandardBounds() {
        assertEquals(1, new GbAlarmResetCommand(Set.of(2), 1).type());
        assertEquals(5, new GbAlarmResetCommand(Set.of(2), 5).type());
        assertEquals(1, new GbAlarmResetCommand(Set.of(5), 1).type());
        assertEquals(13, new GbAlarmResetCommand(Set.of(5), 13).type());
        assertEquals(1, new GbAlarmResetCommand(Set.of(6), 1).type());
        assertEquals(2, new GbAlarmResetCommand(Set.of(6), 2).type());
        assertEquals(Set.of(1, 3, 7), new GbAlarmResetCommand(Set.of(1, 3, 7), null).methods());
    }

    @Test
    void rejectsNullInvalidMethodsAndAmbiguousTypes() {
        assertThrows(NullPointerException.class, () -> new GbAlarmResetCommand(null, null));
        assertThrows(NullPointerException.class, () -> new GbAlarmResetCommand(setWithNull(), null));
        for (int method : new int[]{Integer.MIN_VALUE, 0, 8, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new GbAlarmResetCommand(Set.of(method), null));
        }
        assertThrows(IllegalArgumentException.class, () -> new GbAlarmResetCommand(Set.of(), 1));
        assertThrows(IllegalArgumentException.class, () -> new GbAlarmResetCommand(Set.of(2, 5), 1));
        assertThrows(IllegalArgumentException.class, () -> new GbAlarmResetCommand(Set.of(1), 1));
    }

    @Test
    void rejectsTypedMethodValuesOutsideTheirIndividualRanges() {
        for (int type : new int[]{Integer.MIN_VALUE, 0, 6, 14, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new GbAlarmResetCommand(Set.of(2), type));
        }
        for (int type : new int[]{Integer.MIN_VALUE, 0, 14, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new GbAlarmResetCommand(Set.of(5), type));
        }
        for (int type : new int[]{Integer.MIN_VALUE, 0, 3, 13, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new GbAlarmResetCommand(Set.of(6), type));
        }
    }

    private static Set<Integer> setWithNull() {
        var methods = new HashSet<Integer>();
        methods.add(null);
        return methods;
    }
}
