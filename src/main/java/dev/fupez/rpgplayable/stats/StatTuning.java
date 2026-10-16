package dev.fupez.rpgplayable.stats;

import java.util.Properties;

/** Server coefficients; bounds keep even unlimited investments inside native attribute/payload limits. */
public record StatTuning(double miningPerStrength, double healthPerVitality, double movementPerAgility,
        double attackSpeedPerAgility, double magicPerIntelligence, double manaPerIntelligence,
        double staminaPerVitality, double manaRegenPerSpirit, double staminaRegenPerSpirit, double supportPerSpirit) {
    public static final StatTuning DEFAULT = new StatTuning(.005, 2, .002, .01, .01, 2, .2, .1, .15, .005);
    public StatTuning {
        double[] values = {miningPerStrength, healthPerVitality, movementPerAgility, attackSpeedPerAgility,
                magicPerIntelligence, manaPerIntelligence, staminaPerVitality, manaRegenPerSpirit, staminaRegenPerSpirit, supportPerSpirit};
        double[] limits = {.05, 2.5, .01, .05, .05, 20, 10, 5, 5, .05};
        for (int i = 0; i < values.length; i++) if (!Double.isFinite(values[i]) || values[i] < 0 || values[i] > limits[i])
            throw new IllegalArgumentException("Stat coefficient outside native-safe bounds");
    }
    public static StatTuning from(Properties p) {
        return new StatTuning(value(p, "miningPerStrength", .005), value(p, "healthPerVitality", 2),
                value(p, "movementPerAgility", .002), value(p, "attackSpeedPerAgility", .01),
                value(p, "magicPerIntelligence", .01), value(p, "manaPerIntelligence", 2),
                value(p, "staminaPerVitality", .2), value(p, "manaRegenPerSpirit", .1),
                value(p, "staminaRegenPerSpirit", .15), value(p, "supportPerSpirit", .005));
    }
    private static double value(Properties p, String key, double fallback) {
        return Double.parseDouble(p.getProperty(key, Double.toString(fallback)).trim());
    }
    public static String defaultsText() {
        return """
                # Coefficients apply to the native-safe diminished stat values.
                miningPerStrength=0.005
                healthPerVitality=2
                movementPerAgility=0.002
                attackSpeedPerAgility=0.01
                magicPerIntelligence=0.01
                manaPerIntelligence=2
                staminaPerVitality=0.2
                manaRegenPerSpirit=0.1
                staminaRegenPerSpirit=0.15
                supportPerSpirit=0.005
                """;
    }
}
