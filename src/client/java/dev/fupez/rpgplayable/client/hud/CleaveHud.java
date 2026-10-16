package dev.fupez.rpgplayable.client.hud;

import dev.fupez.rpgplayable.client.*;
import dev.fupez.rpgplayable.player.PlayerClass;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.Locale;

public final class CleaveHud {
    private static final ItemStack ICON = new ItemStack(Items.IRON_SWORD);
    private CleaveHud() { }
    public static void register(KeyBinding key) {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of("rpgplayable", "cleave"), (context, ticks) -> {
            var client = MinecraftClient.getInstance();
            var progress = ClientProgress.state().snapshot();
            if (client.player == null || client.options.hudHidden || client.currentScreen != null || progress == null
                    || progress.progress().playerClass() != PlayerClass.WARRIOR) return;
            var state = ClientCleave.state();
            // Outside the hotbar, above its right corner; fits a 320x240 scaled GUI.
            int x = context.getScaledWindowWidth() - 64, y = context.getScaledWindowHeight() - 70;
            context.fill(x, y, x + 58, y + 34, 0xd0101720);
            context.drawItem(ICON, x + 3, y + 3);
            if (state.remaining() > 0) {
                int height = (int) Math.ceil(16.0 * state.remaining() / state.total());
                context.fill(x + 3, y + 19 - height, x + 19, y + 19, 0xb0000000);
            }
            context.drawTextWithShadow(client.textRenderer, Text.literal(client.textRenderer.trimToWidth(key.getBoundKeyLocalizedText().getString(), 30)), x + 23, y + 6, 0xffe7c67a);
            context.drawTextWithShadow(client.textRenderer, Text.translatable("ability.rpgplayable.cleave"), x + 3, y + 23, 0xffffffff);
            String label = !state.known() ? "sync" : state.pending() ? "waiting" : state.phase();
            var text = label.equals("cooldown") ? Text.literal(String.format(Locale.ROOT, "%.1fs", state.remaining() / 20.0))
                    : Text.translatable("cleave.rpgplayable." + label);
            context.drawCenteredTextWithShadow(client.textRenderer, text, x + 29, y - 11,
                    label.equals("windup") ? 0xffffd36b : 0xffe7c67a);
            if (!state.feedback().isEmpty()) context.drawCenteredTextWithShadow(client.textRenderer,
                    Text.translatable("cleave.rpgplayable." + state.feedback()), context.getScaledWindowWidth() / 2,
                    context.getScaledWindowHeight() / 2 + 24, 0xffe7c67a);
        });
    }
}
