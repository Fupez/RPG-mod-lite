package dev.fupez.rpgplayable.client.hud;

import dev.fupez.rpgplayable.client.ClientAbilities;
import dev.fupez.rpgplayable.client.ClientNumbers;
import dev.fupez.rpgplayable.client.ClientProgress;
import dev.fupez.rpgplayable.client.ui.RpgUi;
import dev.fupez.rpgplayable.network.ProgressSnapshotPayload;
import dev.fupez.rpgplayable.player.PlayerClass;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

/** Compact, tick-owned read model. No resources or progression are predicted here. */
public final class ProgressHud {
    private static final HudMeter HEALTH = new HudMeter(), MANA = new HudMeter(), STAMINA = new HudMeter();
    private static final HudXp XP = new HudXp();
    private static Object actor, world, language;
    private static PlayerClass type = PlayerClass.UNSELECTED;
    private static ProgressSnapshotPayload observed;
    private static String heading = "", level = "", hp = "", mana = "", stamina = "", xp = "", hint = "", choose = "", gain = "", levelTitle = "", levelDetail = "";
    private static String lastKey = "";
    private static double lastHealth;
    private static int resourceWarning, hitTicks, clock;
    private static boolean initialized, lowHealth;
    private ProgressHud() { }

    public static void register(KeyBinding characterKey) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> update(client, characterKey));
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of("rpgplayable", "progress"), (context, ticks) -> {
            var client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden || client.currentScreen != null || observed == null) return;
            float delta = client.isPaused() ? 1 : ticks.getTickProgress(false);
            render(context, client, delta);
        });
    }
    /** A rejected server result can briefly mark the resource meter without changing its value. */
    static void warnResource() { resourceWarning = 16; }

    private static void update(MinecraftClient client, KeyBinding key) {
        if (actor != client.player || world != client.world) {
            actor = client.player; world = client.world; reset();
        }
        if (client.player == null || client.isPaused()) return;
        var snapshot = ClientProgress.state().snapshot();
        if (snapshot == null) {
            if (observed != null) reset();
            return;
        }
        var p = snapshot.progress();
        boolean changedClass = type != p.playerClass();
        if (changedClass) { reset(); type = p.playerClass(); }
        if (resourceWarning > 0) resourceWarning--;
        if (hitTicks > 0) hitTicks--;
        clock++;
        double currentHealth = client.player.getHealth(), maximum = client.player.getMaxHealth();
        lowHealth = maximum > 0 && currentHealth / maximum <= .25;
        if (!initialized) { HEALTH.reset(ratio(currentHealth, maximum)); lastHealth = currentHealth; }
        if (currentHealth < lastHealth) hitTicks = 9;
        lastHealth = currentHealth;
        HEALTH.tick(ratio(currentHealth, maximum));
        hp = "HP " + whole(currentHealth) + " / " + whole(maximum);
        var combat = ClientAbilities.state().snapshot();
        if (combat != null) {
            if (!initialized) { MANA.reset(ratio(combat.mana(), combat.maxMana())); STAMINA.reset(ratio(combat.stamina(), combat.maxStamina())); }
            MANA.tick(ratio(combat.mana(), combat.maxMana())); STAMINA.tick(ratio(combat.stamina(), combat.maxStamina()));
            mana = "MP " + whole(combat.mana()) + " / " + whole(combat.maxMana());
            stamina = "SP " + whole(combat.stamina()) + " / " + whole(combat.maxStamina());
        } else { mana = "MP —"; stamina = "SP —"; }
        String currentKey = key.getBoundKeyLocalizedText().getString();
        boolean textChanged = observed != snapshot || !lastKey.equals(currentKey) || language != Language.getInstance();
        if (textChanged) {
            XP.accept(p.level(), p.xp(), snapshot.nextLevelXp());
            heading = Text.translatable("class.rpgplayable." + p.playerClass().id()).getString();
            level = Text.translatable("hud.rpgplayable.level_short", ClientNumbers.compact(p.level())).getString();
            xp = "XP " + ClientNumbers.compact(p.xp()) + " / " + ClientNumbers.compact(snapshot.nextLevelXp());
            hint = p.unspentPoints().signum() > 0
                    ? Text.translatable("hud.rpgplayable.points_short", ClientNumbers.compact(p.unspentPoints()), currentKey).getString()
                    : Text.translatable("hud.rpgplayable.character_short", currentKey).getString();
            choose = Text.translatable("hud.rpgplayable.choose", currentKey).getString();
            levelTitle = Text.translatable("hud.rpgplayable.level_up_title", ClientNumbers.compact(p.level())).getString();
            levelDetail = Text.translatable("hud.rpgplayable.level_up_detail").getString();
            gain = Text.translatable("hud.rpgplayable.xp_gain", ClientNumbers.compact(XP.gain())).getString();
            lastKey = currentKey; language = Language.getInstance();
        }
        XP.tick(); observed = snapshot; initialized = true;
    }
    private static void render(DrawContext context, MinecraftClient client, float delta) {
        int x = 6, y = 6, width = Math.min(152, context.getScaledWindowWidth() - 12), accent = RpgUi.accent(type);
        var font = client.textRenderer;
        if (type == PlayerClass.UNSELECTED) {
            RpgUi.panel(context, x, y, width, 29, accent, 1);
            context.drawItem(RpgUi.classIcon(type), x + 5, y + 6);
            context.drawTextWithShadow(font, font.trimToWidth(choose, width - 30), x + 25, y + 10, RpgUi.GOLD);
            return;
        }
        RpgUi.panel(context, x, y, width, 80, accent, 1);
        context.drawItem(RpgUi.classIcon(type), x + 5, y + 4);
        context.drawTextWithShadow(font, heading, x + 25, y + 7, accent);
        context.drawTextWithShadow(font, level, x + width - 7 - font.getWidth(level), y + 7, RpgUi.GOLD);
        float low = lowHealth ? .35f + .25f * (float)Math.sin((clock + delta) * .24) : 0;
        float hit = Math.max(0, (hitTicks - delta) / 9f);
        float warning = Math.max(0, (resourceWarning - delta) / 16f);
        int barWidth = width - 12;
        RpgUi.bar(context, x + 6, y + 23, barWidth, 10, HEALTH.fill(delta), HEALTH.trail(delta), 0xffb84751, Math.max(low, hit));
        context.drawTextWithShadow(font, hp, x + 10, y + 24, RpgUi.TEXT);
        boolean mage = type == PlayerClass.MAGE;
        var main = mage ? MANA : STAMINA;
        var secondary = mage ? STAMINA : MANA;
        int resourceColor = mage ? 0xff5c94df : type == PlayerClass.ROGUE ? 0xff46a58a : 0xffc08a4f;
        double resourceFill = main.fill(delta);
        float fullGlow = resourceFill >= .999 ? .14f + .06f * (float)Math.sin((clock + delta) * .12) : 0;
        RpgUi.bar(context, x + 6, y + 35, barWidth, 10, resourceFill, resourceFill, resourceColor, Math.max(warning, fullGlow));
        context.drawTextWithShadow(font, mage ? mana : stamina, x + 10, y + 36, warning > 0 ? 0xffffb0a0 : RpgUi.TEXT);
        RpgUi.bar(context, x + 6, y + 48, 46, 5, secondary.fill(delta), secondary.fill(delta), mage ? 0xff698c78 : 0xff657ba1, 0);
        context.drawTextWithShadow(font, mage ? stamina : mana, x + 57, y + 46, RpgUi.MUTED);
        RpgUi.bar(context, x + 6, y + 57, barWidth, 10, XP.fill(delta), XP.fill(delta), 0xffb39551, XP.gainTicks() > 0 ? .35f : 0);
        context.drawTextWithShadow(font, xp, x + 10, y + 58, RpgUi.TEXT);
        context.drawTextWithShadow(font, font.trimToWidth(hint, barWidth), x + 6, y + 69,
                observed.progress().unspentPoints().signum() > 0 ? RpgUi.GOLD : RpgUi.MUTED);
        if (low > 0 || hit > 0) {
            int edge = RpgUi.alpha(0xffe86565, Math.max(low, hit) * .65f);
            context.fill(x, y + 23, x + 2, y + 33, edge);
        }
        if (XP.gainTicks() > 0) {
            float alpha = Math.min(1, XP.gainTicks() / 12f);
            RpgUi.panel(context, x, y + 85, Math.min(width, font.getWidth(gain) + 20), 20, RpgUi.GOLD, alpha);
            context.drawTextWithShadow(font, gain, x + 9, y + 91, RpgUi.alpha(RpgUi.GOLD, alpha));
        }
        if (XP.levelTicks() > 0) {
            float alpha = Math.min(1, XP.levelTicks() / 14f);
            int noticeWidth = Math.min(148, context.getScaledWindowWidth() - width - 24);
            int right = context.getScaledWindowWidth() - noticeWidth - 6;
            if (noticeWidth >= 100) {
                RpgUi.panel(context, right, 12, noticeWidth, 34, RpgUi.GOLD, alpha);
                context.drawTextWithShadow(font, font.trimToWidth(levelTitle, noticeWidth - 14), right + 7, 19, RpgUi.alpha(RpgUi.GOLD, alpha));
                context.drawTextWithShadow(font, font.trimToWidth(levelDetail, noticeWidth - 14), right + 7, 31, RpgUi.alpha(RpgUi.TEXT, alpha));
            }
        }
    }
    private static void reset() {
        HEALTH.reset(0); MANA.reset(0); STAMINA.reset(0); XP.clear();
        observed = null; type = PlayerClass.UNSELECTED; initialized = lowHealth = false;
        resourceWarning = hitTicks = clock = 0; lastKey = "";
    }
    private static String whole(double value) { return Long.toString(Math.round(value)); }
    private static double ratio(double value, double maximum) { return maximum > 0 ? value / maximum : 0; }
}
