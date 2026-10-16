package dev.fupez.rpgplayable.player;

import dev.fupez.rpgplayable.stats.Stat;
import java.math.BigInteger;
import java.util.Objects;

/** Permanent, arbitrary-precision counters. Each earned level creates exactly one stat point. */
public record PlayerProgress(PlayerClass playerClass, BigInteger level, BigInteger xp, BigInteger unspentPoints,
                             BigInteger strength, BigInteger vitality, BigInteger agility,
                             BigInteger intelligence, BigInteger spirit) {
    public static final PlayerProgress DEFAULT = new PlayerProgress(PlayerClass.UNSELECTED, 1, 0, 0, 0);

    public PlayerProgress {
        Objects.requireNonNull(playerClass, "playerClass");
        for (var counter : new BigInteger[]{level, xp, unspentPoints, strength, vitality, agility, intelligence, spirit}) {
            if (counter == null || counter.signum() < 0) throw new IllegalArgumentException("RPG counters must be nonnegative");
        }
        var points = unspentPoints.add(strength).add(vitality).add(agility).add(intelligence).add(spirit);
        if (level.signum() == 0 || !points.equals(level.subtract(BigInteger.ONE))
                || (playerClass == PlayerClass.UNSELECTED && (!level.equals(BigInteger.ONE) || xp.signum() != 0))) {
            throw new IllegalArgumentException("Invalid RPG progression state");
        }
    }

    public PlayerProgress(PlayerClass playerClass, long level, long xp, long unspentPoints, long strength) {
        this(playerClass, level, xp, unspentPoints, strength, 0, 0, 0, 0);
    }
    public PlayerProgress(PlayerClass playerClass, long level, long xp, long unspentPoints, long strength,
                          long vitality, long agility, long intelligence, long spirit) {
        this(playerClass, BigInteger.valueOf(level), BigInteger.valueOf(xp), BigInteger.valueOf(unspentPoints),
                BigInteger.valueOf(strength), BigInteger.valueOf(vitality), BigInteger.valueOf(agility),
                BigInteger.valueOf(intelligence), BigInteger.valueOf(spirit));
    }
    public boolean selected() { return playerClass != PlayerClass.UNSELECTED; }
    public BigInteger stat(Stat stat) {
        return switch (Objects.requireNonNull(stat, "stat")) {
            case STRENGTH -> strength;
            case VITALITY -> vitality;
            case AGILITY -> agility;
            case INTELLIGENCE -> intelligence;
            case SPIRIT -> spirit;
        };
    }
}
