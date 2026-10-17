package dev.fupez.rpgplayable.client.hud;

import dev.fupez.rpgplayable.abilities.AbilityCatalog;
import dev.fupez.rpgplayable.abilities.AbilityDefinition;
import dev.fupez.rpgplayable.abilities.AbilityId;
import dev.fupez.rpgplayable.abilities.ResourceType;
import dev.fupez.rpgplayable.client.ClientAbilities;
import dev.fupez.rpgplayable.client.ClientProgress;
import dev.fupez.rpgplayable.client.ui.RpgUi;
import dev.fupez.rpgplayable.network.CombatSnapshotPayload;
import dev.fupez.rpgplayable.player.PlayerClass;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

/** Three stable slots; tick updates cache authoritative readouts and bounded visual feedback. */
public final class AbilityHud {
    private static final ItemStack[] ICONS = {
            new ItemStack(Items.IRON_AXE), new ItemStack(Items.SHIELD), new ItemStack(Items.MACE),
            new ItemStack(Items.FIRE_CHARGE), new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.ENDER_PEARL),
            new ItemStack(Items.FEATHER), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.ARROW)};
    private static final HudAbilityFeedback[] FEEDBACK = {new HudAbilityFeedback(), new HudAbilityFeedback(), new HudAbilityFeedback()};
    private static final String[] NAMES = new String[3], KEYS = new String[3], COSTS = new String[3], STATUS = new String[3];
    private static final String[] PHASES = {"ready", "ready", "ready"};
    private static final int[] COOLDOWNS = new int[3], TENTHS = {-1, -1, -1};
    private static final boolean[] KEY_DOWN = new boolean[3], DISABLED = new boolean[3], NO_RESOURCE = new boolean[3];
    private static int[] totals = new int[3];
    private static double[] costs = new double[3];
    private static Object actor, world, language;
    private static PlayerClass type = PlayerClass.UNSELECTED;
    private static List<AbilityDefinition> definitions = List.of();
    private static CombatSnapshotPayload observed;
    private static int lastPressed, feedbackTicks, soundDelay;
    private static String lastFeedback = "", feedbackText = "", readyText = "", windupText = "", recoveryText = "", unavailableText = "";
    private AbilityHud() { }

    /** Shared by the guide; callers must treat this cached stack as read-only. */
    public static ItemStack icon(AbilityId id) { return ICONS[id.ordinal()]; }

    public static void register(KeyBinding[] keys) {
        ClientTickEvents.END_CLIENT_TICK.register(client -> update(client, keys));
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of("rpgplayable", "abilities"), (context, ticks) -> {
            var client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden || client.currentScreen != null || definitions.size() != 3) return;
            render(context, client, client.isPaused() ? 0 : ticks.getTickProgress(false));
        });
    }
    private static void update(MinecraftClient client, KeyBinding[] keys) {
        if (actor != client.player || world != client.world) {
            actor = client.player; world = client.world; reset();
        }
        if (client.player == null || client.isPaused()) return;
        var progress = ClientProgress.state().snapshot();
        if (progress == null || !progress.progress().selected()) {
            if (type != PlayerClass.UNSELECTED || observed != null) reset();
            return;
        }
        var newType = progress.progress().playerClass();
        boolean changedClass = type != newType;
        if (changedClass) {
            reset(); type = newType;
            definitions = AbilityCatalog.forClass(type);
        }
        boolean translate = changedClass || language != Language.getInstance();
        if (translate) {
            readyText = Text.translatable("ability_state.rpgplayable.ready").getString();
            windupText = Text.translatable("ability_state.rpgplayable.windup").getString();
            recoveryText = Text.translatable("ability_state.rpgplayable.recovery").getString();
            unavailableText = Text.translatable("hud.rpgplayable.disabled").getString();
            for (int i = 0; i < definitions.size(); i++) NAMES[i] = Text.translatable("ability.rpgplayable." + definitions.get(i).id().id()).getString();
            language = Language.getInstance();
        }
        if (feedbackTicks > 0) feedbackTicks--;
        if (soundDelay > 0) soundDelay--;
        var state = ClientAbilities.state();
        var snapshot = state.snapshot();
        boolean newSnapshot = observed != snapshot;
        boolean confirmedReady = false;
        if (newSnapshot && snapshot != null) {
            // Payload array accessors clone: copy once per server snapshot, never per draw.
            totals = snapshot.cooldownTotals(); costs = snapshot.costs();
            if (observed != null) {
                var before = observed.cooldowns(); var after = snapshot.cooldowns();
                for (int i = 0; i < 3; i++) confirmedReady |= before[i] > 0 && after[i] == 0;
            }
        }
        boolean actionActive = false;
        for (int i = 0; i < 3; i++) {
            PHASES[i] = state.phase(i);
            if (PHASES[i].equals("windup") || PHASES[i].equals("recovery")) actionActive = true;
        }
        for (int i = 0; i < definitions.size(); i++) {
            var definition = definitions.get(i);
            boolean pressed = keys[i].isPressed() && client.currentScreen == null;
            if (pressed && !KEY_DOWN[i]) lastPressed = i;
            KEY_DOWN[i] = pressed;
            COOLDOWNS[i] = state.cooldown(i);
            FEEDBACK[i].tick(COOLDOWNS[i], pressed);
            KEYS[i] = client.textRenderer.trimToWidth(keys[i].getBoundKeyLocalizedText().getString(), 17);
            double available = snapshot == null ? 0 : definition.resource() == ResourceType.MANA ? snapshot.mana() : snapshot.stamina();
            NO_RESOURCE[i] = snapshot != null && available + 1e-9 < costs[i];
            DISABLED[i] = snapshot == null || !client.player.isAlive() || client.player.isSpectator() || client.player.isCreative()
                    || NO_RESOURCE[i] || actionActive && PHASES[i].equals("ready");
            if (newSnapshot || translate) COSTS[i] = snapshot == null ? "—" : number(costs[i]) + (definition.resource() == ResourceType.MANA ? " MP" : " SP");
            int tenths = (COOLDOWNS[i] + 1) / 2;
            if (tenths != TENTHS[i] || translate || !PHASES[i].equals("cooldown")) {
                STATUS[i] = PHASES[i].equals("windup") ? windupText : PHASES[i].equals("recovery") ? recoveryText
                        : COOLDOWNS[i] > 0 ? (tenths / 10) + "." + (tenths % 10) + "s"
                        : DISABLED[i] ? unavailableText : readyText;
                TENTHS[i] = tenths;
            }
        }
        String feedback = state.feedback();
        boolean rejected = newSnapshot && snapshot != null && snapshot.result() > CombatSnapshotPayload.ACCEPTED;
        if (rejected || !feedback.isEmpty() && !feedback.equals(lastFeedback)) {
            int slot = state.feedbackSlot() >= 0 ? state.feedbackSlot() : lastPressed;
            FEEDBACK[slot].reject(); feedbackTicks = 45;
            feedbackText = Text.translatable("hud.rpgplayable.fail." + feedback).getString();
            if (feedback.equals("no_resource")) ProgressHud.warnResource();
            if (soundDelay == 0 && client.currentScreen == null && !client.options.hudHidden) {
                client.player.playSound(SoundEvents.BLOCK_COPPER_HIT, .13f, .75f);
                soundDelay = 12;
            }
        } else if (newSnapshot && snapshot != null && snapshot.result() == CombatSnapshotPayload.ACCEPTED) {
            int slot = snapshot.activeSlot() >= 0 ? snapshot.activeSlot() : lastPressed;
            FEEDBACK[slot].accept(); feedbackTicks = 0;
        }
        if (confirmedReady && soundDelay == 0 && client.currentScreen == null && !client.options.hudHidden) {
            client.player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, .10f, 1.65f);
            soundDelay = 6;
        }
        lastFeedback = feedback; observed = snapshot;
    }
    private static void render(DrawContext context, MinecraftClient client, float delta) {
        int gap = 4, width = Math.min(100, (context.getScaledWindowWidth() - 20) / 3), height = 48;
        int left = (context.getScaledWindowWidth() - (width * 3 + gap * 2)) / 2;
        int rows = Math.max(1, (int)Math.ceil((client.player.getMaxHealth() + client.player.getAbsorptionAmount()) / 20.0));
        int rowSpacing = Math.max(10 - (rows - 2), 3);
        int vanillaTop = context.getScaledWindowHeight() - 49 - (rows - 1) * rowSpacing;
        int y = Math.max(94, Math.min(context.getScaledWindowHeight() - 113, vanillaTop - height - 8));
        int accent = RpgUi.accent(type);
        var font = client.textRenderer;
        for (int i = 0; i < 3; i++) {
            int x = left + i * (width + gap);
            float failure = FEEDBACK[i].failure(delta), ready = FEEDBACK[i].ready(delta), pressed = FEEDBACK[i].pressed(delta), success = FEEDBACK[i].success(delta);
            int edge = failure > 0 ? 0xffe97871 : NO_RESOURCE[i] ? 0xff8f6268 : accent;
            RpgUi.panel(context, x, y, width, height, edge, 1);
            if (pressed > 0 || success > 0) context.fill(x + 2, y + 3, x + width - 2, y + height - 2, RpgUi.alpha(accent, Math.max(pressed * .2f, success * .17f)));
            double remaining = Math.max(0, COOLDOWNS[i] - delta);
            double covered = totals[i] > 0 ? Math.clamp(remaining / totals[i], 0, 1) : 0;
            if (covered > 0) {
                int veil = (int)Math.ceil((height - 4) * covered);
                context.fill(x + 2, y + height - 2 - veil, x + width - 2, y + height - 2, 0x70101520);
            }
            context.drawItem(icon(definitions.get(i).id()), x + 6, y + 5);
            if (covered > 0) context.fill(x + 6, y + 21 - (int)Math.ceil(16 * covered), x + 22, y + 21, 0x98090d16);
            if (DISABLED[i]) context.fill(x + 6, y + 5, x + 22, y + 21, 0x660d121a);
            context.drawTextWithShadow(font, STATUS[i], x + 28, y + 9, failure > 0 ? 0xffffaaa1 : COOLDOWNS[i] > 0 ? RpgUi.TEXT : DISABLED[i] ? RpgUi.MUTED : accent);
            context.drawTextWithShadow(font, NAMES[i], x + 6, y + 25, DISABLED[i] ? 0xffb6bfcb : RpgUi.TEXT);
            context.drawTextWithShadow(font, COSTS[i], x + 6, y + 36, NO_RESOURCE[i] || failure > 0 ? 0xffe49a91 : RpgUi.MUTED);
            int badge = x + width - 23;
            context.fill(badge, y + 34, badge + 18, y + 45, pressed > 0 ? RpgUi.alpha(accent, .65f) : 0xff101721);
            context.fill(badge, y + 34, badge + 18, y + 35, pressed > 0 ? RpgUi.GOLD : 0xff566171);
            context.drawCenteredTextWithShadow(font, KEYS[i], badge + 9, y + 36, RpgUi.GOLD);
            int line = (int)((width - 4) * (1 - covered));
            context.fill(x + 2, y + height - 2, x + 2 + line, y + height - 1, RpgUi.alpha(accent, covered > 0 ? .8f : .4f));
            if (ready > 0 || failure > 0) {
                int flash = RpgUi.alpha(failure > 0 ? 0xffef8c7c : RpgUi.GOLD, Math.max(ready, failure) * .85f);
                context.fill(x + 1, y + 1, x + width - 1, y + 2, flash);
                context.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, flash);
            }
        }
        if (feedbackTicks > 0 && !feedbackText.isEmpty()) {
            float alpha = Math.min(1, feedbackTicks / 10f);
            int noticeWidth = Math.min(148, font.getWidth(feedbackText) + 16), x = context.getScaledWindowWidth() - noticeWidth - 6;
            RpgUi.panel(context, x, 54, noticeWidth, 22, 0xffcb8578, alpha);
            context.drawTextWithShadow(font, font.trimToWidth(feedbackText, noticeWidth - 12), x + 6, 61, RpgUi.alpha(0xffedb0a1, alpha));
        }
    }
    private static void reset() {
        type = PlayerClass.UNSELECTED; definitions = List.of(); observed = null; language = null;
        lastPressed = feedbackTicks = soundDelay = 0; lastFeedback = feedbackText = "";
        totals = new int[3]; costs = new double[3];
        for (int i = 0; i < 3; i++) { FEEDBACK[i].clear(); COOLDOWNS[i] = 0; KEY_DOWN[i] = false; TENTHS[i] = -1; }
    }
    private static String number(double value) {
        long tenths = Math.round(value * 10);
        return tenths % 10 == 0 ? Long.toString(tenths / 10) : (tenths / 10) + "." + (tenths % 10);
    }
}
