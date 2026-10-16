package dev.fupez.rpgplayable.abilities;

import java.util.Locale;

public enum AbilityId {
    HEAVY_STRIKE, CHARGE, GROUND_SLAM, FIREBOLT, ARCANE_BURST, BLINK, DASH, BACKSTAB, FAN_OF_KNIVES;
    public String id() { return name().toLowerCase(Locale.ROOT); }
}
