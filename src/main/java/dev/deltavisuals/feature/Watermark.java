package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.gui.Theme;
import dev.deltavisuals.hud.TextHud;
import net.minecraft.client.MinecraftClient;

import java.util.List;

public final class Watermark extends TextHud {
    private final Setting.Bool showFps;

    public Watermark() {
        super("Watermark", "Client name tag with optional FPS", Category.VISUALS, false, 0.5, 0.01);
        showFps = add(new Setting.Bool("Show FPS", true));
    }

    @Override
    protected void collect(MinecraftClient mc, List<Line> out, boolean editing) {
        String s = "DELTA VISUALS" + (showFps.get() ? "  |  " + mc.getCurrentFps() + " FPS" : "");
        out.add(new Line(s, Theme.accent()));
    }
}
