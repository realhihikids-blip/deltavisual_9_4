package dev.deltavisuals.render;

import dev.deltavisuals.util.ColorUtil;
import dev.deltavisuals.util.Hash;

/**
 * Low-poly solid models built from quads, drawn through {@link Xf} (which must be set first).
 * All models fit roughly into a unit cube around the local origin. Colours are ARGB; the alpha of the
 * colour you pass is the alpha of the whole model. Everything is plain geometry: no textures, no particles.
 */
public final class Models {
    private static final double TAU = Math.PI * 2;

    // ---- precomputed outlines
    private static final int STAR_N = 10;
    private static final double[] STAR_X = new double[STAR_N], STAR_Y = new double[STAR_N];
    private static final int HEART_N = 22;
    private static final double[] HEART_X = new double[HEART_N], HEART_Y = new double[HEART_N];

    static {
        for (int k = 0; k < STAR_N; k++) {
            double a = -Math.PI / 2 + k * Math.PI / 5, r = (k % 2 == 0) ? 1.0 : 0.47;
            STAR_X[k] = Math.cos(a) * r; STAR_Y[k] = Math.sin(a) * r;
        }
        for (int k = 0; k < HEART_N; k++) {
            double t = TAU * k / HEART_N;
            double s = Math.sin(t);
            HEART_X[k] = 16 * s * s * s / 17.0;
            HEART_Y[k] = (13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t)) / 17.0;
        }
    }

    private Models() {}

    /** Moves the RGB of c towards white by t (0..1), keeps alpha. */
    public static int tone(int c, float t) {
        int w = ColorUtil.lerp(c, 0xFFFFFFFF, t);
        return ColorUtil.withAlpha(w, ColorUtil.alpha(c));
    }

    /** Multiplies RGB, keeps alpha. */
    public static int dark(int c, float k) { return Xf.lit(c, k); }

    // ------------------------------------------------------------------ 5-point star (faceted, 20 faces)

    public static void star(int c) {
        Xf.center(0, 0, 0);
        final double d = 0.30;
        int cc = tone(c, 0.55f);
        for (int k = 0; k < STAR_N; k++) {
            int j = (k + 1) % STAR_N;
            int ck = (k % 2 == 0) ? c : dark(c, 0.82f), cj = (j % 2 == 0) ? c : dark(c, 0.82f);
            Xf.tri(0, 0, d, cc, STAR_X[k], STAR_Y[k], 0, ck, STAR_X[j], STAR_Y[j], 0, cj);
            Xf.tri(0, 0, -d, cc, STAR_X[j], STAR_Y[j], 0, cj, STAR_X[k], STAR_Y[k], 0, ck);
        }
    }

    // ------------------------------------------------------------------ puffy 3D heart

    private static final double[] HEART_S = {1.0, 0.80, 0.54, 0.26, 0.0};
    private static final double HEART_D = 0.46, HEART_CY = 0.05;

    /** Heart, 5 layers per side. detail 1 = all 22 outline points, 2 = every second one (cheaper). */
    public static void heart(int c, int detail) {
        Xf.center(0, HEART_CY, 0);
        int step = Math.max(1, Math.min(2, detail));
        int n = HEART_N / step;
        int layers = HEART_S.length - 1;
        for (int side = 0; side < 2; side++) {
            double sgn = side == 0 ? 1 : -1;
            for (int l = 0; l < layers; l++) {
                double s0 = HEART_S[l], s1 = HEART_S[l + 1];
                double z0 = sgn * HEART_D * Math.sqrt(Math.max(0, 1 - s0 * s0)), z1 = sgn * HEART_D * Math.sqrt(Math.max(0, 1 - s1 * s1));
                int ca = tone(c, (float) (0.5 * (1 - s0))), cb = tone(c, (float) (0.5 * (1 - s1)));
                for (int k = 0; k < n; k++) {
                    int i = k * step, j = ((k + 1) % n) * step;
                    double ax = HEART_X[i] * s0, ay = HEART_CY + (HEART_Y[i] - HEART_CY) * s0;
                    double bx = HEART_X[j] * s0, by = HEART_CY + (HEART_Y[j] - HEART_CY) * s0;
                    double cx = HEART_X[j] * s1, cy = HEART_CY + (HEART_Y[j] - HEART_CY) * s1;
                    double dx = HEART_X[i] * s1, dy = HEART_CY + (HEART_Y[i] - HEART_CY) * s1;
                    Xf.quad(ax, ay, z0, ca, bx, by, z0, ca, cx, cy, z1, cb, dx, dy, z1, cb);
                }
            }
        }
    }

    // ------------------------------------------------------------------ crystal (bipyramid with a waist)

    /**
     * @param sides   4..8
     * @param ringR   radius of the waist rings
     * @param ringY   half height of the straight part
     * @param top     y of the upper tip, bottom = y of the lower tip (positive number, tip is at -bottom)
     */
    public static void crystal(int sides, double ringR, double ringY, double top, double bottom, int seed, int c) {
        Xf.center(0, 0, 0);
        sides = Math.max(3, Math.min(8, sides));
        double[] rx = CR_X, rz = CR_Z;
        for (int k = 0; k < sides; k++) {
            double a = TAU * k / sides + 0.3, r = ringR * (1.0 + 0.16 * Hash.s(seed, k + 3));
            rx[k] = Math.cos(a) * r; rz[k] = Math.sin(a) * r;
        }
        int tip = tone(c, 0.65f), edge = c, low = dark(c, 0.78f);
        for (int k = 0; k < sides; k++) {
            int j = (k + 1) % sides;
            Xf.tri(0, top, 0, tip, rx[k], ringY, rz[k], edge, rx[j], ringY, rz[j], edge);
            Xf.quad(rx[k], ringY, rz[k], edge, rx[k], -ringY, rz[k], low, rx[j], -ringY, rz[j], low, rx[j], ringY, rz[j], edge);
            Xf.tri(0, -bottom, 0, dark(c, 0.6f), rx[j], -ringY, rz[j], low, rx[k], -ringY, rz[k], low);
        }
    }
    private static final double[] CR_X = new double[8], CR_Z = new double[8];

    // ------------------------------------------------------------------ skull (boxes with bevels, ~30 faces)

    /** Face front z at height y (used to glue the eye / nose / teeth overlays onto the sloped front). */
    private static double skullFront(double y) {
        if (y < 0) return 0.36 + 0.06 * (y + 0.42) / 0.42;
        if (y < 0.5) return 0.42 + 0.04 * (y / 0.5);
        return 0.46 - 0.22 * Math.min(1.0, (y - 0.5) / 0.45);
    }

    private static void frust(double y0, double a0, double b0, double c0, double y1, double a1, double b1, double c1, int cBot, int cTop) {
        double zb0 = c0 - b0, zb1 = c0 + b0, zt0 = c1 - b1, zt1 = c1 + b1;
        // front, back, left, right, top, bottom
        Xf.quad(-a0, y0, zb1, cBot, a0, y0, zb1, cBot, a1, y1, zt1, cTop, -a1, y1, zt1, cTop);
        Xf.quad(a0, y0, zb0, cBot, -a0, y0, zb0, cBot, -a1, y1, zt0, cTop, a1, y1, zt0, cTop);
        Xf.quad(-a0, y0, zb0, cBot, -a0, y0, zb1, cBot, -a1, y1, zt1, cTop, -a1, y1, zt0, cTop);
        Xf.quad(a0, y0, zb1, cBot, a0, y0, zb0, cBot, a1, y1, zt0, cTop, a1, y1, zt1, cTop);
        Xf.quad(-a1, y1, zt1, cTop, a1, y1, zt1, cTop, a1, y1, zt0, cTop, -a1, y1, zt0, cTop);
        Xf.quad(-a0, y0, zb0, cBot, a0, y0, zb0, cBot, a0, y0, zb1, cBot, -a0, y0, zb1, cBot);
    }

    private static void onFront(double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4, int c) {
        final double o = 0.007;
        Xf.quad(x1, y1, skullFront(y1) + o, c, x2, y2, skullFront(y2) + o, c, x3, y3, skullFront(y3) + o, c, x4, y4, skullFront(y4) + o, c);
    }

    /** Skull model; face points to +Z. cBone = bone colour (alpha = model alpha). */
    public static void skull(int cBone) {
        Xf.center(0, 0.28, 0);
        int top = tone(cBone, 0.18f), bot = dark(cBone, 0.78f);
        frust(0.0, 0.46, 0.42, 0.0, 0.5, 0.50, 0.46, 0.0, bot, cBone);
        frust(0.5, 0.50, 0.46, 0.0, 0.95, 0.30, 0.27, -0.03, cBone, top);
        frust(-0.42, 0.27, 0.23, 0.12, 0.0, 0.40, 0.36, 0.06, dark(cBone, 0.7f), cBone);
        int dk = ColorUtil.withAlpha(0x120E18, ColorUtil.alpha(cBone));
        // eye sockets
        onFront(-0.39, 0.13, -0.10, 0.09, -0.12, 0.38, -0.36, 0.43, dk);
        onFront(0.10, 0.09, 0.39, 0.13, 0.36, 0.43, 0.12, 0.38, dk);
        // nose
        onFront(0.0, 0.07, -0.075, -0.07, 0.075, -0.07, 0.0, 0.07, dk);
        // teeth
        int td = ColorUtil.withAlpha(0x2A2430, ColorUtil.alpha(cBone));
        for (int i = -2; i <= 2; i++) {
            double x = i * 0.085;
            onFront(x - 0.008, -0.39, x + 0.008, -0.39, x + 0.008, -0.13, x - 0.008, -0.13, td);
        }
        onFront(-0.19, -0.255, 0.19, -0.255, 0.19, -0.238, -0.19, -0.238, td);
    }

    /** Additive eye glow for the skull; call with the same Xf transform, in the additive layer. */
    public static void skullEyes(int cGlow, double sizeMul) {
        Xf.cull = true; Xf.shade = false;
        for (int s = -1; s <= 1; s += 2) {
            double cx = s * 0.235, cy = 0.25;
            double x = Xf.wx(cx, cy, skullFront(cy) + 0.02), y = Xf.wy(cx, cy, skullFront(cy) + 0.02), z = Xf.wz(cx, cy, skullFront(cy) + 0.02);
            double f = Xf.facing(0, 0, 1, x, y, z);                 // only while the face looks at the camera
            if (f <= 0.12) continue;
            int cc = ColorUtil.withAlpha(cGlow, Math.round(ColorUtil.alpha(cGlow) * (float) Math.min(1.0, (f - 0.12) * 3.0)));
            glow(x, y, z, 0.30 * Xf.scale() * sizeMul, cc);
        }
        Xf.shade = true;
    }

    // ------------------------------------------------------------------ billboards (camera facing, additive)

    /** Soft round glow. Alpha of c = intensity in the centre. */
    public static void glow(double cx, double cy, double cz, double r, int c) {
        if (ColorUtil.alpha(c) < 2 || r <= 0) return;
        final int a = ColorUtil.alpha(c);
        int cIn = ColorUtil.withAlpha(c, Math.round(a * 0.30f)), cOut = ColorUtil.withAlpha(c, 0);
        double[] ix = HX_I, iy = HY_I, iz = HZ_I, ox_ = HX_O, oy_ = HY_O, oz_ = HZ_O;
        for (int k = 0; k < 6; k++) {
            double ang = k * Math.PI / 3 + 0.26, ca = Math.cos(ang), sa = Math.sin(ang);
            double dx = Gfx.rightX * ca + Gfx.upX * sa, dy = Gfx.rightY * ca + Gfx.upY * sa, dz = Gfx.rightZ * ca + Gfx.upZ * sa;
            ix[k] = cx + dx * r * 0.5; iy[k] = cy + dy * r * 0.5; iz[k] = cz + dz * r * 0.5;
            ox_[k] = cx + dx * r; oy_[k] = cy + dy * r; oz_[k] = cz + dz * r;
        }
        for (int k = 0; k < 6; k += 2) {
            int k1 = (k + 1) % 6, k2 = (k + 2) % 6;
            Gfx.quad(cx, cy, cz, c, ix[k], iy[k], iz[k], cIn, ix[k1], iy[k1], iz[k1], cIn, ix[k2], iy[k2], iz[k2], cIn);
        }
        for (int k = 0; k < 6; k++) {
            int j = (k + 1) % 6;
            Gfx.quad(ix[k], iy[k], iz[k], cIn, ox_[k], oy_[k], oz_[k], cOut, ox_[j], oy_[j], oz_[j], cOut, ix[j], iy[j], iz[j], cIn);
        }
    }
    private static final double[] HX_I = new double[6], HY_I = new double[6], HZ_I = new double[6];
    private static final double[] HX_O = new double[6], HY_O = new double[6], HZ_O = new double[6];

    /** Four-point sparkle (long thin spikes) facing the camera. */
    public static void sparkle(double cx, double cy, double cz, double r, double rot, int c) {
        if (ColorUtil.alpha(c) < 2 || r <= 0) return;
        int cm = ColorUtil.withAlpha(c, Math.round(ColorUtil.alpha(c) * 0.22f)), c0 = ColorUtil.withAlpha(c, 0);
        double[] tx = SP_X, ty = SP_Y, tz = SP_Z, dx = SP_DX, dy = SP_DY, dz = SP_DZ;
        for (int k = 0; k < 4; k++) {
            double a = rot + k * Math.PI / 2, ca = Math.cos(a), sa = Math.sin(a);
            tx[k] = cx + (Gfx.rightX * ca + Gfx.upX * sa) * r; ty[k] = cy + (Gfx.rightY * ca + Gfx.upY * sa) * r; tz[k] = cz + (Gfx.rightZ * ca + Gfx.upZ * sa) * r;
            double b = a + Math.PI / 4, cb = Math.cos(b), sb = Math.sin(b);
            dx[k] = cx + (Gfx.rightX * cb + Gfx.upX * sb) * r * 0.17; dy[k] = cy + (Gfx.rightY * cb + Gfx.upY * sb) * r * 0.17; dz[k] = cz + (Gfx.rightZ * cb + Gfx.upZ * sb) * r * 0.17;
        }
        for (int k = 0; k < 4; k++) {
            int j = (k + 1) % 4;
            Gfx.quad(cx, cy, cz, c, tx[k], ty[k], tz[k], c0, dx[k], dy[k], dz[k], cm, tx[j], ty[j], tz[j], c0);
        }
    }
    private static final double[] SP_X = new double[4], SP_Y = new double[4], SP_Z = new double[4];
    private static final double[] SP_DX = new double[4], SP_DY = new double[4], SP_DZ = new double[4];
}
