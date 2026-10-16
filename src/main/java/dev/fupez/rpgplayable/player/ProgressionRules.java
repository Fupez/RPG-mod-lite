package dev.fupez.rpgplayable.player;

import dev.fupez.rpgplayable.stats.DerivedStats;
import dev.fupez.rpgplayable.stats.StatTuning;
import java.math.BigInteger;

/** Server-owned tuning. Counter arithmetic has no level or integer-width limit. */
public record ProgressionRules(int baseXp, int xpStep, int killXp, double strengthDamage, StatTuning stats) {
    public static final ProgressionRules DEFAULT = new ProgressionRules(100, 50, 25, 0.5);
    public ProgressionRules(int baseXp, int xpStep, int killXp, double strengthDamage) {
        this(baseXp, xpStep, killXp, strengthDamage, StatTuning.DEFAULT);
    }
    public ProgressionRules {
        java.util.Objects.requireNonNull(stats, "stats");
        if (baseXp < 1 || baseXp > 1_000_000 || xpStep < 0 || xpStep > 1_000_000
                || killXp < 1 || killXp > 1_000_000 || !Double.isFinite(strengthDamage)
                || strengthDamage < 0 || strengthDamage > 10)
            throw new IllegalArgumentException("Invalid RPG tuning: XP must be bounded and damage finite in [0, 10]");
    }
    public BigInteger xpToNextLevel(BigInteger level) {
        if (level == null || level.signum() <= 0) throw new IllegalArgumentException("Invalid level");
        return BigInteger.valueOf(baseXp).add(level.subtract(BigInteger.ONE).multiply(BigInteger.valueOf(xpStep)));
    }
    public long xpToNextLevel(int level) { return xpToNextLevel(BigInteger.valueOf(level)).longValueExact(); }
    public double strengthBonus(PlayerProgress progress) { return DerivedStats.calculate(progress, this).meleeBonus(); }
}
