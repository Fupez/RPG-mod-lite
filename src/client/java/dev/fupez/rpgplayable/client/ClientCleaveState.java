package dev.fupez.rpgplayable.client;

import dev.fupez.rpgplayable.network.CleaveStatePayload;

/** Presentation clock only. Server snapshots continuously correct latency and drift. */
public final class ClientCleaveState {
    private boolean known, interrupted;
    private int remaining, total = 100, windup = 6, recovery = 8, pending, feedbackTicks;
    private String feedback = "";
    public int remaining() { return remaining; }
    public int total() { return total; }
    public boolean pending() { return pending > 0; }
    public boolean known() { return known; }
    public String feedback() { return feedbackTicks > 0 ? feedback : ""; }
    public String phase() {
        if (remaining == 0) return "ready";
        if (interrupted) return "cooldown";
        int elapsed = total - remaining;
        if (elapsed < windup) return "windup";
        return elapsed < windup + recovery ? "recovery" : "cooldown";
    }
    /** Visual weight only; no prediction can authorize gameplay. */
    public float pose(float tickProgress) {
        if (!known || interrupted || remaining == 0) return 0;
        float elapsed = total - remaining + Math.max(0, Math.min(1, tickProgress));
        if (elapsed < windup) return elapsed / windup;
        return Math.max(0, 1 - (elapsed - windup) / recovery);
    }
    public boolean beginRequest() {
        if (!known || pending() || remaining > 0) return false;
        pending = 100;
        return true;
    }
    public void accept(CleaveStatePayload value) {
        interrupted = value.interrupted();
        known = true; remaining = value.remaining(); total = value.total();
        windup = value.windup(); recovery = value.recovery();
        if (value.result() != CleaveStatePayload.UPDATE) pending = 0;
        if (value.result() == CleaveStatePayload.UNAVAILABLE) feedback = remaining > 0 ? "cooldown" : "unavailable";
        else if (value.result() == CleaveStatePayload.INTERRUPTED) feedback = "interrupted";
        else if (value.result() == CleaveStatePayload.ACCEPTED) feedback = "";
        if (value.result() != CleaveStatePayload.UPDATE) feedbackTicks = 40;
    }
    public void tick() {
        if (remaining > 0) remaining--;
        if (feedbackTicks > 0) feedbackTicks--;
        if (pending > 0 && --pending == 0) { feedback = "timeout"; feedbackTicks = 60; }
    }
    public void clear() { known = false; interrupted = false; remaining = 0; pending = 0; feedbackTicks = 0; feedback = ""; }
}
