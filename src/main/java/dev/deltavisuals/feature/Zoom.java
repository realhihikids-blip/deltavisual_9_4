package dev.deltavisuals.feature;

import dev.deltavisuals.core.Bind;
import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Modules;
import dev.deltavisuals.core.Setting;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

/**
 * Hold-to-zoom with a smooth transition. Only changes the field of view of your own camera (like the vanilla FOV
 * slider); nothing is sent to the server. The FOV hook is GameRendererFovMixin.
 */
public final class Zoom extends Module {
    private final Setting.Num level, speed;
    private float cur = 1f;
    private long lastNs;

    public Zoom() {
        super("Zoom", "Hold the key to zoom in smoothly (client-side field of view only)", Category.CONTROLS, true);
        defaultBind(Bind.key(GLFW.GLFW_KEY_C));
        action = true; // the key is held, it must not toggle the module
        level = add(new Setting.Num("Zoom Level", 1.5, 10, 0.1, 4, "x"));
        speed = add(new Setting.Num("Smoothness Speed", 3, 30, 1, 12));
        add(new Setting.Info("Note", () -> "Hold the keybind of this module to zoom"));
    }

    private boolean held(MinecraftClient mc) {
        if (mc.player == null || mc.currentScreen != null || mc.getWindow() == null || !bind.isSet()) return false;
        long window = mc.getWindow().getHandle();
        return bind.type == Bind.Type.KEY
            ? GLFW.glfwGetKey(window, bind.code) == GLFW.GLFW_PRESS
            : GLFW.glfwGetMouseButton(window, bind.code) == GLFW.GLFW_PRESS;
    }

    /** FOV multiplier for this frame (1 = no zoom). Safe to call several times per frame. */
    private float factor() {
        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0f : Math.min(0.1f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;
        float target = held(mc) ? 1f / level.floatValue() : 1f;
        cur += (target - cur) * (1f - (float) Math.exp(-dt * speed.floatValue()));
        if (target == 1f && Math.abs(cur - 1f) < 0.002f) cur = 1f;
        return cur;
    }

    public static float apply(float fov) {
        Zoom z = Modules.ZOOM;
        if (z == null || !z.isEnabled()) return fov;
        return fov * z.factor();
    }
}
