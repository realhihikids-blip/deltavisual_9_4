package dev.deltavisuals.render;

import dev.deltavisuals.util.ColorUtil;
import dev.deltavisuals.util.Hash;

/**
 * Cosmetics for the local player: capes, wings and hats. Everything is solid geometry drawn through {@link Gfx}
 * (vertex colour gradients, procedural patterns, no textures, no particles, no entity model changes).
 * All animation is computed from {@link Rig}, which the Cosmetics module fills each frame with interpolated values.
 */
public final class CosmeticModels {
    public static final String[] CAPE_STYLES = {"Classic", "Royal", "Galaxy", "Flame", "Frost"};
    public static final String[] WING_STYLES = {"Angel", "Dragon", "Crystal", "Butterfly"};
    public static final String[] HAT_STYLES = {"Top Hat", "Wizard Hat", "Crown", "Halo", "Cat Ears"};

    /** Default colours per style: primary, secondary. */
    public static final int[][] CAPE_COLORS = {{0xFF2F6FEB, 0xFFE8EEFF}, {0xFFB01E3C, 0xFFFFD25A}, {0xFF3A1C8F, 0xFF7FE8FF}, {0xFFFF5A1F, 0xFFFFD13A}, {0xFF7FD8FF, 0xFFFFFFFF}};
    public static final int[][] WING_COLORS = {{0xFFFFFFFF, 0xFFFFE3A0}, {0xFF8A1030, 0xFFFF6A3D}, {0xFF8F6BFF, 0xFF52F0FF}, {0xFFFF7AC8, 0xFF7A5CFF}};
    public static final int[][] HAT_COLORS = {{0xFF1C1C24, 0xFFB01E3C}, {0xFF5A2BC0, 0xFFFFD25A}, {0xFFFFC83A, 0xFFE0314F}, {0xFFFFE27A, 0xFFFFFFFF}, {0xFF2C2C36, 0xFFFF9EC0}};

    /** Per-frame data about the player. Angles in degrees (Minecraft convention), velocities in blocks per tick. */
    public static final class Rig {
        public double x, y, z;              // feet position
        public double bodyYaw, headYaw, headPitch;
        public double eyeH;                 // eye height above the feet
        public boolean crouch, airborne;
        public double fwd, side, up;        // smoothed velocity in the body frame
        public double time;                 // seconds
        public int alpha = 255;             // 0..255
    }

    private static final double TAU = Math.PI * 2;
    private static final double LX = 0.30, LY = 0.82, LZ = 0.48;
    private static final double LL = Math.sqrt(LX * LX + LY * LY + LZ * LZ);

    // body frame (horizontal): forward, right
    private static double fx, fz, rx, rz;

    private CosmeticModels() {}

    // ------------------------------------------------------------------ small helpers
    private static double clamp(double v, double lo, double hi) { return v < lo ? lo : Math.min(v, hi); }
    private static double[] v3(double x, double y, double z) { return new double[]{x, y, z}; }

    private static int tint(int c, int rgb, float t) {
        int r = Math.round(ColorUtil.red(c) + (ColorUtil.red(rgb) - ColorUtil.red(c)) * t);
        int g = Math.round(ColorUtil.green(c) + (ColorUtil.green(rgb) - ColorUtil.green(c)) * t);
        int b = Math.round(ColorUtil.blue(c) + (ColorUtil.blue(rgb) - ColorUtil.blue(c)) * t);
        return ColorUtil.argb(ColorUtil.alpha(c), r, g, b);
    }
    private static int darker(int c, float t) { return tint(c, 0x000000, t); }
    private static int lighter(int c, float t) { return tint(c, 0xFFFFFF, t); }
    private static int mixc(int a, int b, double t) {
        t = clamp(t, 0, 1);
        return ColorUtil.lerp(a, b, (float) t);
    }
    private static int alphaMul(int c, double k) { return ColorUtil.mulAlpha(c, (float) clamp(k, 0, 1)); }

    private static void frame(Rig r) {
        double y = Math.toRadians(r.bodyYaw);
        fx = -Math.sin(y); fz = Math.cos(y);
        rx = -Math.cos(y); rz = -Math.sin(y);
    }

    /** Lit quad: brightness from the face normal (two sided). */
    private static void quad(double[] a, double[] b, double[] c, double[] d, int ca, int cb, int cc, int cd, boolean lit) {
        if (lit) {
            double e1x = c[0] - a[0], e1y = c[1] - a[1], e1z = c[2] - a[2], e2x = d[0] - b[0], e2y = d[1] - b[1], e2z = d[2] - b[2];
            double nx = e1y * e2z - e1z * e2y, ny = e1z * e2x - e1x * e2z, nz = e1x * e2y - e1y * e2x;
            double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl > 1e-12) {
                double dot = Math.abs((nx * LX + ny * LY + nz * LZ) / (nl * LL));
                float k = (float) (0.58 + 0.52 * dot);
                ca = shade(ca, k); cb = shade(cb, k); cc = shade(cc, k); cd = shade(cd, k);
            }
        }
        Gfx.quad(a[0], a[1], a[2], ca, b[0], b[1], b[2], cb, c[0], c[1], c[2], cc, d[0], d[1], d[2], cd);
    }

    private static int shade(int c, float k) {
        return ColorUtil.argb(ColorUtil.alpha(c), Math.min(255, Math.round(ColorUtil.red(c) * k)),
            Math.min(255, Math.round(ColorUtil.green(c) * k)), Math.min(255, Math.round(ColorUtil.blue(c) * k)));
    }

    private static void tri(double[] a, double[] b, double[] c, int ca, int cb, int cc, boolean lit) { quad(a, b, c, c, ca, cb, cc, cc, lit); }

    // ================================================================== CAPE
    public static void cape(Rig r, int style, int c1, int c2, double lengthMul) {
        frame(r);
        c1 = alphaMul(c1, r.alpha / 255.0); c2 = alphaMul(c2, r.alpha / 255.0);
        final int NY = 14, NX = 8;
        double L = 1.0 * lengthMul, W = 0.54;
        double ay = r.y + (r.crouch ? 1.16 : 1.38), backOff = r.crouch ? 0.02 : 0.16;
        double ax = r.x - fx * backOff, az = r.z - fz * backOff;
        double speed = Math.hypot(r.fwd, r.side);
        double theta = 0.06 + (r.crouch ? 0.28 : 0.0) + clamp(r.fwd * 2.3, -0.12, 0.95) + clamp(-r.up * 1.3, 0, 0.7) - clamp(r.up * 0.6, 0, 0.2);
        double flutter = 0.03 + 0.07 * clamp(speed * 3.5, 0, 1);
        double lat = -clamp(r.side * 3.0, -0.35, 0.35);
        double bx = -fx, bz = -fz;                                    // back direction

        double[][] C = new double[NY + 1][3];
        C[0][0] = ax; C[0][1] = ay; C[0][2] = az;
        double step = L / NY;
        for (int k = 0; k < NY; k++) {
            double v = (k + 0.5) / NY;
            double phi = theta * Math.pow(v, 0.8) + flutter * Math.sin(r.time * 5.0 + v * 6.0) * v;
            C[k + 1][0] = C[k][0] + bx * Math.sin(phi) * step;
            C[k + 1][1] = C[k][1] - Math.cos(phi) * step;
            C[k + 1][2] = C[k][2] + bz * Math.sin(phi) * step;
        }
        double[][][] P = new double[NY + 1][NX + 1][];
        int[][] col = new int[NY + 1][NX + 1];
        for (int k = 0; k <= NY; k++) {
            double v = k / (double) NY, hw = W * 0.5 * (1 + 0.12 * v);
            for (int j = 0; j <= NX; j++) {
                double u = -1 + 2.0 * j / NX;
                double bulge = 0.03 * (1 - u * u) * (0.2 + v) + 0.012 * Math.sin(r.time * 4 + v * 9 + u * 2.5) * v * (0.3 + clamp(speed * 4, 0, 1));
                double x = C[k][0] + rx * (u * hw + lat * v * v) + bx * bulge;
                double z = C[k][2] + rz * (u * hw + lat * v * v) + bz * bulge;
                double y = C[k][1];
                if (style == 3 && k == NY && (j & 1) == 0) y += 0.07;      // flame hem teeth
                P[k][j] = v3(x, y, z);
                col[k][j] = capeColor(style, c1, c2, v, u, r.time, k, j);
            }
        }
        Gfx.use(false, false);
        for (int k = 0; k < NY; k++) {
            for (int j = 0; j < NX; j++) {
                int ca = col[k][j], cb = col[k][j + 1], cc = col[k + 1][j + 1], cd = col[k + 1][j];
                boolean trim = (style == 0 || style == 1) && (j == 0 || j == NX - 1 || k == NY - 1 || (style == 1 && k == 0));
                if (trim) { int t = darker(c2, 0.08f * k / NY); ca = cb = cc = cd = t; }
                else if (style == 2 || style == 4) {
                    float h = Hash.f(k * 31 + 7, j * 17 + 3);
                    double thr = style == 2 ? 0.90 : 0.94;
                    if (h > thr) {
                        double tw = 0.55 + 0.45 * Math.sin(r.time * (1.5 + h * 3) + h * 40);
                        int s = alphaMul(lighter(c2, 0.35f), 0.55 + 0.45 * tw);
                        ca = cb = cc = cd = s;
                    }
                }
                quad(P[k][j], P[k][j + 1], P[k + 1][j + 1], P[k + 1][j], ca, cb, cc, cd, true);
            }
        }
    }

    private static int capeColor(int style, int c1, int c2, double v, double u, double t, int k, int j) {
        switch (style) {
            case 1: {   // Royal: rich red, lighter near the shoulders
                return mixc(lighter(c1, 0.18f), darker(c1, 0.45f), v);
            }
            case 2: {   // Galaxy: deep purple to blue with a drifting nebula
                int base = mixc(c1, tint(c1, 0x08061E, 0.8f), v * 0.85);
                double g = 0.5 + 0.5 * Math.sin(u * 3.1 + v * 4.3 + t * 0.5) * Math.sin(v * 5.7 - u * 2.2 + 1.3);
                return mixc(base, c2, g * 0.28);
            }
            case 3: {   // Flame: orange to yellow, moving
                double tt = clamp(v + 0.22 * Math.sin(t * 3.2 + u * 4 + v * 7), 0, 1);
                int c = mixc(c1, c2, tt);
                return k == 0 ? darker(c, 0.3f) : c;
            }
            case 4: {   // Frost
                return mixc(c1, c2, 0.3 + 0.5 * v + 0.08 * Math.sin(t * 1.5 + u * 3 + v * 5));
            }
            default: {  // Classic
                return mixc(lighter(c1, 0.10f), darker(c1, 0.40f), v);
            }
        }
    }

    // ================================================================== WINGS
    private static double[] wp(double[] origin, double[] o, double x, double y) {
        return v3(origin[0] + o[0] * x, origin[1] + y, origin[2] + o[2] * x);
    }

    public static void wings(Rig r, int style, int c1, int c2, double size, double speed) {
        frame(r);
        c1 = alphaMul(c1, r.alpha / 255.0); c2 = alphaMul(c2, r.alpha / 255.0);
        double ay = r.y + (r.crouch ? 1.08 : 1.30), backOff = r.crouch ? 0.00 : 0.17;
        double bx = -fx, bz = -fz;
        double t = r.time * speed;
        double open = r.airborne ? 0.90 + 0.10 * Math.sin(t * 7.0) : 0.62 + 0.06 * Math.sin(t * 1.6);
        double flap = r.airborne ? Math.sin(t * 7.0) : Math.sin(t * 1.6) * 0.3;
        double psi = 0.52 + 0.10 * flap;
        for (int side = -1; side <= 1; side += 2) {
            double[] origin = v3(r.x - fx * backOff + rx * side * 0.09, ay, r.z - fz * backOff + rz * side * 0.09);
            double[] o = v3(rx * side * Math.cos(psi) + bx * Math.sin(psi), 0, rz * side * Math.cos(psi) + bz * Math.sin(psi));
            switch (style) {
                case 1: dragonWing(origin, o, c1, c2, size, open, flap, t); break;
                case 2: crystalWing(origin, o, c1, c2, size, open, flap, t); break;
                case 3: butterflyWing(origin, o, c1, c2, size, flap, t, r.airborne); break;
                default: angelWing(origin, o, c1, c2, size, open, flap, t); break;
            }
        }
    }

    private static void angelWing(double[] origin, double[] o, int c1, int c2, double size, double open, double flap, double t) {
        Gfx.use(false, false);
        for (int layer = 0; layer < 2; layer++) {
            int n = layer == 0 ? 9 : 8;
            for (int i = 0; i < n; i++) {
                double f = i / (double) (n - 1);
                double a = Math.toRadians(45 + 125 * open * f) + 0.09 * Math.sin(t * 7.0 - f * 1.2) * open * (flap == 0 ? 0 : 1) + (layer == 1 ? 0.05 : 0);
                double len = size * (0.40 + 0.50 * Math.sin(clamp(f, 0, 1) * Math.PI * 0.85)) * (layer == 0 ? 1.0 : 0.58);
                double w = size * 0.17 * (0.85 + 0.35 * f) * (layer == 0 ? 1.0 : 0.85);
                double rootX = 0.04 * size * f, rootY = 0.12 * size * (1 - f);
                int cBase = layer == 0 ? c1 : lighter(c1, 0.35f), cTip = layer == 0 ? c2 : lighter(c2, 0.45f);
                double delta = 0.10 * (0.2 + 0.8 * f);
                double[] p0 = {rootX, rootY};
                double[] p1 = {p0[0] + Math.sin(a) * 0.45 * len, p0[1] + Math.cos(a) * 0.45 * len};
                double[] p2 = {p1[0] + Math.sin(a + delta) * 0.40 * len, p1[1] + Math.cos(a + delta) * 0.40 * len};
                double[] p3 = {p2[0] + Math.sin(a + 2 * delta) * 0.15 * len, p2[1] + Math.cos(a + 2 * delta) * 0.15 * len};
                double[] n0 = {Math.cos(a), -Math.sin(a)}, n1 = {Math.cos(a + delta), -Math.sin(a + delta)};
                double[] b0l = wp(origin, o, p0[0] - n0[0] * 0.02 * size, p0[1] - n0[1] * 0.02 * size), b0r = wp(origin, o, p0[0] + n0[0] * 0.02 * size, p0[1] + n0[1] * 0.02 * size);
                double[] m1l = wp(origin, o, p1[0] - n0[0] * w * 0.5, p1[1] - n0[1] * w * 0.5), m1r = wp(origin, o, p1[0] + n0[0] * w * 0.5, p1[1] + n0[1] * w * 0.5);
                double[] m2l = wp(origin, o, p2[0] - n1[0] * w * 0.40, p2[1] - n1[1] * w * 0.40), m2r = wp(origin, o, p2[0] + n1[0] * w * 0.40, p2[1] + n1[1] * w * 0.40);
                double[] tip = wp(origin, o, p3[0], p3[1]);
                int cMid = mixc(cBase, cTip, 0.5), tipC = alphaMul(cTip, 0.85);
                quad(b0l, b0r, m1r, m1l, cBase, cBase, cMid, cMid, true);
                quad(m1l, m1r, m2r, m2l, cMid, cMid, cTip, cTip, true);
                tri(m2l, m2r, tip, cTip, cTip, tipC, true);
            }
        }
    }

    private static void dragonWing(double[] origin, double[] o, int c1, int c2, double size, double open, double flap, double t) {
        double fl = flap * 0.45;
        double wx = (0.26 + 0.24 * open) * size, wy = (0.28 + 0.26 * open) * size;
        double ca = Math.cos(fl), sa = Math.sin(fl);
        double[] W = {wx * ca - wy * sa, wx * sa + wy * ca};
        double[] targetA = {62, 28, -5, -38}, lens = {0.95, 1.0, 0.88, 0.70};
        double[][] F = new double[4][2];
        for (int k = 0; k < 4; k++) {
            double ang = Math.toRadians(85 + (targetA[k] - 85) * open) + fl;
            F[k][0] = W[0] + Math.cos(ang) * lens[k] * size * (0.40 + 0.40 * open);
            F[k][1] = W[1] + Math.sin(ang) * lens[k] * size * (0.40 + 0.40 * open);
        }
        double[] B = {0.05 * size, -0.55 * size * (0.5 + 0.5 * open)};
        int mem = c1, edge = alphaMul(c2, 0.75), bone = darker(c1, 0.55f);
        Gfx.use(false, false);
        double[] S = wp(origin, o, 0, 0);
        double[] Wp = wp(origin, o, W[0], W[1]);
        for (int k = 0; k < 3; k++) {
            double[] mid = {(F[k][0] + F[k + 1][0]) * 0.5 + (W[0] - (F[k][0] + F[k + 1][0]) * 0.5) * 0.28, (F[k][1] + F[k + 1][1]) * 0.5 + (W[1] - (F[k][1] + F[k + 1][1]) * 0.5) * 0.28};
            double[] Fa = wp(origin, o, F[k][0], F[k][1]), Fb = wp(origin, o, F[k + 1][0], F[k + 1][1]), M = wp(origin, o, mid[0], mid[1]);
            tri(Wp, Fa, M, mem, edge, mixc(mem, edge, 0.6), true);
            tri(Wp, M, Fb, mem, mixc(mem, edge, 0.6), edge, true);
        }
        double[] Fl = wp(origin, o, F[3][0], F[3][1]), Bp = wp(origin, o, B[0], B[1]);
        tri(Wp, Fl, Bp, mem, edge, mixc(mem, edge, 0.4), true);
        tri(S, Wp, Bp, mem, mem, mixc(mem, edge, 0.4), true);
        // bones
        bone(origin, o, 0, 0, W[0], W[1], 0.034 * size, bone);
        for (int k = 0; k < 4; k++) bone(origin, o, W[0], W[1], F[k][0], F[k][1], 0.024 * size, bone);
    }

    private static void bone(double[] origin, double[] o, double x0, double y0, double x1, double y1, double w, int c) {
        double dx = x1 - x0, dy = y1 - y0, l = Math.hypot(dx, dy);
        if (l < 1e-6) return;
        double nx = -dy / l * w * 0.5, ny = dx / l * w * 0.5;
        quad(wp(origin, o, x0 - nx, y0 - ny), wp(origin, o, x0 + nx, y0 + ny), wp(origin, o, x1 + nx * 0.6, y1 + ny * 0.6), wp(origin, o, x1 - nx * 0.6, y1 - ny * 0.6), c, c, c, c, true);
    }

    private static void crystalWing(double[] origin, double[] o, int c1, int c2, double size, double open, double flap, double t) {
        int n = 7;
        for (int pass = 0; pass < 2; pass++) {
            Gfx.use(pass == 1, false);
            for (int i = 0; i < n; i++) {
                double f = i / (double) (n - 1);
                double a = Math.toRadians(30 + 125 * open * f) + 0.07 * Math.sin(t * 6.0 - f * 1.4) * open;
                double len = size * (0.42 + 0.60 * Math.sin(f * Math.PI * 0.9 + 0.15)) * (pass == 1 ? 1.16 : 1.0);
                double w = size * 0.13 * (pass == 1 ? 1.5 : 1.0);
                double bob = 0.02 * size * Math.sin(t * 2.0 + i * 1.7);
                double rootX = 0.03 * size * f, rootY = 0.10 * size * (1 - f) + bob;
                double dx = Math.sin(a), dy = Math.cos(a), px = Math.cos(a), py = -Math.sin(a);
                double[] root = wp(origin, o, rootX, rootY);
                double[] tip = wp(origin, o, rootX + dx * len, rootY + dy * len);
                double[] mL = wp(origin, o, rootX + dx * len * 0.38 - px * w, rootY + dy * len * 0.38 - py * w);
                double[] mR = wp(origin, o, rootX + dx * len * 0.38 + px * w, rootY + dy * len * 0.38 + py * w);
                double[] ridge = wp(origin, o, rootX + dx * len * 0.42, rootY + dy * len * 0.42);
                double flick = 0.82 + 0.18 * Math.sin(t * 3.0 + i * 2.3);
                int light = pass == 1 ? alphaMul(c2, 0.22 * flick) : lighter(mixc(c1, c2, f * 0.6), 0.15f);
                int dark = pass == 1 ? alphaMul(c1, 0.18 * flick) : darker(mixc(c1, c2, f * 0.6), 0.25f);
                int tipC = pass == 1 ? alphaMul(c2, 0.05) : lighter(c2, 0.4f);
                tri(root, mL, tip, dark, light, tipC, pass == 0);
                tri(root, tip, mR, dark, tipC, light, pass == 0);
                tri(root, mL, ridge, dark, light, mixc(light, dark, 0.5), pass == 0);
            }
        }
    }

    private static void butterflyWing(double[] origin, double[] o, int c1, int c2, double size, double flap, double t, boolean airborne) {
        double phase = airborne ? Math.sin(t * 8.0) : Math.sin(t * 2.2);
        double sx = 0.45 + 0.55 * (0.5 + 0.5 * phase);
        Gfx.use(false, false);
        int dark = darker(c1, 0.55f);
        double[][] lobes = {{0.30, 0.24, 0.36, 0.48, 0.55}, {0.22, -0.26, 0.24, 0.34, -0.35}};
        for (int li = 0; li < lobes.length; li++) {
            double cx = lobes[li][0] * size, cy = lobes[li][1] * size, ex = lobes[li][2] * size, ey = lobes[li][3] * size, rot = lobes[li][4];
            double cr = Math.cos(rot), sr = Math.sin(rot);
            int seg = 22;
            double[] root = wp(origin, o, 0.02 * size * sx, 0.04 * size);
            for (int s = 0; s < seg; s++) {
                double a0 = s * TAU / seg, a1 = (s + 1) * TAU / seg;
                double[] p0 = ell(origin, o, cx, cy, ex, ey, cr, sr, a0, sx, 1.0), p1 = ell(origin, o, cx, cy, ex, ey, cr, sr, a1, sx, 1.0);
                double[] q0 = ell(origin, o, cx, cy, ex, ey, cr, sr, a0, sx, 0.90), q1 = ell(origin, o, cx, cy, ex, ey, cr, sr, a1, sx, 0.90);
                double[] s0 = ell(origin, o, cx, cy, ex, ey, cr, sr, a0, sx, 0.46), s1 = ell(origin, o, cx, cy, ex, ey, cr, sr, a1, sx, 0.46);
                double glow = 0.5 + 0.5 * Math.sin(a0 * 2 + t * 1.5);
                int body = mixc(c1, c2, 0.15 + 0.35 * glow);
                tri(root, q0, q1, darker(c1, 0.15f), body, body, true);
                quad(q0, p0, p1, q1, dark, dark, dark, dark, false);
                // lighter spot in the middle of each lobe
                double[] sc = wp(origin, o, cx * sx, cy);
                tri(sc, s0, s1, lighter(c2, 0.35f), c2, c2, false);
            }
        }
    }

    private static double[] ell(double[] origin, double[] o, double cx, double cy, double ex, double ey, double cr, double sr, double a, double sx, double k) {
        double lx = Math.cos(a) * ex * k, ly = Math.sin(a) * ey * k;
        double x = cx + lx * cr - ly * sr, y = cy + lx * sr + ly * cr;
        return wp(origin, o, x * sx, y);
    }

    // ================================================================== HATS
    private static double hcx, hcy, hcz, hsc;
    private static double[] hX = new double[3], hY = new double[3], hZ = new double[3];

    private static double[] hp(double lx, double ly, double lz) {
        lx *= hsc; ly *= hsc; lz *= hsc;
        return v3(hcx + hX[0] * lx + hY[0] * ly + hZ[0] * lz, hcy + hX[1] * lx + hY[1] * ly + hZ[1] * lz, hcz + hX[2] * lx + hY[2] * ly + hZ[2] * lz);
    }

    private static void hatFrame(Rig r, double size) {
        double yaw = Math.toRadians(r.headYaw), pitch = Math.toRadians(clamp(r.headPitch, -60, 80)) * 0.8;
        double hfx = -Math.sin(yaw), hfz = Math.cos(yaw), hrx = -Math.cos(yaw), hrz = -Math.sin(yaw);
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        hX = v3(hrx, 0, hrz);
        hY = v3(hfx * sp, cp, hfz * sp);              // up tilts forward when looking down
        hZ = v3(hfx * cp, -sp, hfz * cp);
        hcx = r.x; hcy = r.y + r.eyeH; hcz = r.z;
        hsc = size;
    }

    public static void hat(Rig r, int style, int c1, int c2, double size) {
        c1 = alphaMul(c1, r.alpha / 255.0); c2 = alphaMul(c2, r.alpha / 255.0);
        hatFrame(r, size);
        Gfx.use(false, false);
        switch (style) {
            case 1: wizardHat(c1, c2, r.time); break;
            case 2: crown(c1, c2, r.time); break;
            case 3: halo(c1, c2, r.time); break;
            case 4: catEars(c1, c2); break;
            default: topHat(c1, c2); break;
        }
    }

    /** Ring of points around the local Y axis. */
    private static double[] ringPt(double y, double rad, double a, double cx, double cz) {
        return hp(cx + Math.cos(a) * rad, y, cz + Math.sin(a) * rad);
    }

    private static void cylinder(double y0, double y1, double r0, double r1, int c0, int c1, int n, boolean capTop) {
        for (int i = 0; i < n; i++) {
            double a = i * TAU / n, b = (i + 1) * TAU / n;
            int ca = mixc(c0, c1, 0.5 + 0.5 * Math.cos(a * 1.0 - 0.8) * 0.0);
            quad(ringPt(y0, r0, a, 0, 0), ringPt(y0, r0, b, 0, 0), ringPt(y1, r1, b, 0, 0), ringPt(y1, r1, a, 0, 0), c0, c0, c1, c1, true);
            if (capTop) tri(hp(0, y1, 0), ringPt(y1, r1, a, 0, 0), ringPt(y1, r1, b, 0, 0), lighter(c1, 0.1f), c1, c1, true);
        }
    }

    private static void annulus(double y, double rin, double rout, int cin, int cout, int n, boolean lit) {
        for (int i = 0; i < n; i++) {
            double a = i * TAU / n, b = (i + 1) * TAU / n;
            quad(ringPt(y, rin, a, 0, 0), ringPt(y, rout, a, 0, 0), ringPt(y, rout, b, 0, 0), ringPt(y, rin, b, 0, 0), cin, cout, cout, cin, lit);
        }
    }

    private static void topHat(int c1, int c2) {
        int n = 16;
        double y0 = 0.24, y1 = 0.60;
        cylinder(y0, y1, 0.205, 0.192, darker(c1, 0.1f), lighter(c1, 0.12f), n, true);
        cylinder(y0 + 0.005, y0 + 0.085, 0.211, 0.209, c2, c2, n, false);                 // band
        annulus(y0 + 0.002, 0.19, 0.34, lighter(c1, 0.06f), darker(c1, 0.15f), n, true);       // brim
    }

    private static void wizardHat(int c1, int c2, double time) {
        int n = 18, stacks = 8;
        annulus(0.245, 0.19, 0.43, lighter(c1, 0.1f), darker(c1, 0.25f), n, true);          // brim
        double h = 0.72, base = 0.25, r0 = 0.225, bendX = 0.06, bendZ = -0.30;
        for (int s = 0; s < stacks; s++) {
            double t0 = s / (double) stacks, t1 = (s + 1) / (double) stacks;
            double rA = r0 * (1 - t0), rB = r0 * (1 - t1);
            double yA = base + h * t0, yB = base + h * t1;
            double xA = bendX * t0 * t0, xB = bendX * t1 * t1, zA = bendZ * t0 * t0, zB = bendZ * t1 * t1;
            int cA = mixc(lighter(c1, 0.15f), darker(c1, 0.35f), t0), cB = mixc(lighter(c1, 0.15f), darker(c1, 0.35f), t1);
            for (int i = 0; i < n; i++) {
                double a = i * TAU / n, b = (i + 1) * TAU / n;
                if (s == stacks - 1) tri(ringPt(yA, rA, a, xA, zA), ringPt(yA, rA, b, xA, zA), hp(xB, yB, zB), cA, cA, cB, true);
                else quad(ringPt(yA, rA, a, xA, zA), ringPt(yA, rA, b, xA, zA), ringPt(yB, rB, b, xB, zB), ringPt(yB, rB, a, xB, zB), cA, cA, cB, cB, true);
            }
        }
        cylinder(base + 0.004, base + 0.075, 0.2305, 0.2255, c2, c2, n, false);              // band
        // glowing stars on the cone
        Gfx.use(true, false);
        for (int k = 0; k < 7; k++) {
            double t = 0.18 + 0.62 * Hash.f(5, k), a = Hash.f(6, k) * TAU;
            double rr = r0 * (1 - t) + 0.012;
            double[] p = hp(Math.cos(a) * rr + bendX * t * t, base + h * t, Math.sin(a) * rr + bendZ * t * t);
            double tw = 0.55 + 0.45 * Math.sin(time * 3.0 + k * 1.9);
            sparkle(p, 0.03 * hsc * (0.7 + 0.5 * Hash.f(7, k)), alphaMul(lighter(c2, 0.3f), tw));
        }
        Gfx.use(false, false);
    }

    private static void sparkle(double[] p, double s, int c) {
        int tip = ColorUtil.withAlpha(c, 0);
        double[][] d = {{Gfx.rightX, Gfx.rightY, Gfx.rightZ}, {Gfx.upX, Gfx.upY, Gfx.upZ}};
        for (int k = 0; k < 2; k++) {
            double[] a = d[k], b = d[1 - k];
            Gfx.quad(p[0] - a[0] * s * 2.4, p[1] - a[1] * s * 2.4, p[2] - a[2] * s * 2.4, tip, p[0] + b[0] * s * 0.35, p[1] + b[1] * s * 0.35, p[2] + b[2] * s * 0.35, c,
                     p[0] + a[0] * s * 2.4, p[1] + a[1] * s * 2.4, p[2] + a[2] * s * 2.4, tip, p[0] - b[0] * s * 0.35, p[1] - b[1] * s * 0.35, p[2] - b[2] * s * 0.35, c);
        }
    }

    private static void crown(int c1, int c2, double time) {
        int n = 16, spikes = 8;
        double y0 = 0.22, y1 = 0.31, rad = 0.215;
        int gold = c1, goldDark = darker(c1, 0.35f), goldLight = lighter(c1, 0.35f);
        for (int i = 0; i < n; i++) {
            double a = i * TAU / n, b = (i + 1) * TAU / n;
            int cc = mixc(goldDark, goldLight, 0.5 + 0.5 * Math.sin(a * 2 + 0.6));
            quad(ringPt(y0, rad, a, 0, 0), ringPt(y0, rad, b, 0, 0), ringPt(y1, rad, b, 0, 0), ringPt(y1, rad, a, 0, 0), cc, cc, lighter(cc, 0.1f), lighter(cc, 0.1f), true);
        }
        annulus(y1, rad - 0.02, rad, goldDark, gold, n, true);
        for (int i = 0; i < spikes; i++) {
            double a0 = i * TAU / spikes - 0.30, a1 = i * TAU / spikes + 0.30, am = i * TAU / spikes;
            double tipH = y1 + (i % 2 == 0 ? 0.17 : 0.11);
            tri(ringPt(y1, rad, a0, 0, 0), ringPt(y1, rad, a1, 0, 0), ringPt(tipH, rad * 0.97, am, 0, 0), gold, gold, goldLight, true);
            if (i % 2 == 0) {                                                                // gem on every second spike
                double gy = y0 + 0.045, gr = 0.028;
                double[] top = ringPt(gy + gr * 1.2, rad + 0.012, am, 0, 0), bot = ringPt(gy - gr * 1.2, rad + 0.012, am, 0, 0);
                double[] l = ringPt(gy, rad + 0.012 + gr * 0.2, am - gr * 3.2, 0, 0), rr = ringPt(gy, rad + 0.012 + gr * 0.2, am + gr * 3.2, 0, 0);
                tri(top, l, rr, lighter(c2, 0.5f), c2, c2, false);
                tri(bot, rr, l, darker(c2, 0.3f), c2, c2, false);
            }
        }
    }

    private static void halo(int c1, int c2, double time) {
        Gfx.use(true, false);
        double y = 0.50 + 0.022 * Math.sin(time * 2.2);
        int n = 28;
        int core = c1, soft = alphaMul(c1, 0.32), none = ColorUtil.withAlpha(c1, 0);
        annulus(y, 0.205, 0.27, core, core, n, false);
        annulus(y, 0.15, 0.205, none, soft, n, false);
        annulus(y, 0.27, 0.36, soft, none, n, false);
        for (int k = 0; k < 3; k++) {
            double a = time * 0.9 + k * TAU / 3;
            double[] p = ringPt(y, 0.238, a, 0, 0);
            double tw = 0.5 + 0.5 * Math.sin(time * 4 + k * 2);
            sparkle(p, 0.028 * hsc, alphaMul(c2, 0.5 + 0.5 * tw));
        }
        Gfx.use(false, false);
    }

    private static void catEars(int c1, int c2) {
        for (int s = -1; s <= 1; s += 2) {
            double xc = s * 0.165, zc = 0.02, yb = 0.225;
            double[] b0 = hp(xc - 0.085, yb, zc - 0.07), b1 = hp(xc + 0.085, yb, zc - 0.07), b2 = hp(xc + 0.085, yb, zc + 0.07), b3 = hp(xc - 0.085, yb, zc + 0.07);
            double[] apex = hp(xc + s * 0.03, yb + 0.23, zc - 0.015);
            int dark = darker(c1, 0.25f), light = lighter(c1, 0.1f);
            tri(b0, b1, apex, dark, dark, light, true);
            tri(b1, b2, apex, dark, dark, light, true);
            tri(b2, b3, apex, dark, dark, light, true);
            tri(b3, b0, apex, dark, dark, light, true);
            // inner ear on the front face
            double[] i0 = hp(xc - 0.05, yb + 0.012, zc + 0.073), i1 = hp(xc + 0.05, yb + 0.012, zc + 0.073), i2 = hp(xc + s * 0.025, yb + 0.15, zc + 0.012);
            tri(i0, i1, i2, c2, c2, lighter(c2, 0.25f), false);
        }
    }
}
