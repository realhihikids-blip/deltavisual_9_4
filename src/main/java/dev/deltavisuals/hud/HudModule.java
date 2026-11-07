package dev.deltavisuals.hud;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.gui.Theme;
import dev.deltavisuals.gui.Ui;
import dev.deltavisuals.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Base for movable HUD elements. Position is stored as a fraction (0..1) of the free screen space, so it
 * survives resolution / GUI-scale changes and an element can never leave the screen.
 */
public abstract class HudModule extends Module {
    private static final int PAD = 3;

    public final Setting.Num posX, posY, scale, bgOpacity;
    public final Setting.Color color;
    public final Setting.Bool background;
    private final double defX, defY;

    /** Last rendered bounds in screen pixels (used by the HUD editor for hit-testing). */
    public int lastX, lastY, lastW, lastH;

    protected HudModule(String name, String desc, Category cat, boolean defEnabled, double defX, double defY) {
        super(name, desc, cat, defEnabled);
        this.defX = defX;
        this.defY = defY;
        posX = add(new Setting.Num("Pos X", 0, 1, 0.0005, defX));
        posY = add(new Setting.Num("Pos Y", 0, 1, 0.0005, defY));
        posX.hidden = true;
        posY.hidden = true;
        scale = add(new Setting.Num("Scale", 0.5, 2.5, 0.05, 1.0, "x"));
        color = add(new Setting.Color("Text Color", 0xFFEDE9FE, true));
        background = add(new Setting.Bool("Background", true));
        bgOpacity = add(new Setting.Num("Background Opacity", 0, 100, 1, 45, "%"));
        add(new Setting.Action("Reset Position", this::resetPosition));
    }

    public void resetPosition() {
        posX.set(defX);
        posY.set(defY);
        scale.set(scale.def);
    }

    // --- implemented by elements --------------------------------------------------
    /** Collect data for this frame. Return false to skip drawing (nothing to show). */
    protected abstract boolean prepare(MinecraftClient mc, boolean editing);
    protected abstract int contentW();
    protected abstract int contentH();
    protected abstract void draw(DrawContext g, MinecraftClient mc, int x, int y);
    /** Opacity of the whole element (background included), 0..1. Elements that fade in and out override this. */
    protected float alphaMul() { return 1f; }
    /** Whether the thin accent bar on the left edge of the background is drawn. */
    protected boolean accentBar() { return true; }

    public final void render(DrawContext g, MinecraftClient mc, boolean editing) {
        if (!isEnabled()) return;
        if (!prepare(mc, editing)) { lastW = 0; lastH = 0; return; }
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        float s = scale.floatValue();
        int cw = contentW() + PAD * 2, ch = contentH() + PAD * 2;
        int bw = Math.round(cw * s), bh = Math.round(ch * s);
        int bx = Math.max(0, Math.min(Math.max(0, sw - bw), (int) Math.round(posX.get() * Math.max(0, sw - bw))));
        int by = Math.max(0, Math.min(Math.max(0, sh - bh), (int) Math.round(posY.get() * Math.max(0, sh - bh))));
        lastX = bx; lastY = by; lastW = bw; lastH = bh;

        var m = g.getMatrices();
        m.pushMatrix();
        m.translate(bx, by);
        m.scale(s, s);
        float prev = Ui.alpha;
        Ui.alpha = alphaMul();
        if (background.get() && bgOpacity.get() > 0) {
            int a = Math.round(bgOpacity.floatValue() * 2.55f);
            Ui.round(g, 0, 0, cw, ch, 5, ColorUtil.argb(a, 10, 8, 20));
            Ui.round(g, 0, 0, cw, 1, 1, ColorUtil.withAlpha(0xFFFFFFFF, Math.round(a * 0.10f)));
            if (accentBar()) Ui.round(g, 0, 4, 2, Math.max(2, ch - 8), 1, ColorUtil.withAlpha(Theme.accent(), Math.min(255, Math.round(a * 1.6f))));
        }
        draw(g, mc, PAD, PAD);
        Ui.alpha = prev;
        m.popMatrix();
    }

    /** Sets position from a desired top-left pixel (used by the editor while dragging). */
    public void moveTo(MinecraftClient mc, int px, int py) {
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int fx = Math.max(0, sw - lastW), fy = Math.max(0, sh - lastH);
        posX.set(fx == 0 ? 0 : Math.max(0, Math.min(1, px / (double) fx)));
        posY.set(fy == 0 ? 0 : Math.max(0, Math.min(1, py / (double) fy)));
    }
}
