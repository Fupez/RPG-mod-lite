package dev.fupez.rpgplayable.stats;

import dev.fupez.rpgplayable.player.*;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DerivedStatsTest {
    @Test void everyStatChangesItsRequiredGameplayEffects() {
        var base = DerivedStats.calculate(new PlayerProgress(PlayerClass.WARRIOR, 1, 0, 0, 0), ProgressionRules.DEFAULT);
        var invested = DerivedStats.calculate(new PlayerProgress(PlayerClass.WARRIOR, 6, 0, 0, 1, 1, 1, 1, 1), ProgressionRules.DEFAULT);
        assertTrue(invested.meleeBonus() > base.meleeBonus() && invested.miningSpeedMultiplier() > base.miningSpeedMultiplier());
        assertTrue(invested.maxHealthBonus() > base.maxHealthBonus());
        assertTrue(invested.movementSpeedMultiplier() > base.movementSpeedMultiplier() && invested.attackSpeedBonus() > base.attackSpeedBonus());
        assertTrue(invested.magicMultiplier() > base.magicMultiplier() && invested.maxMana() > base.maxMana());
        assertTrue(invested.manaRegen() > base.manaRegen() && invested.staminaRegen() > base.staminaRegen() && invested.supportMultiplier() > base.supportMultiplier());
    }
    @Test void enormousInvestmentsRemainNativeSafeWhileCountersStayUnlimited() {
        var huge = BigInteger.TEN.pow(1000);
        var p = new PlayerProgress(PlayerClass.ROGUE, huge.multiply(BigInteger.valueOf(5)).add(BigInteger.ONE), BigInteger.ZERO,
                BigInteger.ZERO, huge, huge, huge, huge, huge);
        var d = DerivedStats.calculate(p, ProgressionRules.DEFAULT);
        assertTrue(Double.isFinite(d.meleeBonus()) && d.meleeBonus() <= 2000);
        assertTrue(d.maxHealthBonus() < 1024 && d.movementSpeedMultiplier() < 2);
        assertTrue(Double.isFinite(d.maxMana()) && Double.isFinite(d.supportMultiplier()));
    }
    @Test void baseClassesHaveDistinctNativeAndResourceBenefits() {
        var w = DerivedStats.calculate(new PlayerProgress(PlayerClass.WARRIOR, 1, 0, 0, 0), ProgressionRules.DEFAULT);
        var m = DerivedStats.calculate(new PlayerProgress(PlayerClass.MAGE, 1, 0, 0, 0), ProgressionRules.DEFAULT);
        var r = DerivedStats.calculate(new PlayerProgress(PlayerClass.ROGUE, 1, 0, 0, 0), ProgressionRules.DEFAULT);
        assertTrue(w.maxHealthBonus() > m.maxHealthBonus() && w.maxStamina() > m.maxStamina());
        assertTrue(m.maxMana() > r.maxMana() && m.magicMultiplier() > r.magicMultiplier());
        assertTrue(r.movementSpeedMultiplier() > w.movementSpeedMultiplier());
    }
}
