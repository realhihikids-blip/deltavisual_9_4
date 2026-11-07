package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Setting;

/** Colours, fonts (size / weight / colour / shadow), opacity, scale and animation switches for the whole UI. */
public final class ThemeModule extends Module {
    private static final String[] PRESETS = {"Violet", "Ocean", "Emerald", "Sunset", "Rose", "Mono", "Custom"};
    // accent, accent2, panel
    private static final int[][] COLORS = {
        {0xFF8B5CF6, 0xFF4C2A9E, 0xFF0E0A18},
        {0xFF38BDF8, 0xFF1D4ED8, 0xFF08111F},
        {0xFF34D399, 0xFF047857, 0xFF07130F},
        {0xFFFB923C, 0xFFDC2626, 0xFF180B0B},
        {0xFFF472B6, 0xFF9D174D, 0xFF160A12},
        {0xFFE5E7EB, 0xFF6B7280, 0xFF0B0B0D},
    };

    public final Setting.Mode preset, language;
    public final Setting.Color accent, accent2, panel, selection, text;
    public final Setting.Num opacity, textOpacity, textSize, scale;
    public final Setting.Bool bold, shadow, gradient, animations, uiFont;
    private boolean applying;

    public ThemeModule() {
        super("Theme", "Colors, fonts, scale, opacity and animations of the interface", Category.INTERFACE, true);
        alwaysOn = true;
        language = add(new Setting.Mode("Language", new String[]{"English", "Русский"}, 0));
        preset = add(new Setting.Mode("Preset", PRESETS, 0));
        accent = add(new Setting.Color("Accent", COLORS[0][0], false));
        accent2 = add(new Setting.Color("Accent 2", COLORS[0][1], false));
        panel = add(new Setting.Color("Panel Color", COLORS[0][2], false));
        selection = add(new Setting.Color("Selection", COLORS[0][0], false));
        opacity = add(new Setting.Num("Panel Opacity", 30, 100, 1, 90, "%"));
        gradient = add(new Setting.Bool("Gradients", true));
        scale = add(new Setting.Num("UI Scale", 0.7, 1.5, 0.05, 1.0, "x"));
        animations = add(new Setting.Bool("Animations", true));
        textSize = add(new Setting.Num("Text Size", 0.8, 1.3, 0.05, 1.0, "x"));
        bold = add(new Setting.Bool("Bold Text", false));
        shadow = add(new Setting.Bool("Text Shadow", true));
        text = add(new Setting.Color("Text Color", 0xFFEAE6F5, false));
        textOpacity = add(new Setting.Num("Text Opacity", 40, 100, 1, 100, "%"));
        uiFont = add(new Setting.Bool("UI Font", true));
        add(new Setting.Info("Fonts", () -> uiFont.get() ? "Carlito (open licence), supports Cyrillic" : "Vanilla Minecraft font"));
        add(new Setting.Action("Reset Colors", this::resetColors));
        add(new Setting.Action("Reset Whole Theme", this::resetAllTheme));

        preset.onChange = this::applyPreset;
        Runnable custom = () -> { if (!applying) { applying = true; preset.set(PRESETS.length - 1); applying = false; } };
        accent.onChange = custom;
        accent2.onChange = custom;
        panel.onChange = custom;
    }

    private void applyPreset() {
        if (applying) return;
        int i = preset.index();
        if (i >= COLORS.length) return; // Custom keeps current colours
        applying = true;
        accent.set(COLORS[i][0]);
        accent2.set(COLORS[i][1]);
        panel.set(COLORS[i][2]);
        selection.set(COLORS[i][0]);
        applying = false;
    }

    private void resetColors() {
        applying = true;
        preset.set(0);
        accent.set(COLORS[0][0]);
        accent2.set(COLORS[0][1]);
        panel.set(COLORS[0][2]);
        selection.set(COLORS[0][0]);
        text.set(0xFFEAE6F5);
        applying = false;
    }

    private void resetAllTheme() {
        resetColors();
        opacity.set(opacity.def);
        gradient.set(true);
        scale.set(scale.def);
        animations.set(true);
        textSize.set(textSize.def);
        bold.set(false);
        shadow.set(true);
        uiFont.set(true);
        textOpacity.set(textOpacity.def);
    }
}
