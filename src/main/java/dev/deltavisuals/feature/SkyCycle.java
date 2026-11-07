package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Modules;
import dev.deltavisuals.core.Setting;
import net.minecraft.client.MinecraftClient;

/** A key (set with the Keybind button) that switches Custom Sky to the next preset. Also has menu buttons. */
public final class SkyCycle extends Module {
    public SkyCycle() {
        super("Sky Preset Switch", "Bind a key to cycle the Custom Sky presets (turns the sky on)", Category.VISUALS, true);
        alwaysOn = true;
        action = true;
        add(new Setting.Action("Next Preset", () -> Modules.SKY.cyclePreset(1)));
        add(new Setting.Action("Previous Preset", () -> Modules.SKY.cyclePreset(-1)));
        add(new Setting.Info("Current", () -> Modules.SKY.preset().get()));
    }

    @Override
    public void onBind(MinecraftClient mc) { Modules.SKY.cyclePreset(1); }
}
