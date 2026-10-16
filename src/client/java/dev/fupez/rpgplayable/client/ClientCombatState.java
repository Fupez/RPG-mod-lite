package dev.fupez.rpgplayable.client;

import dev.fupez.rpgplayable.network.CombatSnapshotPayload;
import dev.fupez.rpgplayable.network.AbilityRequestPayload;
import java.util.Optional;

/** Server-owned values with a local presentation clock; intent never spends resources. */
public final class ClientCombatState {
    private CombatSnapshotPayload snapshot;
    private int[] cooldowns = new int[3];
    private long sequence = 1, pendingId;
    private int pendingTicks, feedbackTicks, animationTicks, windup, recovery, style, castElapsed;
    private String feedback = "";
    public CombatSnapshotPayload snapshot() { return snapshot; }
    public int cooldown(int slot) { return cooldowns[slot]; }
    public boolean pending() { return pendingId != 0; }
    public String feedback() { return feedbackTicks > 0 ? feedback : ""; }
    public int style() { return style; }
    public float pose(float delta) {
        if (windup + recovery == 0) return 0;
        float elapsed = animationTicks + Math.max(0, Math.min(1, delta));
        if (elapsed < windup) return elapsed / Math.max(1, windup);
        return Math.max(0, 1 - (elapsed - windup) / Math.max(1, recovery));
    }
    public void animate(int style, int windup, int recovery) {
        this.style = style; this.windup = windup; this.recovery = recovery; animationTicks = 0;
    }
    public Optional<AbilityRequestPayload> beginRequest(int slot) {
        if (snapshot == null || pending() || slot < 0 || slot > 2) return Optional.empty();
        // Even local cooldown rejection remains server-owned; requests contain intent only.
        pendingId = sequence; sequence = sequence == Long.MAX_VALUE ? 1 : sequence + 1;
        pendingTicks = 100;
        return Optional.of(new AbilityRequestPayload(slot, pendingId));
    }
    public void accept(CombatSnapshotPayload value) {
        snapshot = value; cooldowns = value.cooldowns(); castElapsed = value.castElapsed();
        if (value.result() == CombatSnapshotPayload.INTERRUPTED) animate(0, 0, 0);
        if (pendingId != 0 && value.requestId() == pendingId) { pendingId = 0; pendingTicks = 0; }
        if (value.result() != CombatSnapshotPayload.UPDATE) {
            feedback = switch (value.result()) {
                case CombatSnapshotPayload.ACCEPTED -> "";
                case CombatSnapshotPayload.NOT_READY -> "not_ready";
                case CombatSnapshotPayload.NO_RESOURCE -> "no_resource";
                case CombatSnapshotPayload.INTERRUPTED -> "interrupted";
                case CombatSnapshotPayload.BLOCKED -> "blocked";
                default -> "unavailable";
            };
            feedbackTicks = 50;
        }
    }
    public String phase(int slot) {
        if (snapshot != null && snapshot.activeSlot() == slot && castElapsed < snapshot.windupTicks() + snapshot.recoveryTicks())
            return castElapsed < snapshot.windupTicks() ? "windup" : "recovery";
        return cooldowns[slot] > 0 ? "cooldown" : "ready";
    }
    public void tick() {
        if (snapshot != null && castElapsed < snapshot.windupTicks() + snapshot.recoveryTicks()) castElapsed++;
        for (int i = 0; i < 3; i++) if (cooldowns[i] > 0) cooldowns[i]--;
        if (animationTicks < windup + recovery) animationTicks++;
        if (feedbackTicks > 0) feedbackTicks--;
        if (pendingId != 0 && --pendingTicks <= 0) { pendingId = 0; feedback = "timeout"; feedbackTicks = 60; }
    }
    public void clear() {
        snapshot = null; cooldowns = new int[3]; pendingId = 0; pendingTicks = 0;
        feedbackTicks = 0; windup = recovery = animationTicks = castElapsed = 0; feedback = "";
    }
}
