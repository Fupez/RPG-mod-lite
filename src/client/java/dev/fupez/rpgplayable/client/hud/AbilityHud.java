package dev.fupez.rpgplayable.client.hud;

import dev.fupez.rpgplayable.client.*;
import dev.fupez.rpgplayable.abilities.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.Locale;

public final class AbilityHud {
    private AbilityHud() { }
    public static ItemStack icon(AbilityId id) {
        return new ItemStack(switch (id) {
            case HEAVY_STRIKE -> Items.IRON_AXE; case CHARGE -> Items.SHIELD; case GROUND_SLAM -> Items.MACE;
            case FIREBOLT -> Items.FIRE_CHARGE; case ARCANE_BURST -> Items.AMETHYST_SHARD; case BLINK -> Items.ENDER_PEARL;
            case DASH -> Items.FEATHER; case BACKSTAB -> Items.IRON_SWORD; case FAN_OF_KNIVES -> Items.ARROW;
        });
    }
    public static void register(KeyBinding[] keys) {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of("rpgplayable", "abilities"), (context, ticks) -> {
            var client = MinecraftClient.getInstance(); var progress = ClientProgress.state().snapshot();
            if (client.player == null || client.options.hudHidden || client.currentScreen != null || progress == null || !progress.progress().selected()) return;
            var abilities = AbilityCatalog.forClass(progress.progress().playerClass()); var state = ClientAbilities.state(); var snapshot = state.snapshot();
            int box = 88, gap = 4, x = (context.getScaledWindowWidth() - (box * 3 + gap * 2)) / 2, y = context.getScaledWindowHeight() - 116;
            for (int i = 0; i < abilities.size(); i++) {
                var ability = abilities.get(i); int left = x + i * (box + gap);
                String phase = state.phase(i);
                boolean active = phase.equals("windup") || phase.equals("recovery");
                context.fill(left, y, left + box, y + 42, active ? 0xe04e3f25 : 0xd0101720);
                context.drawItem(icon(ability.id()), left + 3, y + 3);
                if (snapshot != null && state.cooldown(i) > 0) {
                    int total = Math.max(1, snapshot.cooldownTotals()[i]);
                    int covered = (int)Math.ceil(16.0 * state.cooldown(i) / total);
                    context.fill(left + 3, y + 19 - covered, left + 19, y + 19, 0xbd000000);
                }
                context.drawTextWithShadow(client.textRenderer, Text.literal(client.textRenderer.trimToWidth(keys[i].getBoundKeyLocalizedText().getString(), 43)), left + 23, y + 4, 0xffffd36b);
                String status = snapshot == null ? "…" : phase.equals("cooldown")
                        ? String.format(Locale.ROOT, "%.1fs", state.cooldown(i) / 20.0)
                        : Text.translatable("ability_state.rpgplayable." + phase).getString();
                context.drawTextWithShadow(client.textRenderer, Text.literal(status), left + 23, y + 14, 0xffc7d6e6);
                String name = Text.translatable("ability.rpgplayable." + ability.id().id()).getString();
                context.drawTextWithShadow(client.textRenderer, Text.literal(client.textRenderer.trimToWidth(name, box - 6)), left + 3, y + 24, 0xffffffff);
                context.drawTextWithShadow(client.textRenderer, Text.literal(String.format(Locale.ROOT, "%.0f %s", (snapshot == null ? ability.cost() : snapshot.costs()[i]), ability.resource() == ResourceType.MANA ? "MP" : "SP")), left + 3, y + 33, 0xffa9b9c9);
            }
            if (!state.feedback().isEmpty()) context.drawCenteredTextWithShadow(client.textRenderer,
                    Text.translatable("combat.rpgplayable." + state.feedback()), context.getScaledWindowWidth() / 2, y - 12, 0xffffd36b);
        });
    }
}
