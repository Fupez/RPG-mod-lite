package dev.fupez.rpgplayable.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatPayloadTest {
    @Test void invalidIntentCannotSupplyTargetOrOutOfRangeSlot() {
        assertThrows(IllegalArgumentException.class, () -> new AbilityRequestPayload(3, 1));
        assertThrows(IllegalArgumentException.class, () -> new AbilityRequestPayload(0, 0));
        assertEquals(2, new AbilityRequestPayload(2, 1).slot());
    }
    @Test void snapshotsDoNotExposeMutableServerArrays() {
        int[] cds = {20, 0, 0}, totals = {60, 100, 140}; double[] costs = {20, 25, 35};
        var state = new CombatSnapshotPayload(50, 100, 100, 140, cds, totals, costs, -1, 0, 0, 0, 0, 1, 0);
        cds[0] = 0; costs[0] = -100;
        assertEquals(20, state.cooldowns()[0]); assertEquals(20, state.costs()[0]);
        state.cooldowns()[0] = 0; assertEquals(20, state.cooldowns()[0]);
        assertThrows(IllegalArgumentException.class, () -> new CombatSnapshotPayload(101, 100, 0, 100,
                new int[3], totals, new double[3], -1, 0, 0, 0, 0, 0, 0));
    }
}
