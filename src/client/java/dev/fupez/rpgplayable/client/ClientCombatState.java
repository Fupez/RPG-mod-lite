package dev.fupez.rpgplayable.client;

import dev.fupez.rpgplayable.network.CombatSnapshotPayload;
import dev.fupez.rpgplayable.network.AbilityRequestPayload;
import java.util.Optional;

/** Server-owned HUD values with local countdowns; intent never spends resources. */
public final class ClientCombatState {
    private CombatSnapshotPayload snapshot;
    private int[] cooldowns = new int[3];
    private long sequence = 1, pendingId;
    private int pendingTicks, feedbackTicks, castElapsed;
    private int pendingSlot = -1, feedbackSlot = -1;
    private String feedback = "";
    public CombatSnapshotPayload snapshot() { return snapshot; }
    public int cooldown(int slot) { return cooldowns[slot]; }
    public boolean pending() { return pendingId != 0; }
    public String feedback() { return feedbackTicks > 0 ? feedback : ""; }
    public int feedbackSlot() { return feedbackSlot; }
    public Optional<AbilityRequestPayload> beginRequest(int slot) {
        if (snapshot == null || pending() || slot < 0 || slot > 2) return Optional.empty();
        // Even local cooldown rejection remains server-owned; requests contain intent only.
        pendingId = sequence; sequence = sequence == Long.MAX_VALUE ? 1 : sequence + 1;
        pendingSlot = slot;
        pendingTicks = 100;
        return Optional.of(new AbilityRequestPayload(slot, pendingId));
    }
    public void accept(CombatSnapshotPayload value) {
        if (value.result() == CombatSnapshotPayload.INTERRUPTED && snapshot != null && snapshot.activeSlot() >= 0)
            feedbackSlot = snapshot.activeSlot();
        snapshot = value; cooldowns = value.cooldowns(); castElapsed = value.castElapsed();
        if (pendingId != 0 && value.requestId() == pendingId) {
            feedbackSlot = pendingSlot; pendingSlot = -1; pendingId = 0; pendingTicks = 0;
        }
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
        if (feedbackTicks > 0) feedbackTicks--;
        if (pendingId != 0 && --pendingTicks <= 0) {
            feedbackSlot = pendingSlot; pendingSlot = -1; pendingId = 0; feedback = "timeout"; feedbackTicks = 60;
        }
    }
    public void clear() {
        snapshot = null; cooldowns = new int[3]; pendingId = 0; pendingTicks = 0;
        feedbackTicks = 0; castElapsed = 0; feedback = "";
        pendingSlot = feedbackSlot = -1;
    }
}
