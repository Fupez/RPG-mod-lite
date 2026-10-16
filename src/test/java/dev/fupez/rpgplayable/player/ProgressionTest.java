package dev.fupez.rpgplayable.player;

import dev.fupez.rpgplayable.stats.Stat;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressionTest {
    private static final ProgressionRules RULES = ProgressionRules.DEFAULT;
    private static final PlayerProgress WARRIOR = Progression.selectWarrior(PlayerProgress.DEFAULT);
    private static BigInteger n(long value) { return BigInteger.valueOf(value); }
    @Test void allThreeSelectionsPersistAndCannotBeReplayed() {
        assertEquals(PlayerProgress.DEFAULT, Progression.addXp(PlayerProgress.DEFAULT, 1000, RULES));
        for (var clazz : new PlayerClass[]{PlayerClass.WARRIOR, PlayerClass.MAGE, PlayerClass.ROGUE}) {
            var selected = Progression.select(PlayerProgress.DEFAULT, clazz);
            assertEquals(clazz, selected.playerClass());
            var earned = Progression.addXp(selected, 100, RULES);
            for (var choice : PlayerClass.values()) assertEquals(earned, Progression.select(earned, choice));
        }
    }
    @Test void exactThresholdAndMultipleLevelsPreserveRemaindersAndPoints() {
        var p = Progression.addXp(WARRIOR, 100, RULES);
        assertEquals(n(2), p.level()); assertEquals(n(0), p.xp()); assertEquals(n(1), p.unspentPoints());
        p = Progression.allocateStrength(p);
        p = Progression.addXp(p, 360, RULES);
        assertEquals(n(4), p.level()); assertEquals(n(10), p.xp());
        assertEquals(n(2), p.unspentPoints()); assertEquals(n(1), p.strength());
    }
    @Test void levelsContinueBeyondLegacyCapWithoutDiscardingXp() {
        var p = Progression.addXp(WARRIOR, 257500, RULES);
        assertEquals(n(101), p.level()); assertEquals(n(100), p.unspentPoints()); assertEquals(n(0), p.xp());
        assertEquals(n(100), Progression.addXp(p, 100, RULES).xp());
    }
    @Test void arbitraryPrecisionAwardsHaveNoIntegerWidthLimit() {
        var huge = BigInteger.TEN.pow(1000);
        var flat = new ProgressionRules(100, 0, 25, .5);
        var p = Progression.addXp(WARRIOR, huge, flat);
        assertEquals(huge.divide(n(100)).add(BigInteger.ONE), p.level());
        assertEquals(huge.divide(n(100)), p.unspentPoints()); assertEquals(BigInteger.ZERO, p.xp());
        var curved = Progression.addXp(WARRIOR, huge, RULES);
        assertTrue(curved.level().compareTo(n(Long.MAX_VALUE)) > 0);
        assertTrue(curved.xp().signum() >= 0 && curved.xp().compareTo(RULES.xpToNextLevel(curved.level())) < 0);
        assertEquals(curved, Progression.addXp(curved, BigInteger.ZERO, RULES));
    }
    @Test void bulkCurveMatchesIncrementalRewardsIncludingSteepEarlyThresholds() {
        for (var rules : new ProgressionRules[]{RULES, new ProgressionRules(1, 1000000, 1, .5), new ProgressionRules(10, 0, 2, .25)}) {
            var incremental = WARRIOR;
            for (int i = 0; i < 500; i++) incremental = Progression.addXp(incremental, 37, rules);
            assertEquals(incremental, Progression.addXp(WARRIOR, 18500, rules));
            assertEquals(WARRIOR, Progression.addXp(WARRIOR, 0, rules));
        }
    }
    @Test void fiveStatsSpendOnlyEarnedPointsAndCannotOverspend() {
        var p = Progression.addXp(WARRIOR, 1000, RULES);
        for (var stat : Stat.values()) p = Progression.allocate(p, stat);
        assertEquals(BigInteger.ZERO, p.unspentPoints());
        for (var stat : Stat.values()) { assertEquals(BigInteger.ONE, p.stat(stat)); assertEquals(p, Progression.allocate(p, stat)); }
        assertEquals(PlayerProgress.DEFAULT, Progression.allocate(PlayerProgress.DEFAULT, Stat.VITALITY));
    }
    @Test void changedCurveNormalizesSavedXpWithoutOverflow() {
        var saved = new PlayerProgress(PlayerClass.WARRIOR, 1, Long.MAX_VALUE, 0, 0);
        assertEquals(Progression.addXp(WARRIOR, Long.MAX_VALUE, RULES), Progression.addXp(saved, 0, RULES));
        assertThrows(IllegalArgumentException.class, () -> Progression.addXp(saved, -1, RULES));
    }
    @Test void configurableCurveAndDamageRemainEffective() {
        var rules = new ProgressionRules(10, 5, 2, .25);
        var p = Progression.allocateStrength(Progression.addXp(WARRIOR, 25, rules));
        assertEquals(n(3), p.level()); assertEquals(n(0), p.xp());
        assertEquals(20, rules.xpToNextLevel(3)); assertEquals(.25, rules.strengthBonus(p));
    }
    @Test void forgedStateAndInvalidRulesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerProgress(PlayerClass.UNSELECTED, 2, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new PlayerProgress(PlayerClass.MAGE, 1, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PlayerProgress(PlayerClass.ROGUE, 2, 0, 100, 0));
        assertThrows(IllegalArgumentException.class, () -> new ProgressionRules(0, 50, 25, .5));
        assertThrows(IllegalArgumentException.class, () -> new ProgressionRules(100, -1, 25, .5));
        assertThrows(IllegalArgumentException.class, () -> new ProgressionRules(100, 50, 0, .5));
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -.5})
            assertThrows(IllegalArgumentException.class, () -> new ProgressionRules(100, 50, 25, bad));
    }
}
