package dev.deltavisuals.util;

/** Reusable colour source for 3D effects. One instance per module, filled each frame (no allocation). */
public final class Palette {
    public static final String[] MODES = {"Static", "Gradient", "Rainbow", "Health", "Fire", "Neon"};
    /** Modes that do not need an entity (BlockOverlay, Trails). */
    public static final String[] PLAIN = {"Static", "Gradient", "Rainbow", "Fire", "Neon"};
    private static final double TAU = Math.PI * 2;

    public String mode = "Static";
    public int c1 = 0x33CCFF, c2 = 0xFF4DA6;
    public double speed = 1;
    /** 0..1, used by the Health mode. */
    public float health = 1f;
    /** Running time in ticks (with partial tick). */
    public double time;

    public Palette set(String mode, int rgb1, int rgb2, double speed, double time) {
        this.mode = mode; this.c1 = rgb1 & 0xFFFFFF; this.c2 = rgb2 & 0xFFFFFF;
        this.speed = speed; this.time = time;
        return this;
    }

    /** @return opaque RGB (alpha bits = 0xFF) for the given phase (0..1 along a ribbon, or any offset). */
    public int at(double phase) {
        double t = time * 0.05 * speed;
        switch (mode) {
            case "Gradient": {
                double w = 0.5 + 0.5 * Math.sin((t * 0.5 + phase) * TAU);
                return ColorUtil.lerp(0xFF000000 | c1, 0xFF000000 | c2, (float) w);
            }
            case "Rainbow":
                return ColorUtil.hsv((float) (t * 0.2 + phase), 0.85f, 1f, 255);
            case "Health": {
                float h = Math.max(0f, Math.min(1f, health));
                return ColorUtil.lerp(0xFFFF2A2A, 0xFF35FF6A, h);
            }
            case "Fire": {
                double f = phase + 0.12 * Math.sin(time * 0.35 * speed + phase * 9);
                return ColorUtil.lerp(0xFFFFF0A0, 0xFFFF2E00, (float) Math.max(0, Math.min(1, f)));
            }
            case "Neon": {
                double w = 0.5 + 0.5 * Math.sin(t * 2 + phase * 3);
                return ColorUtil.lerp(0xFF000000 | c1, 0xFF000000 | c2, (float) w);
            }
            default:
                return 0xFF000000 | c1;
        }
    }

    /** Colour with alpha 0..1 applied. */
    public int at(double phase, float alpha) {
        return ColorUtil.withAlpha(at(phase), Math.round(255f * Math.max(0f, Math.min(1f, alpha))));
    }
}
