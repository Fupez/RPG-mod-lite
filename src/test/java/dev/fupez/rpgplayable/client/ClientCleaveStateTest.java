package dev.fupez.rpgplayable.client;

import dev.fupez.rpgplayable.network.CleaveStatePayload;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientCleaveStateTest {
    @Test void waitsForAuthorityAndDoesNotInventCooldownOnRequest() {
        var state = new ClientCleaveState();
        assertFalse(state.beginRequest());
        state.accept(new CleaveStatePayload(0, 100, 6, 8, CleaveStatePayload.UPDATE, false));
        assertTrue(state.beginRequest());
        assertFalse(state.beginRequest());
        assertEquals(0, state.remaining());
        state.accept(new CleaveStatePayload(100, 100, 6, 8, CleaveStatePayload.ACCEPTED, false));
        assertFalse(state.pending());
        assertFalse(state.beginRequest());
        assertEquals("windup", state.phase());
        for (int i = 0; i < 6; i++) state.tick();
        assertEquals("recovery", state.phase());
        for (int i = 0; i < 8; i++) state.tick();
        assertEquals("cooldown", state.phase());
    }
    @Test void disconnectAndTimeoutDoNotLeaveStuckInput() {
        var state = new ClientCleaveState();
        state.accept(new CleaveStatePayload(0, 100, 6, 8, 0, false));
        assertTrue(state.beginRequest());
        for (int i = 0; i < 100; i++) state.tick();
        assertFalse(state.pending());
        assertEquals("timeout", state.feedback());
        state.clear();
        assertFalse(state.beginRequest());
        assertEquals(0, state.remaining());
    }
    @Test void handPoseRisesDuringWindupAndReturnsDuringRecovery() {
        var state = new ClientCleaveState();
        assertEquals(0, state.pose(0));
        state.accept(new CleaveStatePayload(100, 100, 6, 8, 1, false));
        assertEquals(0, state.pose(0));
        state.accept(new CleaveStatePayload(97, 100, 6, 8, 0, false));
        assertEquals(0.5f, state.pose(0), 0.001);
        state.accept(new CleaveStatePayload(94, 100, 6, 8, 0, false));
        assertEquals(1, state.pose(0));
        state.accept(new CleaveStatePayload(90, 100, 6, 8, 0, false));
        assertEquals(0.5f, state.pose(0), 0.001);
        state.accept(new CleaveStatePayload(90, 100, 6, 8, 3, true));
        assertEquals(0, state.pose(0));
    }
    @Test void interruptedCastStaysInCooldownAcrossUpdates() {
        var state = new ClientCleaveState();
        state.accept(new CleaveStatePayload(99, 100, 6, 8, CleaveStatePayload.INTERRUPTED, true));
        assertEquals("cooldown", state.phase());
        state.accept(new CleaveStatePayload(98, 100, 6, 8, CleaveStatePayload.UPDATE, true));
        assertEquals("cooldown", state.phase());
        state.accept(new CleaveStatePayload(0, 100, 6, 8, CleaveStatePayload.UPDATE, false));
        assertEquals("ready", state.phase());
    }
    @Test void authoritativeResyncCorrectsLocalCountdown() {
        var state = new ClientCleaveState();
        state.accept(new CleaveStatePayload(80, 100, 6, 8, 0, false));
        state.tick(); assertEquals(79, state.remaining());
        state.accept(new CleaveStatePayload(90, 100, 6, 8, 0, false));
        assertEquals(90, state.remaining());
        assertThrows(IllegalArgumentException.class, () -> new CleaveStatePayload(101, 100, 6, 8, 0, false));
    }
}
