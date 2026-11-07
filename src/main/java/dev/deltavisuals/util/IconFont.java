package dev.deltavisuals.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * The Delta Visuals crystal as ONE text character (U+E000) of the bitmap font assets/delta_visuals/font/icons.json,
 * so it can sit inside any Text: the tab list, name tags above heads, chat.
 *
 * Attaching a font to a Style differs between Minecraft versions (an Identifier before, a font source object in newer
 * ones), so the right Style method is looked up once by parameter TYPES, the same way UiFont does it. If nothing fits,
 * a coloured delta sign is used instead, so the marker never disappears and never crashes.
 */
public final class IconFont {
    public static final char GLYPH = '\uE000';
    private static final Identifier FONT_ID = Identifier.of("delta_visuals", "icons");

    private static Text logo;

    private IconFont() {}

    /** Shared, immutable-by-convention logo text. */
    public static Text logo() {
        if (logo == null) logo = build();
        return logo;
    }

    private static Text build() {
        try {
            MutableText t = Text.literal(String.valueOf(GLYPH));
            Style st = withFont(t.getStyle());
            if (st != null) return t.setStyle(st.withColor(Formatting.WHITE)); // white = texture colours untouched
        } catch (Throwable ignored) { }
        return Text.literal("\u0394").formatted(Formatting.LIGHT_PURPLE);
    }

    private static Style withFont(Style base) throws ReflectiveOperationException {
        for (Method c : Style.class.getMethods()) {
            if (c.getReturnType() != Style.class || c.getParameterCount() != 1 || Modifier.isStatic(c.getModifiers())) continue;
            if (c.getParameterTypes()[0] == Identifier.class) return (Style) c.invoke(base, FONT_ID);
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
                    return (Style) c.invoke(base, k.newInstance(FONT_ID));
                } catch (ReflectiveOperationException ignored) { }
            }
        }
        return null;
    }

    /** True if the text already contains the logo (stops a second logo being appended). */
    public static boolean hasLogo(Text t) {
        return t.getString().contains(logo().getString());
    }
}
