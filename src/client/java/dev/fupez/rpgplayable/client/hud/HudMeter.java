package dev.fupez.rpgplayable.client.hud;

/** Tick-owned easing with a delayed damage trail. Sampling never changes state. */
final class HudMeter {
    private double previous, value, previousTrail, trail, target;
    private int hold;
    private boolean initialized;

    void reset(double ratio) {
        previous = value = previousTrail = trail = target = bound(ratio);
        hold = 0;
        initialized = true;
    }
    void tick(double ratio) {
        ratio = bound(ratio);
        if (!initialized) { reset(ratio); return; }
        previous = value; previousTrail = trail;
        if (ratio < target - .0001) hold = 8;
        target = ratio;
        value += (target - value) * .4;
        if (Math.abs(value - target) < .0001) value = target;
        if (value >= trail) { trail = value; hold = 0; }
        else if (hold > 0) hold--;
        else trail = Math.max(value, trail + (target - trail) * .16);
    }
    double fill(float delta) { return interpolate(previous, value, delta); }
    double trail(float delta) { return Math.max(fill(delta), interpolate(previousTrail, trail, delta)); }
    private static double interpolate(double from, double to, float delta) {
        return from + (to - from) * bound(delta);
    }
    private static double bound(double value) { return Double.isFinite(value) ? Math.clamp(value, 0, 1) : 0; }
}
