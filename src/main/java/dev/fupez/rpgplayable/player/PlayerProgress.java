package dev.fupez.rpgplayable.player;

import java.util.Objects;

/** Permanent RPG progress. Points cannot be created independently of earned levels. */
public record PlayerProgress(PlayerClass playerClass, int level, long xp, int unspentPoints, int strength) {
    public static final int MAX_LEVEL = 100;
    public static final PlayerProgress DEFAULT = new PlayerProgress(PlayerClass.UNSELECTED, 1, 0, 0, 0);

    public PlayerProgress {
        Objects.requireNonNull(playerClass, "playerClass");
        if (level < 1 || level > MAX_LEVEL || xp < 0 || unspentPoints < 0 || strength < 0
                || (long) unspentPoints + strength != level - 1
                || (level == MAX_LEVEL && xp != 0)
                || (playerClass == PlayerClass.UNSELECTED && (level != 1 || xp != 0))) {
            throw new IllegalArgumentException("Invalid RPG progression state");
        }
    }

    public boolean selected() { return playerClass != PlayerClass.UNSELECTED; }
}
