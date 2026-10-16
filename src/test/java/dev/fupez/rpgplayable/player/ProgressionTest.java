package dev.fupez.rpgplayable.player;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProgressionTest {
    private static final ProgressionRules RULES = ProgressionRules.DEFAULT;
    private static final PlayerProgress WARRIOR = Progression.selectWarrior(PlayerProgress.DEFAULT);

    @Test void noProgressBeforeClassSelectionAndSelectionCannotReplay() {
        assertEquals(PlayerProgress.DEFAULT, Progression.addXp(PlayerProgress.DEFAULT, 1000, RULES));
        assertEquals(PlayerClass.WARRIOR, WARRIOR.playerClass());
        var leveled = Progression.addXp(WARRIOR, 100, RULES);
        assertEquals(leveled, Progression.selectWarrior(leveled));
    }

    @Test void exactThresholdLevelsOnceAndAwardsOnePoint() {
        var p = Progression.addXp(WARRIOR, 100, RULES);
        assertEquals(2, p.level()); assertEquals(0, p.xp());
        assertEquals(1, p.unspentPoints()); assertEquals(0, p.strength());
    }

    @Test void multipleLevelsKeepRemainderAndExistingInvestment() {
        var p = Progression.allocateStrength(Progression.addXp(WARRIOR, 100, RULES));
        p = Progression.addXp(p, 360, RULES);
        assertEquals(4, p.level()); assertEquals(10, p.xp());
        assertEquals(2, p.unspentPoints()); assertEquals(1, p.strength());
    }

    @Test void enormousGrantsAreBoundedAndDoNotOverflow() {
        var p = Progression.addXp(WARRIOR, Long.MAX_VALUE, RULES);
        assertEquals(PlayerProgress.MAX_LEVEL, p.level()); assertEquals(0, p.xp());
        assertEquals(PlayerProgress.MAX_LEVEL - 1, p.unspentPoints());
        assertEquals(p, Progression.addXp(p, Long.MAX_VALUE, RULES));
    }

    @Test void zeroIsNoOpAndNegativeAwardsAlwaysReject() {
        assertEquals(WARRIOR, Progression.addXp(WARRIOR, 0, RULES));
        assertThrows(IllegalArgumentException.class, () -> Progression.addXp(WARRIOR, -1, RULES));
        assertThrows(IllegalArgumentException.class, () -> Progression.addXp(PlayerProgress.DEFAULT, -1, RULES));
    }

    @Test void allocationSpendsOnlyAvailablePoints() {
        assertEquals(WARRIOR, Progression.allocateStrength(WARRIOR));
        var p = Progression.allocateStrength(Progression.addXp(WARRIOR, 100, RULES));
        assertEquals(0, p.unspentPoints()); assertEquals(1, p.strength());
        assertEquals(p, Progression.allocateStrength(p));
    }

    @Test void changedCurveNormalizesSavedXpWithoutArithmeticOverflow() {
        var p = new PlayerProgress(PlayerClass.WARRIOR, 1, Long.MAX_VALUE, 0, 0);
        assertEquals(PlayerProgress.MAX_LEVEL, Progression.addXp(p, 0, RULES).level());
    }

    @Test void configurableCurveAndDamageUseServerTuning() {
        var rules = new ProgressionRules(10, 5, 2, 0.25);
        var p = Progression.allocateStrength(Progression.addXp(WARRIOR, 25, rules));
        assertEquals(3, p.level()); assertEquals(0, p.xp());
        assertEquals(20, rules.xpToNextLevel(3));
        assertEquals(0.25, rules.strengthBonus(p));
    }

    @Test void invalidOrForgedStateCannotBeConstructed() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerProgress(PlayerClass.UNSELECTED, 2, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new PlayerProgress(PlayerClass.WARRIOR, 1, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PlayerProgress(PlayerClass.WARRIOR, 2, 0, 100, 0));
        assertThrows(IllegalArgumentException.class, () -> new PlayerProgress(PlayerClass.WARRIOR, 101, 0, 100, 0));
        assertThrows(IllegalArgumentException.class, () -> new PlayerProgress(PlayerClass.WARRIOR, 100, 1, 99, 0));
    }

    @Test void invalidRulesRejectNonfiniteAndUnsafeNumbers() {
        assertThrows(IllegalArgumentException.class, () -> new ProgressionRules(0, 50, 25, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new ProgressionRules(100, -1, 25, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new ProgressionRules(100, 50, 0, 0.5));
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -0.5})
            assertThrows(IllegalArgumentException.class, () -> new ProgressionRules(100, 50, 25, bad));
    }
}
