package dev.fupez.rpgplayable.client.hud;

import dev.fupez.rpgplayable.client.*;
import net.fabricmc.fabric.api.client.rendering.v1.hud.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import java.util.Locale;

public final class ProgressHud {
    private ProgressHud() { }
    public static void register(KeyBinding characterKey) {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of("rpgplayable", "progress"), (context, ticks) -> {
            var client = MinecraftClient.getInstance(); var state = ClientProgress.state(); var snapshot = state.snapshot();
            if (client.player == null || client.options.hudHidden || snapshot == null || client.currentScreen != null) return;
            int x = 6, y = 6, width = Math.min(184, context.getScaledWindowWidth() - 12);
            var p = snapshot.progress();
            context.fill(x, y, x + width, y + (p.selected() ? 77 : 22), 0xcc101720);
            if (!p.selected()) {
                context.drawTextWithShadow(client.textRenderer, Text.translatable("hud.rpgplayable.choose", characterKey.getBoundKeyLocalizedText()), x + 4, y + 5, 0xffe7c67a);
                return;
            }
            String heading = Text.translatable("class.rpgplayable." + p.playerClass().id()).getString() + "  Lv " + ClientNumbers.compact(p.level());
            context.drawTextWithShadow(client.textRenderer, Text.literal(heading), x + 4, y + 4, 0xffe7c67a);
            double health = client.player.getHealth(), maximum = client.player.getMaxHealth();
            bar(context, x + 4, y + 17, width - 8, health / maximum, 0xffc84949);
            context.drawTextWithShadow(client.textRenderer, Text.literal(String.format(Locale.ROOT, "HP %.0f / %.0f", health, maximum)), x + 6, y + 18, 0xffffffff);
            var combat = ClientAbilities.state().snapshot();
            if (combat != null) {
                boolean mana = p.playerClass() == dev.fupez.rpgplayable.player.PlayerClass.MAGE;
                double value = mana ? combat.mana() : combat.stamina(), cap = mana ? combat.maxMana() : combat.maxStamina();
                bar(context, x + 4, y + 31, width - 8, cap > 0 ? value / cap : 0, mana ? 0xff3979cb : 0xff4e9d65);
                context.drawTextWithShadow(client.textRenderer, Text.literal(String.format(Locale.ROOT, "%s %.0f / %.0f", mana ? "MP" : "SP", value, cap)), x + 6, y + 32, 0xffffffff);
            }
            bar(context, x + 4, y + 45, width - 8, ClientNumbers.ratio(p.xp(), snapshot.nextLevelXp()), 0xffad893e);
            context.drawTextWithShadow(client.textRenderer, Text.literal("XP " + ClientNumbers.compact(p.xp()) + " / " + ClientNumbers.compact(snapshot.nextLevelXp())), x + 6, y + 46, 0xffffffff);
            Text characterHint = p.unspentPoints().signum() > 0
                    ? Text.translatable("hud.rpgplayable.spend_points", ClientNumbers.compact(p.unspentPoints()), characterKey.getBoundKeyLocalizedText())
                    : Text.translatable("hud.rpgplayable.character_hint", characterKey.getBoundKeyLocalizedText());
            context.drawTextWithShadow(client.textRenderer, client.textRenderer.trimToWidth(characterHint.getString(), width - 8),
                    x + 4, y + 63, p.unspentPoints().signum() > 0 ? 0xffffd36b : 0xffacbac8);
            if (state.levelUpTicks() > 0) context.drawCenteredTextWithShadow(client.textRenderer,
                    Text.translatable("hud.rpgplayable.level_up", ClientNumbers.compact(p.level())), context.getScaledWindowWidth() / 2, Math.max(y + 82, context.getScaledWindowHeight() / 3), 0xffffd36b);
        });
    }
    static void bar(net.minecraft.client.gui.DrawContext context, int x, int y, int width, double ratio, int color) {
        context.fill(x, y, x + width, y + 12, 0xff29323d);
        context.fill(x, y, x + (int)(width * Math.clamp(ratio, 0, 1)), y + 12, color);
    }
}
