package dev.fupez.rpgplayable.client;

import dev.fupez.rpgplayable.network.ProgressActionPayload;
import dev.fupez.rpgplayable.network.ProgressSnapshotPayload;
import java.util.Optional;

/** Client presentation state, never a source of gameplay authority. */
public final class ClientProgressState {
    public enum Feedback { NONE, WAITING, APPLIED, REJECTED, TIMEOUT }
    private ProgressSnapshotPayload snapshot;
    private long nextRequest = 1;
    private long pendingId;
    private int pendingTicks;
    private int levelUpTicks;
    private Feedback feedback = Feedback.NONE;

    public ProgressSnapshotPayload snapshot() { return snapshot; }
    public boolean pending() { return pendingId != 0; }
    public Feedback feedback() { return feedback; }
    public int levelUpTicks() { return levelUpTicks; }

    public Optional<ProgressActionPayload> beginRequest(int action) {
        if (snapshot == null || pending()) return Optional.empty();
        var p = snapshot.progress();
        if (action == ProgressActionPayload.SELECT_WARRIOR && p.selected()
                || action == ProgressActionPayload.ALLOCATE_STRENGTH && (!p.selected() || p.unspentPoints() == 0))
            return Optional.empty();
        long id = nextRequest;
        nextRequest = nextRequest == Long.MAX_VALUE ? 1 : nextRequest + 1;
        var request = new ProgressActionPayload(action, id);
        pendingId = id;
        pendingTicks = 100;
        feedback = Feedback.WAITING;
        return Optional.of(request);
    }

    /** Returns true once for a newly observed level; never on first sync or rejoin. */
    public boolean accept(ProgressSnapshotPayload update) {
        boolean leveled = snapshot != null && update.progress().level() > snapshot.progress().level();
        snapshot = update;
        if (pending() && pendingId == update.requestId()) {
            pendingId = 0;
            pendingTicks = 0;
            feedback = update.accepted() ? Feedback.APPLIED : Feedback.REJECTED;
        }
        if (leveled) levelUpTicks = 80;
        return leveled;
    }

    public void tick() {
        if (levelUpTicks > 0) levelUpTicks--;
        if (pending() && --pendingTicks <= 0) {
            pendingId = 0;
            feedback = Feedback.TIMEOUT;
        }
    }

    public void clear() {
        snapshot = null;
        pendingId = 0;
        pendingTicks = 0;
        levelUpTicks = 0;
        feedback = Feedback.NONE;
    }
}
