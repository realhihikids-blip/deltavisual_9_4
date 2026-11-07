package dev.deltavisuals.core;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bind capture (used by the GUI) and in-game bind firing.
 *
 * Capture is event driven (Screen.keyPressed / mouseClicked), firing uses edge detection on GLFW state
 * and is polled both every tick and every rendered frame so short taps are not lost.
 */
public final class BindManager {
    private BindManager() {}

    /** Module currently waiting for a key / button, or null. */
    private static Module listening;
    private static long listenStart;
    private static String lastResult = "";
    private static final Map<Integer, Boolean> prev = new HashMap<>();

    public static Module listening() { return listening; }
    public static String lastResult() { return lastResult; }

    public static void startListening(Module m) {
        listening = m;
        listenStart = System.currentTimeMillis();
        lastResult = "";
    }

    public static void cancel() { listening = null; }

    /** @return true if the event was consumed by bind capture. */
    public static boolean captureKey(int key) {
        Module m = listening;
        if (m == null) return false;
        if (System.currentTimeMillis() - listenStart < 80) return true; // swallow echo of the opening event
        if (key == GLFW.GLFW_KEY_ESCAPE) { listening = null; lastResult = "Cancelled"; return true; }
        if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
            m.setBind(Bind.NONE);
            listening = null; lastResult = "Bind cleared";
            return true;
        }
        assign(m, Bind.key(key));
        return true;
    }

    /** @return true if the event was consumed by bind capture. */
    public static boolean captureMouse(int button) {
        Module m = listening;
        if (m == null) return false;
        if (System.currentTimeMillis() - listenStart < 80) return true; // opening click must not bind itself
        assign(m, Bind.mouse(button));
        return true;
    }

    private static void assign(Module m, Bind b) {
        m.setBind(b);
        listening = null;
        List<Module> c = conflicts(m, b);
        lastResult = c.isEmpty() ? "Bound to " + b.label() : "Bound to " + b.label() + " (shared with " + c.size() + " other)";
    }

    public static List<Module> conflicts(Module m, Bind b) {
        List<Module> out = new ArrayList<>();
        if (!b.isSet()) return out;
        for (Module o : Modules.all()) if (o != m && o.bind.equals(b)) out.add(o);
        return out;
    }

    private static boolean down(long window, Bind b) {
        return b.type == Bind.Type.KEY
            ? GLFW.glfwGetKey(window, b.code) == GLFW.GLFW_PRESS
            : GLFW.glfwGetMouseButton(window, b.code) == GLFW.GLFW_PRESS;
    }

    private static final Map<Integer, Boolean> cur = new HashMap<>();
    private static final Map<Integer, Boolean> fresh = new HashMap<>();

    /** Edge-detects every bound key and fires modules on a fresh press. Safe to call many times per frame. */
    public static void poll(MinecraftClient mc) {
        if (mc == null || mc.getWindow() == null) return;
        long window = mc.getWindow().getHandle();
        Screen s = mc.currentScreen;
        boolean inv = s instanceof InventoryScreen;
        boolean freeScreen = s == null;

        // pass 1: sample each distinct bound key once, so keys shared by several modules fire all of them
        cur.clear();
        fresh.clear();
        for (Module m : Modules.all()) {
            Bind b = m.bind;
            if (!b.isSet()) continue;
            int k = b.hashCode();
            if (cur.containsKey(k)) continue;
            boolean now = down(window, b);
            cur.put(k, now);
            fresh.put(k, now && !prev.getOrDefault(k, false));
        }
        prev.putAll(cur);

        if (listening != null || mc.player == null) return;

        // pass 2: fire
        for (Module m : Modules.all()) {
            Bind b = m.bind;
            if (!b.isSet() || !fresh.getOrDefault(b.hashCode(), false)) continue;
            if (!(freeScreen || (inv && m.allowInInventory))) continue;
            m.triggerBind(mc);
        }
    }
}
