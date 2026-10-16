package dev.fupez.rpgplayable.client;

import dev.fupez.rpgplayable.network.CombatSnapshotPayload;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientCombatStateTest {
    private static CombatSnapshotPayload snapshot(long request, int result, int cooldown) {
        return new CombatSnapshotPayload(50, 100, 70, 100, new int[]{cooldown, 0, 0}, new int[]{60, 100, 140},
                new double[]{20, 25, 35}, -1, 0, 6, 8, 0, request, result);
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
        state.clear(); assertNull(state.snapshot()); assertEquals(0, state.pose(0));
    }
    @Test void animationAndCooldownUseIndependentServerClocks() {
        var state = new ClientCombatState(); state.accept(snapshot(0, 0, 60)); state.animate(3, 6, 8);
        for (int i = 0; i < 3; i++) state.tick();
        assertEquals(.5, state.pose(0), .001); assertEquals(57, state.cooldown(0));
        state.accept(snapshot(0, 0, 59)); assertEquals(59, state.cooldown(0));
        for (int i = 0; i < 3; i++) state.tick();
        assertEquals(1, state.pose(0), .001);
        state.animate(0, 0, 0); assertEquals(0, state.pose(0));
    }
    @Test void unlimitedCountersStayCompactAndRatiosStayFinite() {
        var huge = BigInteger.TEN.pow(1000);
        assertEquals("1.00e1000", ClientNumbers.compact(huge));
        assertEquals(.5, ClientNumbers.ratio(huge, huge.multiply(BigInteger.TWO)), 1e-8);
        assertEquals(1, ClientNumbers.ratio(huge, BigInteger.ONE));
        assertEquals(0, ClientNumbers.ratio(BigInteger.ONE, BigInteger.ZERO));
    }
}
