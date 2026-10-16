package dev.fupez.rpgplayable.ability;

/** Prototype tuning; ticks use the server clock, damage uses native health units. */
public record CleaveRules(int windupTicks, int recoveryTicks, int cooldownTicks, double range, double damage) {
    public static final CleaveRules DEFAULT = new CleaveRules(6, 8, 100, 3, 6);
    public CleaveRules {
        if (windupTicks < 1 || recoveryTicks < 1 || cooldownTicks < windupTicks + (long) recoveryTicks
                || cooldownTicks > 72000 || !Double.isFinite(range) || range < 1 || range > 6
                || !Double.isFinite(damage) || damage <= 0 || damage > 100)
            throw new IllegalArgumentException("Invalid Cleave tuning");
    }
    /** A 100-degree horizontal cone, with a two-block vertical tolerance. Minecraft yaw 0 faces +Z. */
    public boolean inArc(double dx, double dy, double dz, float yaw) {
        double distance = dx * dx + dz * dz;
        if (!Double.isFinite(distance) || !Double.isFinite(dy) || !Float.isFinite(yaw)
                || distance > range * range || Math.abs(dy) > 2) return false;
        if (distance < 1.0e-8) return true;
        double angle = Math.toRadians(yaw);
        return (dx * -Math.sin(angle) + dz * Math.cos(angle)) / Math.sqrt(distance) >= Math.cos(Math.toRadians(50));
    }
}
