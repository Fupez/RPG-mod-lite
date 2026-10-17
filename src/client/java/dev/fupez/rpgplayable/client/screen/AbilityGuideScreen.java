package dev.fupez.rpgplayable.client.screen;

import dev.fupez.rpgplayable.abilities.AbilityCatalog;
import dev.fupez.rpgplayable.abilities.AbilityDefinition;
import dev.fupez.rpgplayable.abilities.ResourceType;
import dev.fupez.rpgplayable.client.ClientAbilities;
import dev.fupez.rpgplayable.client.ClientProgress;
import dev.fupez.rpgplayable.client.hud.AbilityHud;
import dev.fupez.rpgplayable.client.ui.RpgButton;
import dev.fupez.rpgplayable.client.ui.RpgUi;
import dev.fupez.rpgplayable.player.PlayerClass;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Short tab transitions, real bound keys and server tuning, with scrollable small-screen pages. */
public final class AbilityGuideScreen extends RpgMenuScreen {
    private static final Text SCROLL_HINT = Text.translatable("screen.rpgplayable.scroll_hint");
    private static final Text CAST_DETAILS = Text.translatable("screen.rpgplayable.ability_details");
    private final Screen parent;
    private final RpgButton[] tabs = new RpgButton[4];
    private final Text[] keys = new Text[3], names = new Text[3], descriptions = new Text[3];
    private final Text[] basics = new Text[5], basicHeadings = new Text[5];
    private final Text basicsNarration;
    private final ArrayList<Line> content = new ArrayList<>();
    private List<AbilityDefinition> abilities = List.of();
    private PlayerClass playerClass;
    private Object combatObserved;
    private Text cost = Text.empty(), cooldown = Text.empty(), timing = Text.empty(), subtitle = Text.empty();
    private int page, contentTop, contentBottom, contentHeight, scroll;
    private long changedPageAt;
    private boolean dirty = true, wide;

    private record Line(OrderedText text, int x, int y, int color) { }

    public AbilityGuideScreen(Screen parent) {
        super(Text.translatable("screen.rpgplayable.guide"));
        this.parent = parent;
        var snapshot = ClientProgress.state().snapshot();
        page = snapshot != null && snapshot.progress().selected() ? 0 : 3;
        var narration = Text.empty();
        for (int i = 0; i < basics.length; i++) {
            basics[i] = Text.translatable("screen.rpgplayable.basics." + (i + 1));
            basicHeadings[i] = Text.translatable("screen.rpgplayable.basics_heading." + (i + 1));
            if (i > 0) narration.append("\n");
            narration.append(basics[i]);
        }
        basicsNarration = narration;
    }

    @Override protected void init() {
        layout(590, 300);
        wide = panelWidth >= 500;
        contentTop = top + 77;
        contentBottom = top + panelHeight - 43;
        int tabWidth = (panelWidth - 34) / 4;
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            tabs[i] = addDrawableChild(new RpgButton(left + 14 + i * (tabWidth + 2), top + 45, tabWidth, 23,
                    i == 3 ? Text.translatable("screen.rpgplayable.basics") : Text.translatable("screen.rpgplayable.ability_slot", i + 1),
                    GOLD, button -> {
                        if (page != index) {
                            page = index;
                            scroll = 0;
                            dirty = true;
                            changedPageAt = System.nanoTime();
                        }
                        refresh();
                        narrateScreenIfNarrationEnabled(true);
                    }));
        }
        int footerWidth = (panelWidth - 34) / 2;
        addDrawableChild(new RpgButton(left + 14, top + panelHeight - 28, footerWidth, 20,
                Text.translatable("screen.rpgplayable.keybinds"), GOLD,
                button -> client.setScreen(new KeybindsScreen(this, client.options)))
                .tooltip(Text.translatable("screen.rpgplayable.keybinds.tooltip")));
        addDrawableChild(new RpgButton(left + 20 + footerWidth, top + panelHeight - 28, footerWidth, 20,
                Text.translatable("gui.back"), GOLD, button -> close()));
        playerClass = null;
        dirty = true;
        refresh();
    }

    @Override public void tick() { refresh(); }

    private void refresh() {
        var progress = ClientProgress.state().snapshot();
        var type = progress == null ? PlayerClass.UNSELECTED : progress.progress().playerClass();
        boolean classChanged = type != playerClass;
        if (classChanged) {
            playerClass = type;
            subtitle = Text.translatable(type == PlayerClass.UNSELECTED ? "screen.rpgplayable.choose_first" : "class.rpgplayable." + type.id());
            abilities = AbilityCatalog.forClass(type);
            for (int i = 0; i < abilities.size(); i++) {
                names[i] = Text.translatable("ability.rpgplayable." + abilities.get(i).id().id());
                descriptions[i] = Text.translatable("ability.rpgplayable." + abilities.get(i).id().id() + ".description");
            }
            dirty = true;
        }
        if (page < 3 && page >= abilities.size()) { page = 3; scroll = 0; dirty = true; }
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].active = i == 3 || i < abilities.size();
            tabs[i].selected(i == page);
            tabs[i].accent(RpgUi.accent(playerClass));
            if (i == 3) {
                if (classChanged) tabs[i].tooltip(Text.translatable("screen.rpgplayable.basics.tooltip"));
                continue;
            }
            Text key = boundKey(i);
            if (classChanged || keys[i] == null || !keys[i].getString().equals(key.getString())) {
                keys[i] = key;
                tabs[i].tooltip(i < abilities.size() ? Text.translatable("screen.rpgplayable.ability_tooltip",
                        names[i], key, descriptions[i]) : Text.translatable("screen.rpgplayable.choose_first"));
                dirty = true;
            }
        }
        var combat = ClientAbilities.state().snapshot();
        if (combat != combatObserved) { combatObserved = combat; dirty = true; }
        if (dirty) {
            rebuildContent();
            dirty = false;
        }
    }

    private Text boundKey(int slot) {
        if (client != null) for (var binding : client.options.allKeys)
            if (binding.getId().equals("key.rpgplayable.ability_" + (slot + 1))) return binding.getBoundKeyLocalizedText();
        return Text.translatable("key.keyboard.unknown");
    }

    private void rebuildContent() {
        content.clear();
        int maxWidth = panelWidth - 38;
        int accent = RpgUi.accent(playerClass);
        int y = 3;
        if (page == 3 || page >= abilities.size()) {
            for (int i = 0; i < basics.length; i++) {
                y = lines(Text.literal("0" + (i + 1) + "  ").append(basicHeadings[i]), 0, y, maxWidth, accent);
                y = lines(basics[i], 0, y + 3, maxWidth, MUTED) + 12;
            }
            contentHeight = y - 8;
        } else {
            var ability = abilities.get(page);
            y = lines(names[page], 25, 6, maxWidth - 25, INK);
            y = lines(Text.translatable("screen.rpgplayable.ability_key", keys[page]), 0, Math.max(29, y + 9), maxWidth, accent);
            int paragraphY = y + 13;
            int descriptionWidth = wide ? maxWidth - 186 : maxWidth;
            int afterDescription = lines(descriptions[page], 0, paragraphY, descriptionWidth, MUTED);
            var combat = ClientAbilities.state().snapshot();
            boolean detailsKnown = combat != null && combat.cooldownTotals()[ability.slot()] != 0;
            cost = detailsKnown ? Text.translatable("screen.rpgplayable.ability_cost", number(combat.costs()[ability.slot()]),
                    Text.translatable(ability.resource() == ResourceType.MANA ? "screen.rpgplayable.mana" : "screen.rpgplayable.stamina"))
                    : Text.translatable("screen.rpgplayable.details_waiting");
            cooldown = detailsKnown ? Text.translatable("screen.rpgplayable.ability_cooldown", seconds(combat.cooldownTotals()[ability.slot()])) : Text.empty();
            timing = combat != null && combat.activeSlot() == page
                    ? Text.translatable("screen.rpgplayable.ability_timing", seconds(combat.windupTicks()), seconds(combat.recoveryTicks())) : Text.empty();
            int detailX = wide ? maxWidth - 166 : 0;
            int detailY = wide ? paragraphY : afterDescription + 12;
            int detailWidth = wide ? 162 : maxWidth;
            y = lines(CAST_DETAILS, detailX, detailY, detailWidth, INK);
            y = lines(cost, detailX, y + 7, detailWidth, 0xff95c1d9);
            if (!cooldown.getString().isEmpty()) y = lines(cooldown, detailX, y + 5, detailWidth, 0xff95c1d9);
            if (!timing.getString().isEmpty()) y = lines(timing, detailX, y + 5, detailWidth, 0xff95c1d9);
            contentHeight = Math.max(afterDescription, y) + 4;
        }
        scroll = Math.max(0, Math.min(scroll, maximumScroll()));
    }

    private int lines(Text text, int x, int y, int maxWidth, int color) {
        for (var line : textRenderer.wrapLines(text, maxWidth)) {
            content.add(new Line(line, x, y, color));
            y += 11;
        }
        return y;
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int accent = RpgUi.accent(playerClass);
        begin(context, accent);
        heading(context, subtitle, accent);
        context.fill(left + 14, top + 72, left + panelWidth - 14, top + 73, tint(0xff293d47));
        context.enableScissor(left + 13, contentTop, left + panelWidth - 13, contentBottom);
        context.getMatrices().pushMatrix();
        float transition = changedPageAt == 0 ? 1 : Math.min(1, (System.nanoTime() - changedPageAt) / 170_000_000f);
        float eased = 1 - (1 - transition) * (1 - transition);
        context.getMatrices().translate(Math.round((1 - eased) * 7), -scroll);
        if (page < abilities.size()) context.drawItem(AbilityHud.icon(abilities.get(page).id()), left + 19, contentTop + 2);
        for (var line : content) {
            int y = contentTop + line.y();
            if (y + 10 < contentTop + scroll || y > contentBottom + scroll) continue;
            int color = tint(line.color());
            color = color & 0xffffff | Math.round((color >>> 24) * (.25f + .75f * eased)) << 24;
            context.drawTextWithShadow(textRenderer, line.text(), left + 19 + line.x(), y, color);
        }
        context.getMatrices().popMatrix();
        context.disableScissor();
        if (maximumScroll() > 0) {
            int trackHeight = contentBottom - contentTop;
            int thumbHeight = Math.max(12, trackHeight * trackHeight / contentHeight);
            int thumbY = contentTop + (trackHeight - thumbHeight) * scroll / maximumScroll();
            context.fill(left + panelWidth - 11, contentTop, left + panelWidth - 9, contentBottom, tint(0xff263b45));
            context.fill(left + panelWidth - 11, thumbY, left + panelWidth - 9, thumbY + thumbHeight, tint(accent));
            draw(context, SCROLL_HINT, left + 16, contentBottom + 3, panelWidth - 32, MUTED);
        }
        super.render(context, mouseX, mouseY, delta);
        end(context);
    }

    private int maximumScroll() { return Math.max(0, contentHeight - (contentBottom - contentTop)); }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= left && mouseX < left + panelWidth && mouseY >= contentTop && mouseY < contentBottom && maximumScroll() > 0) {
            scroll = Math.max(0, Math.min(maximumScroll(), scroll - (int) Math.round(verticalAmount * 22)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override public boolean keyPressed(KeyInput input) {
        if (maximumScroll() > 0) {
            int key = input.key();
            if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_PAGE_UP) {
                scroll = Math.max(0, Math.min(maximumScroll(), scroll + (key == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * (contentBottom - contentTop - 11)));
                return true;
            }
            if (key == GLFW.GLFW_KEY_HOME || key == GLFW.GLFW_KEY_END) {
                scroll = key == GLFW.GLFW_KEY_HOME ? 0 : maximumScroll();
                return true;
            }
        }
        return super.keyPressed(input);
    }

    private static String number(double value) { return String.format(Locale.ROOT, value == Math.rint(value) ? "%.0f" : "%.1f", value); }
    private static String seconds(int ticks) { return String.format(Locale.ROOT, "%.2f", ticks / 20.0); }

    @Override protected void addScreenNarrations(NarrationMessageBuilder builder) {
        super.addScreenNarrations(builder);
        builder.put(NarrationPart.USAGE, page == 3 || page >= abilities.size() ? basicsNarration
                : Text.translatable("screen.rpgplayable.ability_narration", names[page], keys[page], descriptions[page], cost, cooldown).append(" ").append(timing));
    }

    @Override public void close() { client.setScreen(parent); }
}
