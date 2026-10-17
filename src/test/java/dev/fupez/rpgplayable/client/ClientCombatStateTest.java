package dev.fupez.rpgplayable.client;

import dev.fupez.rpgplayable.network.CombatSnapshotPayload;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientCombatStateTest {
    private static CombatSnapshotPayload snapshot(long request, int result, int cooldown) {
        return snapshot(request, result, cooldown, -1, 0);
    }
    private static CombatSnapshotPayload snapshot(long request, int result, int cooldown, int activeSlot, int elapsed) {
        return new CombatSnapshotPayload(50, 100, 70, 100, new int[]{cooldown, 0, 0}, new int[]{60, 100, 140},
                new double[]{20, 25, 35}, activeSlot, elapsed, 6, 8, 0, request, result);
    }
    @Test void intentDoesNotPredictResourcesOrCooldownAndOnlyMatchingAckClearsPending() {
        var state = new ClientCombatState();
        assertTrue(state.beginRequest(0).isEmpty());
        state.accept(snapshot(0, CombatSnapshotPayload.UPDATE, 0));
        var request = state.beginRequest(0).orElseThrow();
        assertEquals(50, state.snapshot().mana()); assertEquals(0, state.cooldown(0));
        assertTrue(state.beginRequest(1).isEmpty());
        state.accept(snapshot(request.requestId() + 1, CombatSnapshotPayload.ACCEPTED, 60));
        assertTrue(state.pending());
        state.accept(snapshot(request.requestId(), CombatSnapshotPayload.ACCEPTED, 60));
        assertFalse(state.pending()); assertEquals(60, state.cooldown(0));
    }
    @Test void timeoutAndDisconnectRecoverInputWithoutInventingGameplay() {
        var state = new ClientCombatState(); state.accept(snapshot(0, 0, 0)); state.beginRequest(2).orElseThrow();
        for (int i = 0; i < 100; i++) state.tick();
        assertFalse(state.pending()); assertEquals("timeout", state.feedback()); assertEquals(70, state.snapshot().stamina());
        state.clear(); assertNull(state.snapshot()); assertEquals("ready", state.phase(0));
        assertEquals(0, state.cooldown(0)); assertEquals("", state.feedback());
    }
    @Test void hudCastPhaseAndCooldownFollowServerCorrections() {
        var state = new ClientCombatState();
        state.accept(snapshot(0, CombatSnapshotPayload.UPDATE, 60, 0, 0));
        assertEquals("windup", state.phase(0));
        assertEquals("ready", state.phase(1));
        for (int i = 0; i < 3; i++) state.tick();
        assertEquals("windup", state.phase(0)); assertEquals(57, state.cooldown(0));
        state.accept(snapshot(0, CombatSnapshotPayload.UPDATE, 59, 0, 5));
        assertEquals(59, state.cooldown(0)); assertEquals("windup", state.phase(0));
        state.tick();
        assertEquals("recovery", state.phase(0)); assertEquals(58, state.cooldown(0));
        for (int i = 0; i < 8; i++) state.tick();
        assertEquals("cooldown", state.phase(0)); assertEquals(50, state.cooldown(0));
        state.accept(snapshot(0, CombatSnapshotPayload.UPDATE, 0));
        assertEquals("ready", state.phase(0));
    }
    @Test void interruptedSnapshotEndsHudCastAndKeepsServerCooldown() {
        var state = new ClientCombatState();
        state.accept(snapshot(0, CombatSnapshotPayload.UPDATE, 60, 0, 3));
        assertEquals("windup", state.phase(0));
        state.accept(snapshot(0, CombatSnapshotPayload.INTERRUPTED, 57));
        assertEquals("cooldown", state.phase(0)); assertEquals(57, state.cooldown(0));
        assertEquals("interrupted", state.feedback());
    }
    @Test void unlimitedCountersStayCompactAndRatiosStayFinite() {
        var huge = BigInteger.TEN.pow(1000);
        assertEquals("1.00e1000", ClientNumbers.compact(huge));
        assertEquals(.5, ClientNumbers.ratio(huge, huge.multiply(BigInteger.TWO)), 1e-8);
        assertEquals(1, ClientNumbers.ratio(huge, BigInteger.ONE));
        assertEquals(0, ClientNumbers.ratio(BigInteger.ONE, BigInteger.ZERO));
    }
}
