package dev.deltavisuals.feature;

import dev.deltavisuals.core.Bind;
import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.gui.ClickGuiScreen;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public final class GuiModule extends Module {
    public GuiModule() {
        super("Click GUI", "Opens this menu. Change the key with the Keybind button", Category.CONTROLS, true);
        alwaysOn = true;
        action = true;
        defaultBind(Bind.key(GLFW.GLFW_KEY_RIGHT_SHIFT));
    }

    @Override
    public void onBind(MinecraftClient mc) {
        if (mc.currentScreen == null) mc.setScreen(new ClickGuiScreen());
    }
}
