package dev.fupez.rpgplayable.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/** A normal keyboard/narrator accessible Minecraft button with the RPG menu treatment. */
public class RpgButton extends ButtonWidget {
    private int accent;
    private boolean selected;
    private Text narration;
    private float hover;
    private long lastFrame;

    public RpgButton(int x, int y, int width, int height, Text message, int accent, PressAction action) {
        super(x, y, width, height, message, action, DEFAULT_NARRATION_SUPPLIER);
        this.accent = accent;
    }

    public RpgButton tooltip(Text text) { setTooltip(Tooltip.of(text)); return this; }
    public RpgButton narration(Text text) { narration = text; return this; }
    public void accent(int color) { accent = color; }
    public void selected(boolean value) { selected = value; }
    protected int accent() { return accent; }
    protected float hoverAmount() { return hover; }
    protected boolean selected() { return selected; }

    protected int color(int color) {
        int opacity = Math.round((color >>> 24) * alpha);
        return color & 0x00ffffff | opacity << 24;
    }

    protected void renderSurface(DrawContext context) {
        long now = System.nanoTime();
        float elapsed = lastFrame == 0 ? 0 : Math.min(.05f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        float target = active && (isHovered() || isFocused()) ? 1 : 0;
        hover += Math.max(-elapsed / .16f, Math.min(elapsed / .16f, target - hover));
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int border = active ? (selected || isFocused() ? accent : mix(0xff36454e, accent, hover * .7f)) : 0xff30383c;
        context.fill(x, y, x + w, y + h, color(border));
        int surface = selected ? mix(0xff192630, accent, .12f) : mix(0xff16222c, accent, hover * .10f);
        if (!active) surface = 0xff121b22;
        context.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, color(surface), color(0xff0e1821));
        context.fill(x + 1, y + 1, x + w - 1, y + 2, color(active ? mix(surface, accent, .22f + hover * .24f) : 0xff202c33));
        if (selected) context.fill(x + 1, y + 3, x + 3, y + h - 3, color(accent));
        if (isFocused()) {
            context.fill(x + 3, y + h - 3, x + w - 3, y + h - 2, color(accent));
        }
    }

    @Override protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        renderSurface(context);
        var renderer = MinecraftClient.getInstance().textRenderer;
        int textColor = active ? selected || isHovered() || isFocused() ? 0xfffff1cc : 0xffd6dee1 : 0xff64747d;
        context.drawCenteredTextWithShadow(renderer, renderer.trimToWidth(getMessage().getString(), getWidth() - 10),
                getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, color(textColor));
    }

    @Override protected MutableText getNarrationMessage() {
        return narration == null ? super.getNarrationMessage() : narration.copy();
    }

    public static int mix(int from, int to, float amount) {
        float t = Math.max(0, Math.min(1, amount));
        int r = Math.round((from >> 16 & 255) * (1 - t) + (to >> 16 & 255) * t);
        int g = Math.round((from >> 8 & 255) * (1 - t) + (to >> 8 & 255) * t);
        int b = Math.round((from & 255) * (1 - t) + (to & 255) * t);
        return 0xff000000 | r << 16 | g << 8 | b;
    }
}
