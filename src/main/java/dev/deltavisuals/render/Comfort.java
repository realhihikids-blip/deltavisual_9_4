package dev.deltavisuals.render;

/**
 * PvP comfort for decorative effects: a 0..1 factor that makes a point of an effect transparent when it is
 * right next to the camera (so it never covers the screen) and, optionally, when it is close to the crosshair
 * (so it never hides the thing you are aiming at). Uses the camera basis stored by {@link Gfx#begin}.
 */
public final class Comfort {
    private Comfort() {}

    private static double clamp(double v, double lo, double hi) { return v < lo ? lo : Math.min(v, hi); }

    /**
     * @param nearStart distance (m) below which the effect is fully invisible
     * @param nearLen   distance (m) over which it fades in after nearStart
     * @param aim       also fade near the crosshair
     * @param clearDeg  angular radius (deg) around the crosshair that is cleared when aim is on
     * @param size      rough radius (m) of the effect, so its edge (not its centre) is used for the aim test
     */
    public static float factor(double x, double y, double z, double nearStart, double nearLen,
                               boolean aim, double clearDeg, double size) {
        double dx = x - Gfx.camX, dy = y - Gfx.camY, dz = z - Gfx.camZ;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1e-4) return 0f;
        double f = clamp((d - nearStart) / Math.max(0.05, nearLen), 0, 1);
        f = f * f * (3 - 2 * f);
        if (aim && f > 0) {
            double cos = (dx * Gfx.lookX + dy * Gfx.lookY + dz * Gfx.lookZ) / d;
            double ang = Math.acos(clamp(cos, -1, 1));
            ang = Math.max(0, ang - Math.atan2(size, d));
            double lim = Math.toRadians(clearDeg);
            double t = clamp((ang - lim * 0.5) / Math.max(1e-4, lim * 0.9), 0, 1);
            f *= 0.10 + 0.90 * (t * t * (3 - 2 * t));
        }
        return (float) f;
    }
}
