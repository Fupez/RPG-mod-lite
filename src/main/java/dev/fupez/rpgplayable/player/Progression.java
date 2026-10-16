package dev.fupez.rpgplayable.player;

import dev.fupez.rpgplayable.stats.Stat;
import java.math.BigInteger;
import java.util.Objects;

public final class Progression {
    private static final BigInteger TWO = BigInteger.TWO;
    private Progression() { }
    public static PlayerProgress select(PlayerProgress current, PlayerClass selection) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(selection, "selection");
        if (current.selected() || selection == PlayerClass.UNSELECTED) return current;
        return new PlayerProgress(selection, 1, 0, 0, 0);
    }
    public static PlayerProgress selectWarrior(PlayerProgress current) { return select(current, PlayerClass.WARRIOR); }
    public static PlayerProgress allocateStrength(PlayerProgress current) { return allocate(current, Stat.STRENGTH); }
    public static PlayerProgress allocate(PlayerProgress current, Stat stat) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(stat, "stat");
        if (!current.selected() || current.unspentPoints().signum() == 0) return current;
        return new PlayerProgress(current.playerClass(), current.level(), current.xp(), current.unspentPoints().subtract(BigInteger.ONE),
                increment(current, stat, Stat.STRENGTH), increment(current, stat, Stat.VITALITY),
                increment(current, stat, Stat.AGILITY), increment(current, stat, Stat.INTELLIGENCE), increment(current, stat, Stat.SPIRIT));
    }
    private static BigInteger increment(PlayerProgress current, Stat chosen, Stat field) {
        return current.stat(field).add(chosen == field ? BigInteger.ONE : BigInteger.ZERO);
    }
    public static PlayerProgress addXp(PlayerProgress current, long amount, ProgressionRules rules) {
        return addXp(current, BigInteger.valueOf(amount), rules);
    }
    /** Solve the arithmetic-series XP curve in bulk, including when a tuning change normalizes saved XP. */
    public static PlayerProgress addXp(PlayerProgress current, BigInteger amount, ProgressionRules rules) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(rules, "rules");
        if (amount.signum() < 0) throw new IllegalArgumentException("XP award must not be negative");
        if (!current.selected()) return current;
        var available = current.xp().add(amount);
        var threshold = rules.xpToNextLevel(current.level());
        var step = BigInteger.valueOf(rules.xpStep());
        BigInteger gained;
        if (step.signum() == 0) gained = available.divide(threshold);
        else {
            // cost(n) = (step*n*n + (2*threshold-step)*n)/2.
            var linear = threshold.multiply(TWO).subtract(step);
            var discriminant = linear.multiply(linear).add(step.multiply(available).shiftLeft(3));
            gained = discriminant.sqrt().subtract(linear).divide(step.multiply(TWO));
        }
        var spent = gained.multiply(threshold.multiply(TWO).add(gained.subtract(BigInteger.ONE).multiply(step))).divide(TWO);
        return new PlayerProgress(current.playerClass(), current.level().add(gained), available.subtract(spent),
                current.unspentPoints().add(gained), current.strength(), current.vitality(), current.agility(), current.intelligence(), current.spirit());
    }
}
