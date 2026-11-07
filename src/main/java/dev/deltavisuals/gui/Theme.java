package dev.deltavisuals.gui;

import dev.deltavisuals.core.Modules;
import dev.deltavisuals.feature.ThemeModule;
import dev.deltavisuals.util.ColorUtil;

/** Read-only view over the Theme module used by all drawing code. */
public final class Theme {
    private Theme() {}

    private static ThemeModule t() { return Modules.THEME; }

    public static float scale() { return t().scale.floatValue(); }
    public static boolean animations() { return t().animations.get(); }
    public static boolean gradient() { return t().gradient.get(); }
    public static float textSize() { return t().textSize.floatValue(); }
    public static boolean bold() { return t().bold.get(); }
    public static boolean shadow() { return t().shadow.get(); }
    public static boolean uiFont() { return t() != null && t().uiFont.get(); }

    public static int accent() { return t().accent.get(); }
    public static int accent2() { return t().accent2.get(); }
    public static int selection() { return t().selection.get(); }
    public static int text() { return ColorUtil.mulAlpha(t().text.get(), t().textOpacity.floatValue() / 100f); }
    public static int dim() { return ColorUtil.mulAlpha(text(), 0.55f); }

    private static int base() { return t().panel.get() | 0xFF000000; }
    private static int alphaOf(float k) { return Math.round(255f * t().opacity.floatValue() / 100f * k); }

    public static int panel() { return ColorUtil.withAlpha(base(), alphaOf(1f)); }
    public static int sidebar() { return ColorUtil.withAlpha(ColorUtil.lerp(base(), 0xFF000000, 0.38f), alphaOf(0.95f)); }
    public static int card() { return ColorUtil.withAlpha(ColorUtil.lerp(base(), 0xFFFFFFFF, 0.07f), alphaOf(0.9f)); }
    public static int cardHover() { return ColorUtil.withAlpha(ColorUtil.lerp(ColorUtil.lerp(base(), 0xFFFFFFFF, 0.07f), accent() | 0xFF000000, 0.22f), alphaOf(0.95f)); }
    public static int field() { return ColorUtil.withAlpha(ColorUtil.lerp(base(), 0xFF000000, 0.25f), alphaOf(1f)); }
}
