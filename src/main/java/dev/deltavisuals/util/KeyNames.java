package dev.deltavisuals.util;

import org.lwjgl.glfw.GLFW;

public final class KeyNames {
    private KeyNames() {}

    public static String key(int code) {
        switch (code) {
            case GLFW.GLFW_KEY_SPACE: return "Space";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "L-Shift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "R-Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "L-Ctrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "R-Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT: return "L-Alt";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "R-Alt";
            case GLFW.GLFW_KEY_TAB: return "Tab";
            case GLFW.GLFW_KEY_ENTER: return "Enter";
            case GLFW.GLFW_KEY_BACKSPACE: return "Backspace";
            case GLFW.GLFW_KEY_DELETE: return "Delete";
            case GLFW.GLFW_KEY_INSERT: return "Insert";
            case GLFW.GLFW_KEY_HOME: return "Home";
            case GLFW.GLFW_KEY_END: return "End";
            case GLFW.GLFW_KEY_PAGE_UP: return "Page Up";
            case GLFW.GLFW_KEY_PAGE_DOWN: return "Page Down";
            case GLFW.GLFW_KEY_UP: return "Up";
            case GLFW.GLFW_KEY_DOWN: return "Down";
            case GLFW.GLFW_KEY_LEFT: return "Left";
            case GLFW.GLFW_KEY_RIGHT: return "Right";
            case GLFW.GLFW_KEY_CAPS_LOCK: return "Caps Lock";
            case GLFW.GLFW_KEY_ESCAPE: return "Esc";
            case GLFW.GLFW_KEY_GRAVE_ACCENT: return "`";
            default:
                if (code >= GLFW.GLFW_KEY_F1 && code <= GLFW.GLFW_KEY_F25) return "F" + (code - GLFW.GLFW_KEY_F1 + 1);
                String n = null;
                try { n = GLFW.glfwGetKeyName(code, 0); } catch (Throwable ignored) { }
                if (n != null && !n.isEmpty()) return n.toUpperCase();
                return "Key " + code;
        }
    }

    public static String mouse(int b) {
        switch (b) {
            case 0: return "Mouse 1 (LMB)";
            case 1: return "Mouse 2 (RMB)";
            case 2: return "Mouse 3 (MMB)";
            default: return "Mouse " + (b + 1);
        }
    }
}
