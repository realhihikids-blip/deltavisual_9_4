package dev.deltavisuals.gui;

import dev.deltavisuals.util.ColorUtil;
import dev.deltavisuals.util.I18n;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Immediate-mode drawing helpers. Every colour passes through the global alpha multiplier (fade in/out). */
public final class Ui {
    private Ui() {}

    public static float alpha = 1f;
    public static float dt = 0.016f;
    private static long last = System.nanoTime();

    public static void frame() {
        long n = System.nanoTime();
        dt = Math.min(0.1f, (n - last) / 1_000_000_000f);
        last = n;
    }

    public static float step(float cur, float target, float speed) {
        if (!Theme.animations()) return target;
        return cur + (target - cur) * Math.min(1f, dt * speed);
    }

    public static int c(int argb) { return alpha >= 0.999f ? argb : ColorUtil.mulAlpha(argb, alpha); }

    // -------------------------------------------------------------- shapes
    public static void rect(DrawContext g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + h, c(color));
    }

    public static void round(DrawContext g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        int col = c(color);
        if (r == 0) { g.fill(x, y, x + w, y + h, col); return; }
        g.fill(x, y + r, x + w, y + h - r, col);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, col);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, col);
        }
    }

    /** 1px rounded outline: accent rounded rect with the body colour drawn over it. */
    public static void roundOutline(DrawContext g, int x, int y, int w, int h, int r, int outline, int body) {
        round(g, x, y, w, h, r, outline);
        round(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), body);
    }

    public static void vGradient(DrawContext g, int x, int y, int w, int h, int top, int bottom) {
        if (w <= 0 || h <= 0) return;
        g.fillGradient(x, y, x + w, y + h, c(top), c(bottom));
    }

    // ---------------------------------------------------------------- text
    private static TextRenderer tr() { return MinecraftClient.getInstance().textRenderer; }

    private static Text styled(String s) {
        MutableText t = UiFont.apply(Text.literal(I18n.t(s)));
        return Theme.bold() ? t.formatted(Formatting.BOLD) : t;
    }

    public static int tw(String s) { return Math.round(tr().getWidth(styled(s)) * Theme.textSize()); }
    public static int th() { return Math.round(9f * Theme.textSize()); }

    public static void text(DrawContext g, String s, int x, int y, int color) {
        float sz = Theme.textSize();
        var m = g.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(sz, sz);
        g.drawText(tr(), styled(s), 0, 0, c(color), Theme.shadow());
        m.popMatrix();
    }

    public static void textRight(DrawContext g, String s, int right, int y, int color) {
        text(g, s, right - tw(s), y, color);
    }

    public static String fit(String s, int maxW) {
        s = I18n.t(s);
        if (tw(s) <= maxW) return s;
        String dots = "...";
        int n = s.length();
        while (n > 0 && tw(s.substring(0, n) + dots) > maxW) n--;
        return s.substring(0, Math.max(0, n)) + dots;
    }

    public static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Small on/off switch, anim 0..1. */
    public static void toggle(DrawContext g, int x, int y, float anim) {
        int w = 26, h = 12;
        int off = ColorUtil.withAlpha(0xFF2A2640, 255);
        int on = Theme.accent();
        round(g, x, y, w, h, 6, ColorUtil.lerp(off, on, anim));
        int kx = x + 2 + Math.round(anim * (w - h));
        round(g, kx, y + 2, h - 4, h - 4, 4, 0xFFFFFFFF);
    }
}
