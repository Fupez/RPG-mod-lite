package dev.fupez.rpgplayable.client.hud;

import dev.fupez.rpgplayable.client.ClientProgress;
import dev.fupez.rpgplayable.client.screen.CharacterScreen;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class ProgressHud {
    private ProgressHud() { }
    public static void register(KeyBinding characterKey) {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of("rpgplayable", "progress"), (context, tickCounter) -> {
            var client = MinecraftClient.getInstance();
            var state = ClientProgress.state();
            var snapshot = state.snapshot();
            if (client.player == null || client.options.hudHidden || snapshot == null
                    || client.currentScreen instanceof CharacterScreen) return;
            int x = 8;
            // Keep above the vanilla hearts/armor/hunger rows even at the minimum GUI width.
            int y = Math.max(8, context.getScaledWindowHeight() - 104);
            int width = Math.min(174, context.getScaledWindowWidth() - 16);
            context.fill(x, y, x + width, y + 36, 0xb0101720);
            var p = snapshot.progress();
            if (!p.selected()) {
                context.drawTextWithShadow(client.textRenderer, Text.translatable("hud.rpgplayable.choose", characterKey.getBoundKeyLocalizedText()), x + 4, y + 6, 0xffe7c67a);
                return;
            }
            context.drawTextWithShadow(client.textRenderer, Text.translatable("hud.rpgplayable.level", p.level(), p.strength()), x + 4, y + 4, 0xffffffff);
            context.drawTextWithShadow(client.textRenderer, snapshot.nextLevelXp() == 0
                    ? Text.translatable("hud.rpgplayable.max")
                    : Text.translatable("hud.rpgplayable.xp", p.xp(), snapshot.nextLevelXp()), x + 4, y + 15, 0xffc7d6e6);
            int bar = width - 8;
            int filled = snapshot.nextLevelXp() == 0 ? bar : (int) (bar * (double) p.xp() / snapshot.nextLevelXp());
            context.fill(x + 4, y + 28, x + width - 4, y + 32, 0xff303b48);
            context.fill(x + 4, y + 28, x + 4 + filled, y + 32, 0xffd3a856);
            if (state.levelUpTicks() > 0)
                context.drawCenteredTextWithShadow(client.textRenderer, Text.translatable("hud.rpgplayable.level_up", p.level()),
                        context.getScaledWindowWidth() / 2, context.getScaledWindowHeight() / 3, 0xffffd36b);
        });
    }
}
