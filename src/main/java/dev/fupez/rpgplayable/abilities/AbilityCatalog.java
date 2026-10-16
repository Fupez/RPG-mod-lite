package dev.fupez.rpgplayable.abilities;

import dev.fupez.rpgplayable.player.PlayerClass;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Only nine definitions; no loaders, registries or speculative ability graph. */
public final class AbilityCatalog {
    private static final List<AbilityDefinition> DEFAULTS = List.of(
            ability(AbilityId.HEAVY_STRIKE, PlayerClass.WARRIOR, 0, ResourceType.STAMINA, 20, 60, 6, 8, AnimationStyle.MELEE),
            ability(AbilityId.CHARGE, PlayerClass.WARRIOR, 1, ResourceType.STAMINA, 25, 100, 2, 6, AnimationStyle.DASH),
            ability(AbilityId.GROUND_SLAM, PlayerClass.WARRIOR, 2, ResourceType.STAMINA, 35, 140, 10, 10, AnimationStyle.SLAM),
            ability(AbilityId.FIREBOLT, PlayerClass.MAGE, 0, ResourceType.MANA, 15, 30, 6, 4, AnimationStyle.CAST),
            ability(AbilityId.ARCANE_BURST, PlayerClass.MAGE, 1, ResourceType.MANA, 30, 100, 8, 6, AnimationStyle.CAST),
            ability(AbilityId.BLINK, PlayerClass.MAGE, 2, ResourceType.MANA, 20, 80, 2, 4, AnimationStyle.DASH),
            ability(AbilityId.DASH, PlayerClass.ROGUE, 0, ResourceType.STAMINA, 15, 50, 1, 4, AnimationStyle.DASH),
            ability(AbilityId.BACKSTAB, PlayerClass.ROGUE, 1, ResourceType.STAMINA, 25, 80, 4, 6, AnimationStyle.MELEE),
            ability(AbilityId.FAN_OF_KNIVES, PlayerClass.ROGUE, 2, ResourceType.STAMINA, 30, 100, 5, 5, AnimationStyle.MELEE));
    private static final Map<AbilityId, Double> DEFAULT_DAMAGE = Map.of(
            AbilityId.HEAVY_STRIKE, 8.0, AbilityId.CHARGE, 6.0, AbilityId.GROUND_SLAM, 7.0,
            AbilityId.FIREBOLT, 8.0, AbilityId.ARCANE_BURST, 6.0, AbilityId.BLINK, 0.0,
            AbilityId.DASH, 0.0, AbilityId.BACKSTAB, 7.0, AbilityId.FAN_OF_KNIVES, 5.0);
    private static List<AbilityDefinition> definitions = DEFAULTS;
    private static Map<AbilityId, Double> damages = DEFAULT_DAMAGE;
    private AbilityCatalog() { }
    private static AbilityDefinition ability(AbilityId id, PlayerClass type, int slot, ResourceType resource,
            double cost, int cooldown, int windup, int recovery, AnimationStyle animation) {
        return new AbilityDefinition(id, type, slot, resource, cost, cooldown, windup, recovery, animation);
    }
    public static List<AbilityDefinition> forClass(PlayerClass type) {
        return definitions.stream().filter(a -> a.playerClass() == type).toList();
    }
    public static AbilityDefinition get(PlayerClass type, int slot) {
        return forClass(type).stream().filter(a -> a.slot() == slot).findFirst().orElse(null);
    }
    public static double damage(AbilityId id) { return damages.get(id); }
    public static void reset() { definitions = DEFAULTS; damages = DEFAULT_DAMAGE; }
    public static void load(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        if (!Files.exists(path)) {
            var text = new StringBuilder("# Server ability tuning; restart after editing. Timings are ticks.\n");
            for (var a : DEFAULTS) text.append(a.id().id()).append(".cost=").append(a.cost()).append('\n')
                    .append(a.id().id()).append(".cooldown=").append(a.cooldownTicks()).append('\n')
                    .append(a.id().id()).append(".windup=").append(a.windupTicks()).append('\n')
                    .append(a.id().id()).append(".recovery=").append(a.recoveryTicks()).append('\n')
                    .append(a.id().id()).append(".damage=").append(DEFAULT_DAMAGE.get(a.id())).append('\n');
            try { Files.writeString(path, text, StandardOpenOption.CREATE_NEW); }
            catch (FileAlreadyExistsException ignored) { /* Preserve another writer's config. */ }
        }
        var p = new Properties();
        try (var reader = Files.newBufferedReader(path)) { p.load(reader); }
        var next = new ArrayList<AbilityDefinition>();
        var nextDamage = new EnumMap<AbilityId, Double>(AbilityId.class);
        for (var a : DEFAULTS) {
            String key = a.id().id();
            next.add(ability(a.id(), a.playerClass(), a.slot(), a.resource(),
                    Double.parseDouble(p.getProperty(key + ".cost", "" + a.cost())),
                    Integer.parseInt(p.getProperty(key + ".cooldown", "" + a.cooldownTicks())),
                    Integer.parseInt(p.getProperty(key + ".windup", "" + a.windupTicks())),
                    Integer.parseInt(p.getProperty(key + ".recovery", "" + a.recoveryTicks())), a.animation()));
            double damage = Double.parseDouble(p.getProperty(key + ".damage", "" + DEFAULT_DAMAGE.get(a.id())));
            if (!Double.isFinite(damage) || damage < 0 || damage > 1000) throw new IllegalArgumentException("Invalid " + key + " damage");
            nextDamage.put(a.id(), damage);
        }
        // Publish only after every field is valid.
        definitions = List.copyOf(next); damages = Map.copyOf(nextDamage);
    }
}
