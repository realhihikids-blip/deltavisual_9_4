package dev.deltavisuals.gui;

import dev.deltavisuals.config.ConfigManager;
import dev.deltavisuals.core.Modules;
import dev.deltavisuals.hud.HudModule;
import dev.deltavisuals.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Drag HUD elements, mouse wheel = scale, R = reset hovered (Shift+R = reset all), chips at the bottom enable/disable. */
public final class HudEditorScreen extends Screen {
    private HudModule drag;
    private int offX, offY, lastMx, lastMy;

    public HudEditorScreen() { super(Text.literal("HUD Editor")); }

    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { ConfigManager.save(); }

    private HudModule elementAt(int x, int y) {
        List<HudModule> l = Modules.hud();
        for (int i = l.size() - 1; i >= 0; i--) {
            HudModule m = l.get(i);
            if (m.isEnabled() && m.lastW > 0 && Ui.in(x, y, m.lastX, m.lastY, m.lastW, m.lastH)) return m;
        }
        return null;
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        Ui.frame();
        Ui.alpha = 1f;
        lastMx = mouseX;
        lastMy = mouseY;
        MinecraftClient mc = MinecraftClient.getInstance();
        g.fill(0, 0, width, height, 0x66000000);

        HudModule hov = drag != null ? drag : elementAt(mouseX, mouseY);
        for (HudModule m : Modules.hud()) m.render(g, mc, true);
        for (HudModule m : Modules.hud()) {
            if (!m.isEnabled() || m.lastW <= 0) continue;
            int col = m == hov ? Theme.accent() : ColorUtil.withAlpha(Theme.accent(), 110);
            Ui.rect(g, m.lastX - 1, m.lastY - 1, m.lastW + 2, 1, col);
            Ui.rect(g, m.lastX - 1, m.lastY + m.lastH, m.lastW + 2, 1, col);
            Ui.rect(g, m.lastX - 1, m.lastY, 1, m.lastH, col);
            Ui.rect(g, m.lastX + m.lastW, m.lastY, 1, m.lastH, col);
            if (m == hov) Ui.text(g, m.name + "  " + String.format("%.2fx", m.scale.get()), m.lastX, Math.max(0, m.lastY - Ui.th() - 3), Theme.text());
        }

        String hint = "Drag: move   Wheel: scale   R: reset hovered   Shift+R: reset all   Esc: done";
        Ui.text(g, hint, (width - Ui.tw(hint)) / 2, 8, Theme.text());

        // chips: click to enable / disable an element
        int x = 8, y = height - 26;
        int rows = 1, avail = width - 16;
        int cx = x;
        for (HudModule m : Modules.hud()) {
            int w = Ui.tw(m.name) + 18;
            if (cx + w > avail) { rows++; cx = x; }
            cx += w + 6;
        }
        y = height - 8 - rows * 24;
        cx = x;
        for (HudModule m : Modules.hud()) {
            int w = Ui.tw(m.name) + 18;
            if (cx + w > avail) { cx = x; y += 24; }
            boolean on = m.isEnabled();
            boolean h = Ui.in(mouseX, mouseY, cx, y, w, 20);
            int body = on ? ColorUtil.withAlpha(Theme.accent(), h ? 230 : 190) : (h ? Theme.cardHover() : Theme.card());
            Ui.round(g, cx, y, w, 20, 6, body);
            Ui.text(g, m.name, cx + 9, y + (20 - Ui.th()) / 2, on ? 0xFFFFFFFF : Theme.dim());
            cx += w + 6;
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int x = (int) click.x(), y = (int) click.y();
        // chips
        int avail = width - 16, cx = 8, rows = 1;
        for (HudModule m : Modules.hud()) {
            int w = Ui.tw(m.name) + 18;
            if (cx + w > avail) { rows++; cx = 8; }
            cx += w + 6;
        }
        int cy = height - 8 - rows * 24;
        cx = 8;
        for (HudModule m : Modules.hud()) {
            int w = Ui.tw(m.name) + 18;
            if (cx + w > avail) { cx = 8; cy += 24; }
            if (Ui.in(x, y, cx, cy, w, 20)) { m.setEnabled(!m.isEnabled()); return true; }
            cx += w + 6;
        }
        if (click.button() == 0) {
            HudModule m = elementAt(x, y);
            if (m != null) { drag = m; offX = x - m.lastX; offY = y - m.lastY; }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (drag != null) drag.moveTo(MinecraftClient.getInstance(), (int) click.x() - offX, (int) click.y() - offY);
        return true;
    }

    @Override
    public boolean mouseReleased(Click click) { drag = null; return true; }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        HudModule m = elementAt((int) mouseX, (int) mouseY);
        if (m != null) m.scale.set(m.scale.get() + verticalAmount * 0.05);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) { MinecraftClient.getInstance().setScreen(null); return true; }
        if (key == GLFW.GLFW_KEY_R) {
            if ((input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0) {
                for (HudModule m : Modules.hud()) m.resetPosition();
            } else {
                HudModule m = elementAt(lastMx, lastMy);
                if (m != null) m.resetPosition();
            }
            return true;
        }
        return true;
    }
}
