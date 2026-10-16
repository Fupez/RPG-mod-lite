package dev.fupez.rpgplayable.ability;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CleaveTest {
    @Test void impactOccursOnceAtWindupBoundaryAndRecoveryPrecedesReadiness() {
        var cast = new CleaveCast(100, CleaveRules.DEFAULT);
        assertEquals(CleaveCast.Phase.WINDUP, cast.phase(105));
        assertTrue(cast.impactsAt(106));
        assertFalse(cast.impactsAt(107));
        assertEquals(CleaveCast.Phase.RECOVERY, cast.phase(106));
        assertEquals(CleaveCast.Phase.COOLDOWN, cast.phase(114));
        assertEquals(86, cast.remaining(114));
        assertEquals(CleaveCast.Phase.READY, cast.phase(200));
        assertEquals(0, cast.remaining(300));
    }
    @Test void frontalArcRejectsRearSideRangeAndVerticalMisses() {
        var rules = CleaveRules.DEFAULT;
        assertTrue(rules.inArc(0, 0, 2, 0));
        assertTrue(rules.inArc(1, 0, 2, 0));
        assertFalse(rules.inArc(0, 0, -2, 0));
        assertFalse(rules.inArc(2, 0, 0, 0));
        assertFalse(rules.inArc(0, 0, 3.01, 0));
        assertFalse(rules.inArc(0, 3, 1, 0));
        assertTrue(rules.inArc(-2, 0, 0, 90));
        assertFalse(rules.inArc(Double.NaN, 0, 1, 0));
    }
    @Test void invalidTuningCannotBreakTimingOrDamage() {
        assertThrows(IllegalArgumentException.class, () -> new CleaveRules(0, 8, 100, 3, 6));
        assertThrows(IllegalArgumentException.class, () -> new CleaveRules(6, 8, 10, 3, 6));
        assertThrows(IllegalArgumentException.class, () -> new CleaveRules(6, 8, 100, Double.NaN, 6));
        assertThrows(IllegalArgumentException.class, () -> new CleaveRules(6, 8, 100, 3, Double.POSITIVE_INFINITY));
    }
}
