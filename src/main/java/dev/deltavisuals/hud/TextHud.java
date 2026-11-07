package dev.deltavisuals.hud;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.gui.UiFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/** HUD element made of coloured text lines. */
public abstract class TextHud extends HudModule {
    public record Line(String text, int color) { }

    private final List<Line> lines = new ArrayList<>();
    private int w, h;

    protected TextHud(String name, String desc, Category cat, boolean def, double x, double y) {
        super(name, desc, cat, def, x, y);
    }

    /** Fill lines for this frame. Leave empty to hide the element (unless editing). */
    protected abstract void collect(MinecraftClient mc, List<Line> out, boolean editing);

    @Override
    protected boolean prepare(MinecraftClient mc, boolean editing) {
        lines.clear();
        collect(mc, lines, editing);
        if (lines.isEmpty()) {
            if (!editing) return false;
            lines.add(new Line(name, color.get()));
        }
        int mw = 30;
        for (Line l : lines) mw = Math.max(mw, mc.textRenderer.getWidth(UiFont.text(l.text())));
        w = mw;
        h = lines.size() * 10 - 1;
        return true;
    }

    @Override protected int contentW() { return w; }
    @Override protected int contentH() { return h; }

    @Override
    protected void draw(DrawContext g, MinecraftClient mc, int x, int y) {
        int yy = y;
        for (Line l : lines) {
            g.drawText(mc.textRenderer, UiFont.text(l.text()), x, yy, l.color(), true);
            yy += 10;
        }
    }
}
