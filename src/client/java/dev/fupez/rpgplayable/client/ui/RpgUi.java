package dev.fupez.rpgplayable.client.ui;

import dev.fupez.rpgplayable.player.PlayerClass;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Shared restrained fantasy materials. All item stacks are presentation-only cached icons. */
public final class RpgUi {
    public static final int TEXT = 0xffe8edf3, MUTED = 0xffa2afbf, GOLD = 0xffd5b76b;
    private static final ItemStack WARRIOR = new ItemStack(Items.IRON_SWORD);
    private static final ItemStack MAGE = new ItemStack(Items.AMETHYST_SHARD);
    private static final ItemStack ROGUE = new ItemStack(Items.FEATHER);
    private static final ItemStack NONE = new ItemStack(Items.COMPASS);
    private RpgUi() { }

    public static int accent(PlayerClass type) {
        return switch (type) {
            case WARRIOR -> 0xffc88864;
            case MAGE -> 0xffa88ae8;
            case ROGUE -> 0xff68b99a;
            case UNSELECTED -> GOLD;
        };
    }
    public static ItemStack classIcon(PlayerClass type) {
        return switch (type) { case WARRIOR -> WARRIOR; case MAGE -> MAGE; case ROGUE -> ROGUE; case UNSELECTED -> NONE; };
    }
    public static void panel(DrawContext context, int x, int y, int width, int height, int accent, float alpha) {
        if (width < 4 || height < 4) return;
        context.fill(x + 1, y + 2, x + width + 1, y + height + 2, alpha(0x66000000, alpha));
        context.fill(x, y, x + width, y + height, alpha(0xe70a1019, alpha));
        context.fillGradient(x + 1, y + 1, x + width - 1, y + height - 1,
                alpha(0xe6242b36, alpha), alpha(0xe4101620, alpha));
        context.fill(x + 1, y + 1, x + width - 1, y + 2, alpha(0xff616373, alpha));
        context.fill(x + 1, y + 2, x + 2, y + height - 2, alpha(0xff3c4654, alpha));
        context.fill(x + width - 2, y + 2, x + width - 1, y + height - 2, alpha(0xff252c37, alpha));
        context.fill(x + 2, y + height - 2, x + width - 2, y + height - 1, alpha(0xff090e15, alpha));
        context.fill(x + 3, y + 2, x + Math.min(width - 3, 27), y + 3, alpha(accent, alpha * .85f));
        context.fill(x + width - 5, y + 3, x + width - 3, y + 5, alpha(accent, alpha * .75f));
    }
    public static void bar(DrawContext context, int x, int y, int width, int height,
                           double fill, double trail, int color, float pulse) {
        if (width < 3 || height < 3) return;
        int interior = width - 2;
        int amount = (int)Math.round(interior * bound(fill));
        int delayed = (int)Math.round(interior * bound(trail));
        context.fill(x, y, x + width, y + height, 0xff080d14);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xff202936);
        if (delayed > amount) context.fill(x + 1 + amount, y + 1, x + 1 + delayed, y + height - 1, 0xffb8a18b);
        if (amount > 0) {
            context.fillGradient(x + 1, y + 1, x + 1 + amount, y + height - 1, brighten(color, .14f), darken(color, .24f));
            context.fill(x + 1, y + 1, x + 1 + amount, y + 2, alpha(0xffffffff, .18f + Math.clamp(pulse, 0, 1) * .22f));
            if (amount > 2) context.fill(x + amount, y + 2, x + amount + 1, y + height - 1, alpha(0xffffffff, .25f));
        }
        context.fill(x + 1, y + height - 1, x + width - 1, y + height, 0xff3a4350);
        if (pulse > 0) context.fill(x, y, x + width, y + height, alpha(color, Math.clamp(pulse, 0, 1) * .16f));
    }
    public static int alpha(int color, float opacity) {
        return (color & 0x00ffffff) | ((int)(((color >>> 24) & 255) * Math.clamp(opacity, 0, 1)) << 24);
    }
    private static double bound(double value) { return Double.isFinite(value) ? Math.clamp(value, 0, 1) : 0; }
    private static int brighten(int color, float amount) {
        int r = color >> 16 & 255, g = color >> 8 & 255, b = color & 255;
        return 0xff000000 | (r + (int)((255 - r) * amount)) << 16 | (g + (int)((255 - g) * amount)) << 8 | b + (int)((255 - b) * amount);
    }
    private static int darken(int color, float amount) {
        return 0xff000000 | (int)((color >> 16 & 255) * (1 - amount)) << 16
                | (int)((color >> 8 & 255) * (1 - amount)) << 8 | (int)((color & 255) * (1 - amount));
    }
}
