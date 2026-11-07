package dev.deltavisuals.util;

/** Colour source shared by the new modules: static colour, two-colour gradient or rainbow (chroma). */
public final class Chroma {
    public static final String[] MODES = {"Static", "Gradient", "Rainbow"};
    private static final double TAU = Math.PI * 2;

    private Chroma() {}

    /**
     * @param mode   "Static" / "Gradient" / "Rainbow"
     * @param speed  colour flow speed multiplier
     * @param phase  offset 0..1 so different particles / segments get different hues
     * @param ticks  running tick counter of the module
     * @return RGB (no alpha)
     */
    public static int pick(String mode, int rgb1, int rgb2, double speed, double phase, long ticks) {
        double t = ticks * 0.05 * speed;
        switch (mode) {
            case "Rainbow":
                return ColorUtil.hsv((float) (t * 0.2 + phase), 0.85f, 1f, 255) & 0xFFFFFF;
            case "Gradient": {
                double w = 0.5 + 0.5 * Math.sin((t * 0.5 + phase) * TAU);
                return ColorUtil.lerp(0xFF000000 | (rgb1 & 0xFFFFFF), 0xFF000000 | (rgb2 & 0xFFFFFF), (float) w) & 0xFFFFFF;
            }
            default:
                return rgb1 & 0xFFFFFF;
        }
    }
}
