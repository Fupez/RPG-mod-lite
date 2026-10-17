package dev.fupez.rpgplayable.client.screen;

import dev.fupez.rpgplayable.client.ClientNumbers;
import dev.fupez.rpgplayable.client.ClientProgress;
import dev.fupez.rpgplayable.client.ClientProgressState;
import dev.fupez.rpgplayable.client.ui.RpgButton;
import dev.fupez.rpgplayable.client.ui.RpgUi;
import dev.fupez.rpgplayable.network.ProgressActionPayload;
import dev.fupez.rpgplayable.network.ProgressSnapshotPayload;
import dev.fupez.rpgplayable.player.PlayerClass;
import dev.fupez.rpgplayable.stats.Stat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/** All five allocations and every class choice remain available at a 320 x 240 GUI. */
public final class CharacterScreen extends RpgMenuScreen {
    private static final PlayerClass[] CLASS_TYPES = {PlayerClass.WARRIOR, PlayerClass.MAGE, PlayerClass.ROGUE};
    private static final Stat[] STATS = Stat.values();
    private static final Text[] CLASS_NAMES = {Text.empty(), Text.translatable("class.rpgplayable.warrior"),
            Text.translatable("class.rpgplayable.mage"), Text.translatable("class.rpgplayable.rogue")};
    private static final Text CURRENT_EFFECTS = Text.translatable("screen.rpgplayable.current_effects");
    private static final Text EFFECTS_NOTE = Text.translatable("screen.rpgplayable.effects_note");
    private static final Text INSPECT_HINT = Text.translatable("screen.rpgplayable.inspect_hint");
    private static final Text PERMANENT_WARNING = Text.translatable("screen.rpgplayable.permanent_warning");
    private static final Text PREVIEW_HINT = Text.translatable("screen.rpgplayable.preview_hint");
    private static final Text SELECT_CLASS = Text.translatable("screen.rpgplayable.preview_class");
    private static final Text CLASS_SUBTITLE = Text.translatable("screen.rpgplayable.class_select.subtitle");
    private static final Text UNAVAILABLE = Text.translatable("screen.rpgplayable.unavailable");
    private static final Text ATTRIBUTES = Text.translatable("screen.rpgplayable.attributes");
    private static final Text PROFILE_HINT = Text.translatable("screen.rpgplayable.profile_hint");
    private static final Text POINT_DEDUCTION = Text.literal("−1");
    private static final int[] SELECT = {ProgressActionPayload.SELECT_WARRIOR, ProgressActionPayload.SELECT_MAGE, ProgressActionPayload.SELECT_ROGUE};
    private static final int[] ALLOCATE = {ProgressActionPayload.ALLOCATE_STRENGTH, ProgressActionPayload.ALLOCATE_VITALITY,
            ProgressActionPayload.ALLOCATE_AGILITY, ProgressActionPayload.ALLOCATE_INTELLIGENCE, ProgressActionPayload.ALLOCATE_SPIRIT};
    private final ClassCard[] classes = new ClassCard[3];
    private final StatRow[] rows = new StatRow[5];
    private final RpgButton[] allocations = new RpgButton[5];
    private final Text[] labels = new Text[5], effects = new Text[5], descriptions = new Text[5];
    private final String[] values = new String[5];
    private final Text[] valueTexts = new Text[5];
    private final long[] changedAt = new long[5];
    private final ItemStack[] statIcons = {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.APPLE),
            new ItemStack(Items.FEATHER), new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.GHAST_TEAR)};
    private ProgressSnapshotPayload observed;
    private RpgButton confirm;
    private PlayerClass staged = PlayerClass.UNSELECTED;
    private int selectedStat, statsX, statsY, statsWidth, rowHeight, detailsX, detailsWidth;
    private boolean wide;
    private long pointsChangedAt;
    private Text levelXp = Text.empty(), points = Text.empty();
    private Text profileLevel = Text.empty(), profileXp = Text.empty(), profileRole = Text.empty(), profileResource = Text.empty();
    private Text previewText = PREVIEW_HINT, statusText = INSPECT_HINT;
    private PlayerClass shownStaged;
    private ClientProgressState.Feedback shownFeedback;

    public CharacterScreen() {
        super(Text.translatable("screen.rpgplayable.character"));
        for (int i = 0; i < STATS.length; i++) {
            labels[i] = Text.translatable("stat.rpgplayable." + STATS[i].id());
            descriptions[i] = Text.translatable("stat.rpgplayable." + STATS[i].id() + ".description");
            effects[i] = Text.empty();
            values[i] = "0";
            valueTexts[i] = Text.literal("0");
        }
    }

    @Override protected void init() {
        layout(680, 290);
        wide = panelWidth >= 564;
        int cardWidth = (panelWidth - 40) / 3;
        int cardHeight = panelHeight - 114;
        for (int i = 0; i < classes.length; i++) {
            final int index = i;
            classes[i] = addDrawableChild(new ClassCard(left + 14 + i * (cardWidth + 6), top + 47,
                    cardWidth, cardHeight, CLASS_TYPES[i], button -> {
                        staged = CLASS_TYPES[index];
                        refresh();
                        narrateScreenIfNarrationEnabled(true);
                    }));
        }
        statsX = wide ? left + 170 : left + 14;
        statsWidth = wide ? Math.min(240, (panelWidth - 184) / 2) : panelWidth - 28;
        rowHeight = wide ? Math.min(27, (panelHeight - 122) / 5) : Math.min(25, (panelHeight - 132) / 5);
        statsY = wide ? top + 72 : top + 52;
        detailsX = statsX + statsWidth + 10;
        detailsWidth = left + panelWidth - 14 - detailsX;
        for (int i = 0; i < rows.length; i++) {
            final int index = i;
            rows[i] = addDrawableChild(new StatRow(statsX, statsY + i * rowHeight, statsWidth - 34, rowHeight - 2, i));
            allocations[i] = addDrawableChild(new RpgButton(statsX + statsWidth - 30, statsY + i * rowHeight,
                    30, rowHeight - 2, Text.translatable("screen.rpgplayable.plus_one"), GOLD, button -> {
                        selectedStat = index;
                        if (ClientProgress.request(ALLOCATE[index])) refresh();
                    }).narration(Text.translatable("screen.rpgplayable.allocate", labels[i])));
        }
        int footerY = top + panelHeight - 28;
        int guideWidth = wide ? 124 : 92;
        addDrawableChild(new RpgButton(left + 14, footerY, guideWidth, 20,
                Text.translatable("screen.rpgplayable.guide"), GOLD,
                button -> client.setScreen(new AbilityGuideScreen(this)))
                .tooltip(Text.translatable("screen.rpgplayable.guide.tooltip")));
        int doneWidth = wide ? 80 : 58;
        addDrawableChild(new RpgButton(left + panelWidth - doneWidth - 14, footerY, doneWidth, 20,
                Text.translatable("gui.done"), GOLD, button -> close()));
        int confirmX = left + guideWidth + 20;
        confirm = addDrawableChild(new RpgButton(confirmX, footerY, panelWidth - guideWidth - doneWidth - 40, 20,
                Text.translatable("screen.rpgplayable.preview_class"), GOLD, button -> {
                    for (int i = 0; i < CLASS_TYPES.length; i++) if (CLASS_TYPES[i] == staged) {
                        if (ClientProgress.request(SELECT[i])) refresh();
                        break;
                    }
                }).tooltip(PERMANENT_WARNING));
        observed = null;
        shownStaged = null;
        refresh();
    }

    @Override public void tick() { refresh(); }

    private void refresh() {
        var state = ClientProgress.state();
        var snapshot = state.snapshot();
        if (snapshot == null) observed = null;
        boolean selected = snapshot != null && snapshot.progress().selected();
        boolean ready = snapshot != null && !state.pending() && client != null && client.player != null
                && client.player.isAlive() && !client.player.isSpectator();
        boolean becameSelected = selected && observed != null && !observed.progress().selected();
        if (state.feedback() != shownFeedback) {
            shownFeedback = state.feedback();
            statusText = shownFeedback == ClientProgressState.Feedback.NONE ? INSPECT_HINT
                    : Text.translatable("screen.rpgplayable." + shownFeedback.name().toLowerCase(Locale.ROOT));
        }
        if (snapshot != null && snapshot != observed) {
            var progress = snapshot.progress();
            long now = System.nanoTime();
            for (int i = 0; i < STATS.length; i++) {
                if (observed != null && progress.stat(STATS[i]).compareTo(observed.progress().stat(STATS[i])) > 0) changedAt[i] = now;
                values[i] = ClientNumbers.compact(progress.stat(STATS[i]));
                valueTexts[i] = Text.literal(values[i]);
            }
            if (observed != null && progress.unspentPoints().compareTo(observed.progress().unspentPoints()) < 0) pointsChangedAt = now;
            observed = snapshot;
            levelXp = Text.translatable("screen.rpgplayable.level_xp", ClientNumbers.compact(progress.level()),
                    ClientNumbers.compact(progress.xp()), ClientNumbers.compact(snapshot.nextLevelXp()));
            points = Text.translatable("screen.rpgplayable.points", ClientNumbers.compact(progress.unspentPoints()));
            profileLevel = Text.translatable("screen.rpgplayable.level", ClientNumbers.compact(progress.level()));
            profileXp = Text.translatable("screen.rpgplayable.profile_xp", ClientNumbers.compact(progress.xp()), ClientNumbers.compact(snapshot.nextLevelXp()));
            profileRole = Text.translatable("screen.rpgplayable.role." + progress.playerClass().id());
            profileResource = Text.translatable("screen.rpgplayable.resource." + progress.playerClass().id());
            var d = snapshot.derived();
            effects[0] = Text.translatable("screen.rpgplayable.effect.strength", number(d.meleeBonus()), percent(d.miningSpeedMultiplier()));
            effects[1] = Text.translatable("screen.rpgplayable.effect.vitality", number(d.maxHealthBonus()), number(d.maxStamina()));
            effects[2] = Text.translatable("screen.rpgplayable.effect.agility", percent(d.movementSpeedMultiplier()), number(d.attackSpeedBonus()));
            effects[3] = Text.translatable("screen.rpgplayable.effect.intelligence", percent(d.magicMultiplier()), number(d.maxMana()));
            effects[4] = Text.translatable("screen.rpgplayable.effect.spirit", number(d.manaRegen()), number(d.staminaRegen()), percent(d.supportMultiplier()));
            for (int i = 0; i < STATS.length; i++) {
                Text tip = descriptions[i].copy().append("\n").append(CURRENT_EFFECTS)
                        .append("\n").append(effects[i]).append("\n").append(EFFECTS_NOTE);
                rows[i].tooltip(tip).narration(labels[i].copy().append(": " + values[i] + ". ").append(descriptions[i]).append(" ").append(effects[i]));
                allocations[i].tooltip(Text.translatable("screen.rpgplayable.allocate_cost", labels[i]).append("\n").append(effects[i]));
            }
        }
        for (int i = 0; i < classes.length; i++) {
            classes[i].visible = snapshot != null && !selected;
            classes[i].active = ready;
            classes[i].selected(staged == CLASS_TYPES[i]);
        }
        int accent = snapshot == null ? GOLD : RpgUi.accent(snapshot.progress().playerClass());
        for (int i = 0; i < rows.length; i++) {
            rows[i].visible = allocations[i].visible = selected;
            rows[i].active = selected;
            rows[i].accent(accent);
            allocations[i].accent(accent);
            allocations[i].active = ready && selected && snapshot.progress().unspentPoints().signum() > 0;
        }
        confirm.visible = snapshot != null && !selected;
        confirm.active = ready && staged != PlayerClass.UNSELECTED;
        confirm.accent(RpgUi.accent(staged));
        if (staged != shownStaged) {
            shownStaged = staged;
            confirm.setMessage(staged == PlayerClass.UNSELECTED ? SELECT_CLASS
                    : Text.translatable("screen.rpgplayable.confirm_class", className(staged)));
            confirm.narration(staged == PlayerClass.UNSELECTED ? SELECT_CLASS
                    : Text.translatable("screen.rpgplayable.confirm_narration", className(staged)));
            previewText = staged == PlayerClass.UNSELECTED ? PREVIEW_HINT
                    : Text.translatable("screen.rpgplayable.preview_selected", className(staged));
        }
        if (becameSelected) {
            setInitialFocus(rows[selectedStat]);
            narrateScreenIfNarrationEnabled(true);
        }
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        var state = ClientProgress.state();
        if (state.snapshot() != observed) refresh();
        var snapshot = state.snapshot();
        var type = snapshot == null ? PlayerClass.UNSELECTED : snapshot.progress().playerClass();
        int accent = RpgUi.accent(type);
        begin(context, accent);
        heading(context, snapshot == null ? UNAVAILABLE : snapshot.progress().selected() ? className(type) : CLASS_SUBTITLE, accent);
        if (snapshot != null && snapshot.progress().selected()) {
            int hovered = -1, focused = -1;
            for (int i = 0; i < rows.length; i++) {
                if (rows[i].isMouseOver(mouseX, mouseY) || allocations[i].isMouseOver(mouseX, mouseY)) hovered = i;
                if (rows[i].isFocused() || allocations[i].isFocused()) focused = i;
            }
            if (hovered >= 0) selectedStat = hovered;
            else if (focused >= 0) selectedStat = focused;
            for (int i = 0; i < rows.length; i++) rows[i].selected(i == selectedStat);
            if (wide) renderProfile(context, accent);
            else {
                draw(context, points, left + 112, top + 24, panelWidth - 128, pulse(pointsChangedAt) > 0 ? 0xffffdf8d : GOLD);
                draw(context, levelXp, left + 14, top + 41, panelWidth - 28, MUTED);
                renderEffects(context, left + 14, statsY + 5 * rowHeight + 5, panelWidth - 28, false, accent);
            }
            if (wide) {
                draw(context, ATTRIBUTES, statsX, top + 50, statsWidth, MUTED);
                draw(context, points, statsX, top + 61, statsWidth, pulse(pointsChangedAt) > 0 ? 0xffffdf8d : accent);
                renderEffects(context, detailsX, top + 48, detailsWidth, true, accent);
            }
        } else if (snapshot != null) {
            int warningY = top + panelHeight - 60;
            draw(context, PERMANENT_WARNING, left + 14, warningY,
                    panelWidth - 28, 0xffdfbd84);
            boolean feedback = state.pending() || state.feedback() == ClientProgressState.Feedback.REJECTED || state.feedback() == ClientProgressState.Feedback.TIMEOUT;
            draw(context, feedback ? statusText : previewText, left + 14,
                    warningY + 12, panelWidth - 28, MUTED);
        }
        super.render(context, mouseX, mouseY, delta);
        if (snapshot != null && snapshot.progress().selected()) {
            int feedbackY = top + panelHeight - 41;
            draw(context, statusText, left + 14, feedbackY, panelWidth - 28,
                    state.feedback() == ClientProgressState.Feedback.APPLIED ? 0xff8bd3ae : MUTED);
            if (pulse(pointsChangedAt) > 0) {
                int x = wide ? statsX + statsWidth - 16 : left + panelWidth - 28;
                draw(context, POINT_DEDUCTION, x, wide ? top + 49 : top + 11, 24, 0xffffdf8d);
            }
        }
        end(context);
    }

    private void renderProfile(DrawContext context, int accent) {
        var snapshot = observed;
        var progress = snapshot.progress();
        int x = left + 14, y = top + 49;
        RpgUi.panel(context, x, y, 144, panelHeight - 96, accent, .65f);
        context.drawItem(RpgUi.classIcon(progress.playerClass()), x + 10, y + 10);
        draw(context, className(progress.playerClass()), x + 32, y + 14, 102, accent);
        draw(context, profileRole, x + 10, y + 37, 126, INK);
        draw(context, profileLevel, x + 10, y + 59, 126, INK);
        draw(context, profileXp, x + 10, y + 73, 126, MUTED);
        RpgUi.bar(context, x + 10, y + 87, 124, 5, ClientNumbers.ratio(progress.xp(), snapshot.nextLevelXp()),
                ClientNumbers.ratio(progress.xp(), snapshot.nextLevelXp()), accent, 0);
        draw(context, profileResource, x + 10, y + 102, 126, MUTED);
        if (panelHeight >= 270) wrapped(context, PROFILE_HINT, x + 10, y + 122, 124, MUTED);
    }

    private void renderEffects(DrawContext context, int x, int y, int maxWidth, boolean expanded, int accent) {
        if (expanded) {
            context.drawItem(statIcons[selectedStat], x, y);
            draw(context, labels[selectedStat], x + 22, y + 4, maxWidth - 22, accent);
            int after = wrapped(context, descriptions[selectedStat], x, y + 27, maxWidth, MUTED);
            draw(context, CURRENT_EFFECTS, x, after + 12, maxWidth, INK);
            wrapped(context, effects[selectedStat], x, after + 27, maxWidth, accent);
            if (panelHeight >= 270) wrapped(context, EFFECTS_NOTE, x, top + panelHeight - 83, maxWidth, MUTED);
        } else {
            context.fill(x, y - 1, x + maxWidth, y + 26, tint(0xff172630));
            context.fill(x, y - 1, x + 2, y + 26, tint(accent));
            wrapped(context, effects[selectedStat], x + 7, y + 3, maxWidth - 12, accent);
        }
    }

    private static Text className(PlayerClass type) { return CLASS_NAMES[type.ordinal()]; }
    private static float pulse(long since) { return since == 0 ? 0 : Math.max(0, 1 - (System.nanoTime() - since) / 650_000_000f); }
    private static String number(double value) {
        if (value != 0 && Math.abs(value) < .01) return String.format(Locale.ROOT, "%.2g", value);
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
    private static String percent(double multiplier) { return number(Math.max(0, multiplier - 1) * 100); }

    @Override protected void addScreenNarrations(NarrationMessageBuilder builder) {
        super.addScreenNarrations(builder);
        if (observed != null && observed.progress().selected()) builder.put(NarrationPart.USAGE,
                points.copy().append(". ").append(Text.translatable("screen.rpgplayable.allocate_cost", labels[selectedStat])).append(". ").append(effects[selectedStat]));
        else builder.put(NarrationPart.USAGE, PERMANENT_WARNING);
    }

    private final class StatRow extends RpgButton {
        private final int index;
        private StatRow(int x, int y, int w, int h, int index) {
            super(x, y, w, h, labels[index], GOLD, button -> selectedStat = index);
            this.index = index;
        }
        @Override protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            renderSurface(context);
            float glow = pulse(changedAt[index]);
            if (glow > 0) context.fill(getX() + 1, getY() + 1, getRight() - 1, getBottom() - 1,
                    Math.round(glow * 50) << 24 | accent() & 0xffffff);
            int iconY = getY() + (getHeight() - 16) / 2;
            context.drawItem(statIcons[index], getX() + 5, iconY);
            int numberWidth = textRenderer.getWidth(values[index]);
            draw(context, labels[index], getX() + 25, getY() + (getHeight() - 8) / 2,
                    getWidth() - numberWidth - 37, selected() ? 0xffffe7aa : INK);
            draw(context, valueTexts[index], getRight() - numberWidth - 7,
                    getY() + (getHeight() - 8) / 2, numberWidth + 1, glow > 0 ? 0xffffe7aa : INK);
            if (glow > 0) {
                int y = getY() - 5 - Math.round((1 - glow) * 7);
                context.drawTextWithShadow(textRenderer, "+1", getRight() - 16, y, Math.round(glow * 255) << 24 | 0xffdf8d);
            }
        }
    }

    private final class ClassCard extends RpgButton {
        private final PlayerClass type;
        private final Text role, resource, priorities, example, firstAbility;
        private ClassCard(int x, int y, int w, int h, PlayerClass type, PressAction action) {
            super(x, y, w, h, className(type), RpgUi.accent(type), action);
            this.type = type;
            role = Text.translatable("screen.rpgplayable.role_short." + type.id());
            Text fullRole = Text.translatable("screen.rpgplayable.role." + type.id());
            Text fullPriorities = Text.translatable("screen.rpgplayable.priorities_full." + type.id());
            resource = Text.translatable("screen.rpgplayable.resource." + type.id());
            priorities = Text.translatable("screen.rpgplayable.priorities." + type.id());
            example = Text.translatable("screen.rpgplayable.example." + type.id());
            firstAbility = Text.translatable("ability.rpgplayable." + switch (type) {
                case WARRIOR -> "heavy_strike";
                case MAGE -> "firebolt";
                case ROGUE -> "dash";
                default -> throw new IllegalArgumentException("Unselected class has no preview card");
            });
            Text description = Text.translatable("class.rpgplayable." + type.id() + ".description");
            tooltip(className(type).copy().append("\n").append(fullRole).append(" · ").append(resource)
                    .append("\n").append(description).append("\n").append(fullPriorities).append("\n").append(example));
            narration(Text.translatable("screen.rpgplayable.preview_narration", className(type), fullRole, resource, fullPriorities, description, example));
        }
        @Override protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            renderSurface(context);
            int lift = Math.round(hoverAmount() * 2), x = getX() + 9, y = getY() + 9 - lift;
            context.drawItem(RpgUi.classIcon(type), x + (wide ? 0 : (getWidth() - 34) / 2), y);
            int labelY = wide ? y + 4 : y + 23;
            draw(context, getMessage(), wide ? x + 23 : x, labelY, getWidth() - (wide ? 40 : 18), accent());
            draw(context, role, x, y + (wide ? 29 : 39), getWidth() - 18, INK);
            draw(context, resource, x, y + (wide ? 44 : 53), getWidth() - 18, MUTED);
            draw(context, priorities, x, y + (wide ? 65 : 69), getWidth() - 18, accent());
            if (getHeight() >= 140) wrapped(context, example, x, y + 87, getWidth() - 18, MUTED);
            if (getHeight() < 140) draw(context, firstAbility, x, y + 85, getWidth() - 18, MUTED);
            if (selected()) context.fill(getX() + 7, getBottom() - 6, getRight() - 7, getBottom() - 4, color(accent()));
        }
    }
}
