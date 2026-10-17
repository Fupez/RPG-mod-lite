package dev.fupez.rpgplayable.client.screen;

import dev.fupez.rpgplayable.client.*;
import dev.fupez.rpgplayable.network.ProgressActionPayload;
import dev.fupez.rpgplayable.player.PlayerClass;
import dev.fupez.rpgplayable.stats.Stat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.Locale;

public final class CharacterScreen extends Screen {
    private final ButtonWidget[] classes = new ButtonWidget[3], allocations = new ButtonWidget[5];
    private int panelWidth, top;
    private static final String[] CLASS_IDS = {"warrior", "mage", "rogue"};
    private static final int[] SELECT = {ProgressActionPayload.SELECT_WARRIOR, ProgressActionPayload.SELECT_MAGE, ProgressActionPayload.SELECT_ROGUE};
    private static final int[] ALLOCATE = {ProgressActionPayload.ALLOCATE_STRENGTH, ProgressActionPayload.ALLOCATE_VITALITY, ProgressActionPayload.ALLOCATE_AGILITY, ProgressActionPayload.ALLOCATE_INTELLIGENCE, ProgressActionPayload.ALLOCATE_SPIRIT};
    public CharacterScreen() { super(Text.translatable("screen.rpgplayable.character")); }
    @Override protected void init() {
        panelWidth = Math.min(340, width - 12); top = Math.max(2, (height - 232) / 2);
        int left = (width - panelWidth) / 2;
        for (int i = 0; i < classes.length; i++) {
            final int index = i;
            classes[i] = addDrawableChild(ButtonWidget.builder(Text.translatable("screen.rpgplayable.choose_class", Text.translatable("class.rpgplayable." + CLASS_IDS[i])), b -> ClientProgress.request(SELECT[index]))
                    .dimensions(left + 16, top + 78 + i * 30, panelWidth - 32, 24)
                    .tooltip(Tooltip.of(Text.translatable("class.rpgplayable." + CLASS_IDS[i] + ".description"))).build());
        }
        for (int i = 0; i < allocations.length; i++) {
            final int index = i;
            allocations[i] = addDrawableChild(ButtonWidget.builder(Text.translatable("screen.rpgplayable.plus_one"), b -> ClientProgress.request(ALLOCATE[index]))
                    .dimensions(left + panelWidth - 48, top + 76 + i * 20, 32, 18)
                    .narrationSupplier(defaultNarration -> Text.translatable("screen.rpgplayable.allocate", Text.translatable("stat.rpgplayable." + Stat.values()[index].id())))
                    .tooltip(Tooltip.of(Text.translatable("stat.rpgplayable." + Stat.values()[i].id() + ".description"))).build());
        }
        int footerWidth = (panelWidth - 36) / 2;
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.rpgplayable.guide"), b -> client.setScreen(new AbilityGuideScreen(this)))
                .dimensions(left + 16, top + 208, footerWidth, 20)
                .tooltip(Tooltip.of(Text.translatable("screen.rpgplayable.guide.tooltip"))).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
                .dimensions(left + 20 + footerWidth, top + 208, footerWidth, 20).build());
        refresh();
    }
    @Override public void tick() { refresh(); }
    private void refresh() {
        var state = ClientProgress.state(); var snapshot = state.snapshot();
        boolean selected = snapshot != null && snapshot.progress().selected();
        boolean ready = snapshot != null && !state.pending() && client != null && client.player != null && client.player.isAlive() && !client.player.isSpectator();
        for (var button : classes) { button.visible = snapshot != null && !selected; button.active = ready; }
        for (var button : allocations) { button.visible = selected; button.active = ready && snapshot.progress().unspentPoints().signum() > 0; }
    }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int left = (width - panelWidth) / 2;
        context.fill(left, top, left + panelWidth, top + 232, 0xe0101720);
        super.render(context, mouseX, mouseY, delta);
        centered(context, title, top + 9, 0xffffffff);
        var state = ClientProgress.state(); var snapshot = state.snapshot();
        if (snapshot == null) { centered(context, Text.translatable("screen.rpgplayable.unavailable"), top + 50, 0xffe7c67a); return; }
        var progress = snapshot.progress();
        centered(context, Text.translatable(progress.selected() ? "class.rpgplayable." + progress.playerClass().id() : "screen.rpgplayable.no_class"), top + 27, 0xffe7c67a);
        if (progress.selected()) {
            centered(context, Text.translatable("screen.rpgplayable.level_xp", ClientNumbers.compact(progress.level()), ClientNumbers.compact(progress.xp()), ClientNumbers.compact(snapshot.nextLevelXp())), top + 42, 0xffffffff);
            centered(context, Text.translatable("screen.rpgplayable.points", ClientNumbers.compact(progress.unspentPoints())), top + 57, 0xffc7d6e6);
            for (int i = 0; i < Stat.values().length; i++) {
                var stat = Stat.values()[i]; int y = top + 76 + i * 20;
                context.fill(left + 12, y, left + panelWidth - 54, y + 18, 0x80303b48);
                String text = Text.translatable("stat.rpgplayable." + stat.id()).getString() + "  " + ClientNumbers.compact(progress.stat(stat));
                context.drawTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(text, panelWidth - 82)), left + 17, y + 5, 0xffffffff);
            }
            if (mouseX >= left + 12 && mouseX < left + panelWidth - 54 && mouseY >= top + 76 && mouseY < top + 176) {
                int row = (mouseY - top - 76) / 20;
                var stat = Stat.values()[row];
                Text description = Text.translatable("stat.rpgplayable." + stat.id() + ".description");
                if (row == 0) description = Text.translatable("stat.rpgplayable.strength.current", description, String.format(Locale.ROOT, "%.2f", snapshot.strengthBonus()));
                context.drawOrderedTooltip(textRenderer, textRenderer.wrapLines(description, 210), mouseX, mouseY);
            }
        } else centered(context, Text.translatable("screen.rpgplayable.permanent"), top + 49, 0xffc7d6e6);
        if (state.feedback() != ClientProgressState.Feedback.NONE)
            centered(context, Text.translatable("screen.rpgplayable." + state.feedback().name().toLowerCase(Locale.ROOT)), top + 181, 0xffe7c67a);
        centered(context, Text.translatable("screen.rpgplayable.hint"), top + 195, 0xffacbac8);
    }
    private void centered(DrawContext context, Text text, int y, int color) {
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(textRenderer.trimToWidth(text.getString(), panelWidth - 16)), width / 2, y, color);
    }
    @Override public boolean shouldPause() { return false; }
}
