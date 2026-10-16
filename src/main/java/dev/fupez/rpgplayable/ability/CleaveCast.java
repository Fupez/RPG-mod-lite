package dev.fupez.rpgplayable.ability;

public record CleaveCast(long startedAt, CleaveRules rules) {
    public enum Phase { WINDUP, RECOVERY, COOLDOWN, READY }
    public CleaveCast {
        if (startedAt < 0 || rules == null) throw new IllegalArgumentException("Invalid cast");
    }
    public Phase phase(long now) {
        long elapsed = Math.max(0, now - startedAt);
        if (elapsed < rules.windupTicks()) return Phase.WINDUP;
        if (elapsed < rules.windupTicks() + rules.recoveryTicks()) return Phase.RECOVERY;
        return elapsed < rules.cooldownTicks() ? Phase.COOLDOWN : Phase.READY;
    }
    public boolean impactsAt(long now) { return now - startedAt == rules.windupTicks(); }
    public int remaining(long now) { return (int) Math.max(0, rules.cooldownTicks() - Math.max(0, now - startedAt)); }
}
