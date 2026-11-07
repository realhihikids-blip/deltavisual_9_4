package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Modules;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.gui.ClickGuiScreen;
import dev.deltavisuals.gui.HudEditorScreen;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;

/**
 * Smooth open animation (small zoom + slide) for the inventory and in-game menus.
 * Purely visual: the screen is drawn through a scaled matrix for a fraction of a second, nothing else changes.
 * The matrix push in beforeRender and the pop in afterRender are always paired.
 */
public final class ScreenAnim extends Module {
    private final Setting.Bool inventory, menus;
    private final Setting.Num duration, strength, slide;
    private Screen tracked;
    private long startNs;
    private boolean pushed;

    public ScreenAnim() {
        super("Screen Animations", "Smooth opening of the inventory and menus", Category.INTERFACE, true);
        inventory = add(new Setting.Bool("Inventory And Containers", true));
        menus = add(new Setting.Bool("Pause Menu And Options", true));
        duration = add(new Setting.Num("Duration", 80, 500, 10, 170, " ms"));
        strength = add(new Setting.Num("Zoom Amount", 0, 0.3, 0.01, 0.08));
        slide = add(new Setting.Num("Slide", 0, 40, 1, 8, " px"));
    }

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            ScreenEvents.beforeRender(screen).register((s, g, mx, my, td) -> {
                ScreenAnim a = Modules.SCREEN_ANIM;
                if (a != null) a.begin(s, g);
            });
            ScreenEvents.afterRender(screen).register((s, g, mx, my, td) -> {
                ScreenAnim a = Modules.SCREEN_ANIM;
                if (a != null) a.end(g);
            });
        });
    }

    private boolean eligible(Screen s) {
        if (s instanceof ClickGuiScreen || s instanceof HudEditorScreen || s instanceof ChatScreen) return false;
        if (s instanceof HandledScreen<?>) return inventory.get();
        return menus.get() && MinecraftClient.getInstance().world != null;
    }

    private void begin(Screen s, DrawContext g) {
        try {
            if (pushed || !isEnabled() || !eligible(s)) return;
            if (s != tracked) { tracked = s; startNs = System.nanoTime(); }
            float t = (System.nanoTime() - startNs) / 1_000_000f / duration.floatValue();
            if (t >= 1f) return;
            float inv = 1f - t;
            float ease = 1f - inv * inv * inv;
            float sc = 1f - strength.floatValue() * (1f - ease);
            float cx = s.width / 2f, cy = s.height / 2f;
            var m = g.getMatrices();
            m.pushMatrix();
            pushed = true;
            m.translate(cx, cy + slide.floatValue() * (1f - ease));
            m.scale(sc, sc);
            m.translate(-cx, -cy);
        } catch (Throwable ignored) { /* an animation must never break a screen */ }
    }

    private void end(DrawContext g) {
        if (!pushed) return;
        pushed = false;
        try { g.getMatrices().popMatrix(); } catch (Throwable ignored) { }
    }
}
