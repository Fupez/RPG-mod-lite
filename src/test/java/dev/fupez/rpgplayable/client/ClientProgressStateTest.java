package dev.fupez.rpgplayable.client;

import dev.fupez.rpgplayable.network.*;
import dev.fupez.rpgplayable.player.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientProgressStateTest {
    private static ProgressSnapshotPayload snapshot(int level, long requestId, boolean accepted) {
        return new ProgressSnapshotPayload(new PlayerProgress(PlayerClass.WARRIOR, level, 0, level - 1, 0),
                ProgressionRules.DEFAULT.xpToNextLevel(level), 0, requestId, accepted);
    }
    @Test void actionsRequireSnapshotAndOnlyOneMayBePending() {
        var state = new ClientProgressState();
        assertTrue(state.beginRequest(ProgressActionPayload.SELECT_WARRIOR).isEmpty());
        state.accept(new ProgressSnapshotPayload(PlayerProgress.DEFAULT, 100, 0, 0, true));
        var request = state.beginRequest(ProgressActionPayload.SELECT_WARRIOR).orElseThrow();
        assertTrue(request.requestId() > 0);
        assertTrue(state.beginRequest(ProgressActionPayload.SELECT_WARRIOR).isEmpty());
    }
    @Test void unsolicitedOrStaleRepliesCannotAcknowledgeDifferentRequest() {
        var state = new ClientProgressState(); state.accept(snapshot(2, 0, true));
        var request = state.beginRequest(ProgressActionPayload.ALLOCATE_STRENGTH).orElseThrow();
        state.accept(snapshot(2, 0, true)); assertTrue(state.pending());
        state.accept(snapshot(2, request.requestId() + 1, false)); assertTrue(state.pending());
        state.accept(snapshot(2, request.requestId(), false));
        assertFalse(state.pending()); assertEquals(ClientProgressState.Feedback.REJECTED, state.feedback());
    }
    @Test void requestsTimeoutWithoutInventingAnAllocationOrResending() {
        var state = new ClientProgressState(); state.accept(snapshot(2, 0, true));
        state.beginRequest(ProgressActionPayload.ALLOCATE_STRENGTH).orElseThrow();
        for (int i = 0; i < 100; i++) state.tick();
        assertFalse(state.pending()); assertEquals(ClientProgressState.Feedback.TIMEOUT, state.feedback());
        assertEquals(0, state.snapshot().progress().strength());
    }
    @Test void allocationChangesOnlyWhenAuthoritativeReplyArrives() {
        var state = new ClientProgressState(); state.accept(snapshot(2, 0, true));
        var request = state.beginRequest(ProgressActionPayload.ALLOCATE_STRENGTH).orElseThrow();
        assertEquals(0, state.snapshot().progress().strength());
        var allocated = new PlayerProgress(PlayerClass.WARRIOR, 2, 0, 0, 1);
        state.accept(new ProgressSnapshotPayload(allocated, 150, 0.5, request.requestId(), true));
        assertEquals(allocated, state.snapshot().progress());
        assertFalse(state.pending()); assertEquals(ClientProgressState.Feedback.APPLIED, state.feedback());
    }
    @Test void levelFeedbackOnlyOccursForNewLevelWithinCurrentSession() {
        var state = new ClientProgressState();
        assertFalse(state.accept(snapshot(2, 0, true)));
        assertTrue(state.accept(snapshot(3, 0, true)));
        assertFalse(state.accept(snapshot(3, 0, true)));
        state.clear(); assertNull(state.snapshot());
        assertFalse(state.accept(snapshot(50, 0, true)));
        assertEquals(0, state.levelUpTicks());
    }
}
