package dev.fupez.rpgplayable.stats;

import dev.fupez.rpgplayable.player.PlayerClass;
import dev.fupez.rpgplayable.player.PlayerProgress;
import dev.fupez.rpgplayable.player.ProgressionRules;
import java.math.BigInteger;
import java.util.Objects;

/** Only conversion boundary from unlimited counters to finite native effects. Regen values are units/second. */
public record DerivedStats(double meleeBonus, double miningSpeedMultiplier, double maxHealthBonus,
                           double movementSpeedMultiplier, double attackSpeedBonus, double magicMultiplier,
                           double maxMana, double maxStamina, double manaRegen, double staminaRegen, double supportMultiplier) {
    public static DerivedStats calculate(PlayerProgress progress, ProgressionRules rules) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(rules, "rules");
        double strength = diminish(progress.strength(), 100, 200);
        double vitality = diminish(progress.vitality(), 100, 400);
        double agility = diminish(progress.agility(), 100, 200);
        double intelligence = diminish(progress.intelligence(), 100, 200);
        double spirit = diminish(progress.spirit(), 100, 200);
        boolean warrior = progress.playerClass() == PlayerClass.WARRIOR;
        boolean mage = progress.playerClass() == PlayerClass.MAGE;
        boolean rogue = progress.playerClass() == PlayerClass.ROGUE;
        var tuning = rules.stats();
        return new DerivedStats(strength * rules.strengthDamage(), 1 + strength * tuning.miningPerStrength(),
                vitality * tuning.healthPerVitality() + (warrior ? 4 : 0), 1 + agility * tuning.movementPerAgility() + (rogue ? 0.08 : 0),
                agility * tuning.attackSpeedPerAgility(), 1 + intelligence * tuning.magicPerIntelligence() + (mage ? 0.2 : 0),
                (mage ? 140 : warrior ? 60 : 80) + intelligence * tuning.manaPerIntelligence(),
                (warrior ? 140 : rogue ? 120 : 80) + vitality * tuning.staminaPerVitality(),
                4 + spirit * tuning.manaRegenPerSpirit(), 8 + spirit * tuning.staminaRegenPerSpirit(), 1 + spirit * tuning.supportPerSpirit());
    }
    /** Linear early investment, then continuous diminishing returns with a finite native-safe asymptote. */
    private static double diminish(BigInteger value, double knee, double ceiling) {
        double counter = value.doubleValue();
        if (counter <= knee) return counter;
        if (!Double.isFinite(counter)) return ceiling;
        double span = ceiling - knee;
        return knee + span * (1 - 1 / (1 + (counter - knee) / span));
    }
}
