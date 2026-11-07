package dev.deltavisuals.hud;

import dev.deltavisuals.core.Modules;
import dev.deltavisuals.gui.HudEditorScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public final class HudRenderer {
    private HudRenderer() {}

    public static void render(DrawContext g, MinecraftClient mc) {
        if (mc.player == null || mc.options.hudHidden) return;
        if (mc.currentScreen instanceof HudEditorScreen) return; // the editor draws the elements itself
        for (HudModule m : Modules.hud()) {
            try {
                m.render(g, mc, false);
            } catch (Throwable t) {
                m.setEnabled(false); // never take the HUD (or the game) down with one broken element
            }
        }
    }
}
