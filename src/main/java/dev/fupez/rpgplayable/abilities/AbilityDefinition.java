package dev.fupez.rpgplayable.abilities;

import dev.fupez.rpgplayable.player.PlayerClass;
import java.util.Objects;

public record AbilityDefinition(AbilityId id, PlayerClass playerClass, int slot, ResourceType resource,
        double cost, int cooldownTicks, int windupTicks, int recoveryTicks, AnimationStyle animation) {
    public AbilityDefinition {
        Objects.requireNonNull(id); Objects.requireNonNull(playerClass); Objects.requireNonNull(resource); Objects.requireNonNull(animation);
        if (playerClass == PlayerClass.UNSELECTED || slot < 0 || slot > 2 || !Double.isFinite(cost) || cost < 0 || cost > 1000
                || windupTicks < 1 || recoveryTicks < 1 || cooldownTicks < windupTicks + (long) recoveryTicks || cooldownTicks > 72000)
            throw new IllegalArgumentException("Invalid ability definition");
    }
}
