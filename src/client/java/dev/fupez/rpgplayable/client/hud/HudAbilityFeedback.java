package dev.fupez.rpgplayable.client.hud;

/** Short bounded visual cues, independent of server-owned cooldowns and resources. */
final class HudAbilityFeedback {
    private int lastCooldown, readyTicks, pressedTicks, failureTicks, successTicks;
    private boolean initialized, keyDown;

    void tick(int cooldown, boolean pressed) {
        if (readyTicks > 0) readyTicks--;
        if (pressedTicks > 0) pressedTicks--;
        if (failureTicks > 0) failureTicks--;
        if (successTicks > 0) successTicks--;
        if (initialized && lastCooldown > 0 && cooldown == 0) readyTicks = 14;
        if (pressed && !keyDown) pressedTicks = 5;
        initialized = true; lastCooldown = cooldown; keyDown = pressed;
    }
    void reject() { failureTicks = 14; successTicks = 0; }
    void accept() { successTicks = 10; }
    void clear() {
        lastCooldown = readyTicks = pressedTicks = failureTicks = successTicks = 0;
        initialized = keyDown = false;
    }
    float ready(float delta) { return fade(readyTicks, 14, delta); }
    float pressed(float delta) { return fade(pressedTicks, 5, delta); }
    float failure(float delta) { return fade(failureTicks, 14, delta); }
    float success(float delta) { return fade(successTicks, 10, delta); }
    private static float fade(int ticks, int total, float delta) { return Math.clamp((ticks - Math.clamp(delta, 0, 1)) / total, 0, 1); }
}
