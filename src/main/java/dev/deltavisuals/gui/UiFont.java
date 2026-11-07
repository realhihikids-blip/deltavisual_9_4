package dev.deltavisuals.gui;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Gives interface text the mod font (assets/delta_visuals/font/ui.json: a TrueType font with the vanilla font as
 * fallback, so a missing glyph or a missing file never makes text disappear).
 *
 * The way a font is attached to a text Style changed between Minecraft versions (an Identifier before, a font source
 * object in newer ones). To compile and work on any of them, the right Style method is looked up once by its parameter
 * TYPES (never by name). If nothing fits, the text simply keeps the vanilla font.
 */
public final class UiFont {
    private static final Identifier FONT_ID = Identifier.of("delta_visuals", "ui");
    private static boolean resolved;
    private static Method method;
    private static Object arg;

    private UiFont() {}

    private static void resolve() {
        resolved = true;
        try {
            for (Method c : Style.class.getMethods()) {
                if (c.getReturnType() != Style.class || c.getParameterCount() != 1 || Modifier.isStatic(c.getModifiers())) continue;
                if (c.getParameterTypes()[0] == Identifier.class) { method = c; arg = FONT_ID; return; }
            }
            for (Method c : Style.class.getMethods()) {
                if (c.getReturnType() != Style.class || c.getParameterCount() != 1 || Modifier.isStatic(c.getModifiers())) continue;
                Class<?> p = c.getParameterTypes()[0];
                if (p.isPrimitive() || p == String.class || p.isEnum()) continue;
                Class<?>[] subs = p.isSealed() ? p.getPermittedSubclasses() : p.getDeclaredClasses();
                if (subs == null) continue;
                for (Class<?> s : subs) {
                    try {
                        Constructor<?> k = s.getConstructor(Identifier.class);
                        arg = k.newInstance(FONT_ID);
                        method = c;
                        return;
                    } catch (ReflectiveOperationException ignored) { }
                }
            }
        } catch (Throwable ignored) { }
    }

    public static MutableText apply(MutableText t) {
        if (!Theme.uiFont()) return t;
        if (!resolved) resolve();
        if (method == null) return t;
        try {
            Style st = (Style) method.invoke(t.getStyle(), arg);
            t.setStyle(st);
        } catch (Throwable e) {
            method = null;
        }
        return t;
    }

    public static Text text(String s) { return apply(Text.literal(s)); }
}
