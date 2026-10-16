package dev.fupez.rpgplayable.client.screen;

import dev.fupez.rpgplayable.client.ClientProgress;
import dev.fupez.rpgplayable.client.ClientProgressState;
import dev.fupez.rpgplayable.network.ProgressActionPayload;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.Locale;

public final class CharacterScreen extends Screen {
    private ButtonWidget action;
    private int panelWidth;
    private int top;
    public CharacterScreen() { super(Text.translatable("screen.rpgplayable.character")); }

    @Override protected void init() {
        panelWidth = Math.min(320, width - 16);
        top = Math.max(4, (height - 220) / 2);
        int buttonWidth = Math.min(220, panelWidth - 24);
        action = addDrawableChild(ButtonWidget.builder(Text.empty(), button -> {
            var snapshot = ClientProgress.state().snapshot();
            if (snapshot != null) ClientProgress.request(snapshot.progress().selected()
                    ? ProgressActionPayload.ALLOCATE_STRENGTH : ProgressActionPayload.SELECT_WARRIOR);
            refresh();
        }).dimensions((width - buttonWidth) / 2, top + 124, buttonWidth, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> close())
                .dimensions((width - buttonWidth) / 2, top + 184, buttonWidth, 20).build());
        refresh();
    }

    @Override public void tick() { refresh(); }
    private void refresh() {
        if (action == null) return;
        var state = ClientProgress.state();
        var snapshot = state.snapshot();
        boolean selected = snapshot != null && snapshot.progress().selected();
        action.setMessage(Text.translatable(selected ? "screen.rpgplayable.allocate" : "screen.rpgplayable.choose_warrior"));
        action.active = snapshot != null && !state.pending() && client.player != null
                && client.player.isAlive() && !client.player.isSpectator()
                && (!selected || snapshot.progress().unspentPoints() > 0);
    }

    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int left = (width - panelWidth) / 2;
        context.fill(left, top, left + panelWidth, top + 220, 0xc0101720);
        // Minecraft 1.21.10 already handles the screen backdrop; do not apply a second blur.
        super.render(context, mouseX, mouseY, delta);
        centered(context, title, top + 12, 0xffffffff);
        var state = ClientProgress.state();
        var snapshot = state.snapshot();
        if (snapshot == null) {
            centered(context, Text.translatable("screen.rpgplayable.unavailable"), top + 54, 0xffe0bc72);
            return;
        }
        var p = snapshot.progress();
        centered(context, Text.translatable(p.selected() ? "class.rpgplayable.warrior" : "screen.rpgplayable.no_class"), top + 36, 0xffe7c67a);
        centered(context, snapshot.nextLevelXp() == 0 ? Text.translatable("screen.rpgplayable.max_level", p.level())
                : Text.translatable("screen.rpgplayable.level_xp", p.level(), p.xp(), snapshot.nextLevelXp()), top + 54, 0xffffffff);
        int barWidth = panelWidth - 32;
        int filled = snapshot.nextLevelXp() == 0 ? barWidth : (int) (barWidth * (double) p.xp() / snapshot.nextLevelXp());
        context.fill(left + 16, top + 70, left + 16 + barWidth, top + 76, 0xff303b48);
        context.fill(left + 16, top + 70, left + 16 + filled, top + 76, 0xffd3a856);
        centered(context, Text.translatable("screen.rpgplayable.strength", p.strength(),
                String.format(Locale.ROOT, "%.2f", snapshot.strengthBonus())), top + 92, 0xffffffff);
        centered(context, Text.translatable("screen.rpgplayable.points", p.unspentPoints()), top + 108, 0xffc7d6e6);
        if (state.feedback() != ClientProgressState.Feedback.NONE)
            centered(context, Text.translatable("screen.rpgplayable." + state.feedback().name().toLowerCase(Locale.ROOT)), top + 150, 0xffe0bc72);
        centered(context, Text.translatable("screen.rpgplayable.hint"), top + 166, 0xffacbac8);
    }

    private void centered(DrawContext context, Text text, int y, int color) {
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(textRenderer.trimToWidth(text.getString(), panelWidth - 16)), width / 2, y, color);
    }
    @Override public boolean shouldPause() { return false; }
}
