package dev.fupez.rpgplayable.client.screen;

import dev.fupez.rpgplayable.client.ui.RpgUi;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Shared frame, typography and a brief opening motion for the character menus. */
abstract class RpgMenuScreen extends Screen {
    protected static final int INK = 0xffe6e5df, MUTED = 0xff91a4ad, GOLD = 0xffd5b76b;
    protected int left, top, panelWidth, panelHeight;
    private long opened;
    private float opacity = 1;

    protected RpgMenuScreen(Text title) { super(title); }

    protected void layout(int maximumWidth, int maximumHeight) {
        panelWidth = Math.min(maximumWidth, width - 12);
        panelHeight = Math.min(maximumHeight, height - 8);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        opened = System.nanoTime();
    }

    protected void begin(DrawContext context, int accent) {
        float progress = Math.min(1, (System.nanoTime() - opened) / 180_000_000f);
        float eased = 1 - (1 - progress) * (1 - progress);
        opacity = .3f + .7f * eased;
        setWidgetAlpha(opacity);
        context.fillGradient(0, 0, width, height, 0x70202830, 0xb0060c12);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0, Math.round(5 * (1 - eased)));
        RpgUi.panel(context, left, top, panelWidth, panelHeight, accent, opacity);
        context.fill(left + 14, top + 37, left + panelWidth - 14, top + 38, tint(0xff2d3e47));
        context.fill(left + 14, top + 37, left + 54, top + 38, tint(accent));
    }

    protected void end(DrawContext context) { context.getMatrices().popMatrix(); }
    protected int tint(int color) { return color & 0xffffff | Math.round((color >>> 24) * opacity) << 24; }

    protected void draw(DrawContext context, Text text, int x, int y, int maxWidth, int color) {
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(text.getString(), Math.max(0, maxWidth)), x, y, tint(color));
    }

    protected int wrapped(DrawContext context, Text text, int x, int y, int maxWidth, int color) {
        for (var line : textRenderer.wrapLines(text, maxWidth)) {
            context.drawTextWithShadow(textRenderer, line, x, y, tint(color));
            y += 11;
        }
        return y;
    }

    protected void heading(DrawContext context, Text subtitle, int accent) {
        draw(context, title, left + 16, top + 10, panelWidth - 32, INK);
        draw(context, subtitle, left + 16, top + 24, panelWidth - 32, accent);
    }

    @Override public boolean shouldPause() { return false; }
}
