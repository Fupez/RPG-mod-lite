package dev.fupez.rpgplayable.client.hud;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HudPresentationTest {
    @Test void damageFallsSmoothlyWhileTrailHoldsThenCatchesUp() {
        var meter = new HudMeter();
        meter.reset(1);
        meter.tick(.4);
        assertTrue(meter.fill(1) > .4 && meter.fill(1) < 1);
        assertEquals(1, meter.trail(1), 1e-9);
        for (int i = 0; i < 5; i++) meter.tick(.4);
        assertEquals(1, meter.trail(1), 1e-9);
        for (int i = 0; i < 50; i++) meter.tick(.4);
        assertEquals(.4, meter.fill(1), .001);
        assertEquals(.4, meter.trail(1), .001);
    }
    @Test void healingAndCorrectionNeverLeaveTrailBelowFill() {
        var meter = new HudMeter();
        meter.reset(.2);
        for (int i = 0; i < 12; i++) {
            meter.tick(.9);
            assertTrue(meter.trail(.5f) >= meter.fill(.5f));
            assertTrue(meter.fill(.5f) <= .9);
        }
        meter.reset(.1);
        assertEquals(.1, meter.fill(0), 1e-9);
        assertEquals(.1, meter.trail(1), 1e-9);
    }
    @Test void renderSamplingDoesNotAdvanceAnimationAndBoundsAreSafe() {
        var meter = new HudMeter();
        meter.reset(1); meter.tick(.5);
        double before = meter.fill(.25f);
        for (int i = 0; i < 100; i++) assertEquals(before, meter.fill(.25f));
        assertEquals(1, meter.fill(0), 1e-9);
        meter.reset(Double.NaN); assertEquals(0, meter.fill(1));
        meter.reset(2); assertEquals(1, meter.fill(1));
    }
    @Test void firstXpSyncAndResetDoNotCelebrate() {
        var xp = new HudXp();
        xp.accept(b(12), b(30), b(650));
        assertEquals(0, xp.gainTicks()); assertEquals(0, xp.levelTicks());
        assertEquals(30.0 / 650, xp.fill(1), 1e-8);
        xp.clear(); xp.accept(b(1), b(0), b(100));
        assertEquals(0, xp.gainTicks()); assertEquals(0, xp.levelTicks());
        assertEquals(0, xp.fill(1));
    }
    @Test void xpWrapShowsEarnedAmountAndNeverAnimatesBackwards() {
        var xp = new HudXp();
        xp.accept(b(1), b(90), b(100));
        xp.accept(b(2), b(15), b(150));
        assertEquals(b(25), xp.gain()); assertTrue(xp.levelTicks() > 0);
        xp.tick(); assertTrue(xp.fill(1) >= .9);
        for (int i = 0; i < 80; i++) xp.tick();
        assertEquals(.1, xp.fill(1), .001);
        assertEquals(0, xp.gainTicks()); assertEquals(0, xp.levelTicks());
    }
    @Test void hugeBulkLevelGainUsesAuthoritativeThresholdSeriesWithoutLooping() {
        var xp = new HudXp();
        var levels = BigInteger.TEN.pow(30);
        xp.accept(b(1), b(90), b(100));
        xp.accept(levels.add(BigInteger.ONE), b(5), b(100).add(levels.multiply(b(50))));
        var spent = levels.multiply(b(200).add(levels.subtract(BigInteger.ONE).multiply(b(50)))).divide(b(2));
        assertEquals(spent.subtract(b(85)), xp.gain());
    }
    @Test void xpCorrectionDoesNotInventGainOrLevelUp() {
        var xp = new HudXp();
        xp.accept(b(4), b(75), b(250));
        xp.accept(b(3), b(10), b(200));
        assertEquals(0, xp.gainTicks()); assertEquals(0, xp.levelTicks());
        assertEquals(.05, xp.fill(1), .0001);
    }
    @Test void slotReadyPulseOccursOnlyOnCooldownTransitionAndExpires() {
        var feedback = new HudAbilityFeedback();
        feedback.tick(0, false); assertEquals(0, feedback.ready(0));
        feedback.tick(2, false); feedback.tick(1, false); feedback.tick(0, false);
        assertTrue(feedback.ready(0) > 0);
        for (int i = 0; i < 16; i++) feedback.tick(0, false);
        assertEquals(0, feedback.ready(1));
        feedback.clear(); feedback.tick(0, false); assertEquals(0, feedback.ready(0));
    }
    @Test void heldKeyDoesNotContinuouslyRetriggerPressAndRejectIsLocalFeedbackOnly() {
        var feedback = new HudAbilityFeedback();
        feedback.tick(25, true); assertTrue(feedback.pressed(0) > 0);
        for (int i = 0; i < 10; i++) feedback.tick(25, true);
        assertEquals(0, feedback.pressed(0));
        feedback.reject(); assertTrue(feedback.failure(0) > 0);
        feedback.clear(); assertEquals(0, feedback.failure(0));
    }
    private static BigInteger b(long value) { return BigInteger.valueOf(value); }
}
