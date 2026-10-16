package dev.fupez.rpgplayable.player;

/** Server-owned reversible tuning; the fixed level cap bounds processing of huge awards. */
public record ProgressionRules(int baseXp, int xpStep, int killXp, double strengthDamage) {
    public static final ProgressionRules DEFAULT = new ProgressionRules(100, 50, 25, 0.5);

    public ProgressionRules {
        if (baseXp < 1 || baseXp > 1_000_000 || xpStep < 0 || xpStep > 1_000_000
                || killXp < 1 || killXp > 1_000_000 || !Double.isFinite(strengthDamage)
                || strengthDamage < 0 || strengthDamage > 10) {
            throw new IllegalArgumentException("Invalid RPG tuning: XP values must be bounded and damage must be finite in [0, 10]");
        }
    }

    public long xpToNextLevel(int level) {
        if (level < 1 || level > PlayerProgress.MAX_LEVEL) throw new IllegalArgumentException("Invalid level");
        return level == PlayerProgress.MAX_LEVEL ? 0 : baseXp + (long) (level - 1) * xpStep;
    }

    public double strengthBonus(PlayerProgress progress) { return progress.strength() * strengthDamage; }
}
