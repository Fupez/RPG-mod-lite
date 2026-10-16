package dev.fupez.rpgplayable.player;

import java.util.Objects;

public final class Progression {
    private Progression() { }

    public static PlayerProgress selectWarrior(PlayerProgress current) {
        return current.selected() ? current : new PlayerProgress(PlayerClass.WARRIOR, 1, 0, 0, 0);
    }

    public static PlayerProgress allocateStrength(PlayerProgress current) {
        if (!current.selected() || current.unspentPoints() == 0) return current;
        return new PlayerProgress(current.playerClass(), current.level(), current.xp(),
                current.unspentPoints() - 1, current.strength() + 1);
    }

    public static PlayerProgress addXp(PlayerProgress current, long amount, ProgressionRules rules) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(rules, "rules");
        if (amount < 0) throw new IllegalArgumentException("XP award must not be negative");
        if (!current.selected()) return current;
        int level = current.level();
        long xp = current.xp();
        int points = current.unspentPoints();
        long remaining = amount;
        while (level < PlayerProgress.MAX_LEVEL) {
            long threshold = rules.xpToNextLevel(level);
            // Normalize legitimate saved XP when server tuning changes, without xp + amount overflow.
            if (xp >= threshold) {
                xp -= threshold;
            } else {
                long needed = threshold - xp;
                if (remaining < needed) {
                    xp += remaining;
                    break;
                }
                remaining -= needed;
                xp = 0;
            }
            level++;
            points++;
        }
        if (level == PlayerProgress.MAX_LEVEL) xp = 0;
        return new PlayerProgress(current.playerClass(), level, xp, points, current.strength());
    }
}
