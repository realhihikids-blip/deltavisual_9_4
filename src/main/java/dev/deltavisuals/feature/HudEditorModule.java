package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.gui.HudEditorScreen;
import net.minecraft.client.MinecraftClient;

public final class HudEditorModule extends Module {
    public HudEditorModule() {
        super("HUD Editor", "Drag, scale, enable and reset HUD elements", Category.CONTROLS, true);
        alwaysOn = true;
        action = true;
        add(new Setting.Action("Open Editor", () -> open(MinecraftClient.getInstance())));
        add(new Setting.Info("Tip", () -> "Wheel = scale, R = reset hovered"));
    }

    private static void open(MinecraftClient mc) {
        if (mc.player != null) mc.setScreen(new HudEditorScreen());
    }

    @Override
    public void onBind(MinecraftClient mc) { if (mc.currentScreen == null) open(mc); }
}
