package dev.fupez.rpgplayable.client.screen;

import dev.fupez.rpgplayable.abilities.AbilityCatalog;
import dev.fupez.rpgplayable.abilities.AbilityDefinition;
import dev.fupez.rpgplayable.abilities.ResourceType;
import dev.fupez.rpgplayable.client.ClientAbilities;
import dev.fupez.rpgplayable.client.ClientProgress;
import dev.fupez.rpgplayable.client.hud.AbilityHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/** Small, keyboard-accessible pages keep every ability readable at the minimum GUI size. */
public final class AbilityGuideScreen extends Screen {
    private final Screen parent;
    private final ButtonWidget[] tabs = new ButtonWidget[4];
    private int page, panelWidth, top;

    public AbilityGuideScreen(Screen parent) {
        super(Text.translatable("screen.rpgplayable.guide"));
        this.parent = parent;
        var snapshot = ClientProgress.state().snapshot();
        page = snapshot != null && snapshot.progress().selected() ? 0 : 3;
    }

    @Override protected void init() {
        panelWidth = Math.min(380, width - 12);
        top = Math.max(2, (height - 232) / 2);
        int left = (width - panelWidth) / 2;
        int tabWidth = (panelWidth - 35) / 4;
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            tabs[i] = addDrawableChild(ButtonWidget.builder(i == 3
                            ? Text.translatable("screen.rpgplayable.basics")
                            : Text.translatable("screen.rpgplayable.ability_slot", i + 1), button -> {
                        page = index;
                        refresh();
                        narrateScreenIfNarrationEnabled(true);
                    }).dimensions(left + 16 + i * (tabWidth + 1), top + 43, tabWidth, 20).build());
        }
        int footerWidth = (panelWidth - 36) / 2;
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.rpgplayable.keybinds"), button ->
                        client.setScreen(new KeybindsScreen(this, client.options)))
                .dimensions(left + 16, top + 208, footerWidth, 20)
                .tooltip(Tooltip.of(Text.translatable("screen.rpgplayable.keybinds.tooltip"))).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"), button -> close())
                .dimensions(left + 20 + footerWidth, top + 208, footerWidth, 20).build());
        refresh();
    }

    private List<AbilityDefinition> abilities() {
        var snapshot = ClientProgress.state().snapshot();
        return snapshot == null ? List.of() : AbilityCatalog.forClass(snapshot.progress().playerClass());
    }

    private Text boundKey(int slot) {
        if (client != null) for (var binding : client.options.allKeys)
            if (binding.getId().equals("key.rpgplayable.ability_" + (slot + 1))) return binding.getBoundKeyLocalizedText();
        return Text.translatable("key.keyboard.unknown");
    }

    private Text description(AbilityDefinition ability) {
        return Text.translatable("ability.rpgplayable." + ability.id().id() + ".description");
    }

    private Text costs(AbilityDefinition ability) {
        var snapshot = ClientAbilities.state().snapshot();
        return snapshot == null || snapshot.cooldownTotals()[ability.slot()] == 0 ? Text.translatable("screen.rpgplayable.details_waiting")
                : Text.translatable("screen.rpgplayable.ability_cost", number(snapshot.costs()[ability.slot()]),
                Text.translatable(ability.resource() == ResourceType.MANA ? "screen.rpgplayable.mana" : "screen.rpgplayable.stamina"));
    }

    private Text cooldown(AbilityDefinition ability) {
        var snapshot = ClientAbilities.state().snapshot();
        return snapshot == null || snapshot.cooldownTotals()[ability.slot()] == 0 ? Text.empty()
                : Text.translatable("screen.rpgplayable.ability_cooldown", seconds(snapshot.cooldownTotals()[ability.slot()]));
    }

    @Override public void tick() { refresh(); }

    private void refresh() {
        var abilities = abilities();
        if (page < 3 && page >= abilities.size()) page = 3;
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].active = i == 3 || i < abilities.size();
            Text tip = i == 3 ? Text.translatable("screen.rpgplayable.basics.tooltip")
                    : i < abilities.size() ? Text.translatable("screen.rpgplayable.ability_tooltip",
                    Text.translatable("ability.rpgplayable." + abilities.get(i).id().id()), boundKey(i), description(abilities.get(i)))
                    : Text.translatable("screen.rpgplayable.choose_first");
            tabs[i].setTooltip(Tooltip.of(tip));
            tabs[i].setMessage(i == 3 ? Text.translatable("screen.rpgplayable.basics")
                    : Text.translatable("screen.rpgplayable.ability_slot", i + 1));
        }
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int left = (width - panelWidth) / 2;
        context.fill(left, top, left + panelWidth, top + 232, 0xe0101720);
        super.render(context, mouseX, mouseY, delta);
        centered(context, title, top + 9, 0xffffffff);
        var progress = ClientProgress.state().snapshot();
        centered(context, Text.translatable(progress == null || !progress.progress().selected()
                ? "screen.rpgplayable.choose_first" : "class.rpgplayable." + progress.progress().playerClass().id()), top + 26, 0xffe7c67a);
        int tabWidth = (panelWidth - 35) / 4;
        context.fill(left + 16 + page * (tabWidth + 1), top + 64,
                left + 16 + page * (tabWidth + 1) + tabWidth, top + 66, 0xffffd36b);
        var abilities = abilities();
        if (page == 3 || page >= abilities.size()) {
            wrapped(context, basics(), left + 16, top + 76, panelWidth - 32, 0xffc7d6e6);
            return;
        }
        var ability = abilities.get(page);
        context.drawItem(AbilityHud.icon(ability.id()), left + 16, top + 73);
        draw(context, Text.translatable("ability.rpgplayable." + ability.id().id()), left + 38, top + 77, panelWidth - 54, 0xffffffff);
        draw(context, Text.translatable("screen.rpgplayable.ability_key", boundKey(page)), left + 16, top + 96, panelWidth - 32, 0xffffd36b);
        wrapped(context, description(ability), left + 16, top + 113, panelWidth - 32, 0xffc7d6e6);
        draw(context, costs(ability), left + 16, top + 168, panelWidth - 32, 0xffb3d8ed);
        draw(context, cooldown(ability), left + 16, top + 181, panelWidth - 32, 0xffb3d8ed);
        var combat = ClientAbilities.state().snapshot();
        if (combat != null && combat.activeSlot() == page)
            draw(context, Text.translatable("screen.rpgplayable.ability_timing", seconds(combat.windupTicks()), seconds(combat.recoveryTicks())),
                    left + 16, top + 194, panelWidth - 32, 0xffb3d8ed);
    }

    private Text basics() {
        var text = Text.empty();
        for (int i = 1; i <= 5; i++) {
            if (i > 1) text.append("\n");
            text.append(Text.translatable("screen.rpgplayable.basics." + i));
        }
        return text;
    }

    private void wrapped(DrawContext context, Text text, int x, int y, int maxWidth, int color) {
        for (var line : textRenderer.wrapLines(text, maxWidth)) {
            context.drawTextWithShadow(textRenderer, line, x, y, color);
            y += 10;
        }
    }

    private void draw(DrawContext context, Text text, int x, int y, int maxWidth, int color) {
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(text.getString(), maxWidth), x, y, color);
    }

    private void centered(DrawContext context, Text text, int y, int color) {
        context.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(text.getString(), panelWidth - 32), width / 2, y, color);
    }

    private static String number(double value) { return String.format(Locale.ROOT, value == Math.rint(value) ? "%.0f" : "%.1f", value); }
    private static String seconds(int ticks) { return String.format(Locale.ROOT, "%.2f", ticks / 20.0); }

    @Override protected void addScreenNarrations(NarrationMessageBuilder builder) {
        super.addScreenNarrations(builder);
        var abilities = abilities();
        builder.put(NarrationPart.USAGE, page == 3 || page >= abilities.size() ? basics()
                : Text.translatable("screen.rpgplayable.ability_narration",
                Text.translatable("ability.rpgplayable." + abilities.get(page).id().id()), boundKey(page),
                description(abilities.get(page)), costs(abilities.get(page)), cooldown(abilities.get(page))));
    }

    @Override public void close() { client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
