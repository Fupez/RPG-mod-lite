package dev.fupez.rpgplayable.client.hud;

import dev.fupez.rpgplayable.client.ClientNumbers;
import java.math.BigInteger;

/** XP presentation observes snapshots; it never awards or predicts progression. */
final class HudXp {
    private final HudMeter meter = new HudMeter();
    private BigInteger level, xp, next, gain = BigInteger.ZERO;
    private double target;
    private int wrapTicks, gainTicks, levelTicks;

    void accept(BigInteger newLevel, BigInteger newXp, BigInteger newNext) {
        target = ClientNumbers.ratio(newXp, newNext);
        if (level == null) meter.reset(target);
        else {
            var levels = newLevel.subtract(level);
            if (levels.signum() < 0 || levels.signum() == 0 && newXp.compareTo(xp) < 0) {
                meter.reset(target); wrapTicks = gainTicks = levelTicks = 0; gain = BigInteger.ZERO;
            } else {
                var earned = newXp.subtract(xp);
                if (levels.signum() > 0) {
                    // ProgressionRules uses an arithmetic threshold series. Infer it from both
                    // server thresholds, so custom tuning and enormous bulk gains stay exact.
                    var consumed = levels.multiply(next.add(newNext)).subtract(newNext.subtract(next)).divide(BigInteger.TWO);
                    earned = earned.add(consumed);
                    wrapTicks = 12; levelTicks = 80;
                }
                if (earned.signum() > 0) {
                    gain = gainTicks > 0 ? gain.add(earned) : earned;
                    gainTicks = 60;
                }
            }
        }
        level = newLevel; xp = newXp; next = newNext;
    }
    void tick() {
        if (gainTicks > 0) gainTicks--;
        if (levelTicks > 0) levelTicks--;
        if (wrapTicks > 0) {
            meter.tick(1);
            if (--wrapTicks == 0) meter.reset(0);
        } else meter.tick(target);
    }
    void clear() {
        level = xp = next = null; gain = BigInteger.ZERO;
        target = 0; wrapTicks = gainTicks = levelTicks = 0; meter.reset(0);
    }
    double fill(float delta) { return meter.fill(delta); }
    BigInteger gain() { return gain; }
    int gainTicks() { return gainTicks; }
    int levelTicks() { return levelTicks; }
}
