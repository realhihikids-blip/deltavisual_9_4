package dev.deltavisuals.render;

/**
 * Model transform + per-face lighting on top of {@link Gfx}.
 * Call {@link #set} once per object, then emit faces in LOCAL coordinates with {@link #quad} / {@link #tri}.
 *
 * - Faces are shaded by their world normal against a fixed light, so low-poly models look faceted and solid.
 * - With {@link #cull} on, faces turned away from the camera are skipped (needed for closed convex shapes,
 *   because our layers have no depth write). The outward direction is taken from the local model centre.
 * Static scratch state only: nothing allocates per frame.
 */
public final class Xf {
    private static double ox, oy, oz;
    private static double sx = 1, sy = 1, sz = 1;
    private static double m00 = 1, m01, m02, m10, m11 = 1, m12, m20, m21, m22 = 1;
    private static double ccx, ccy, ccz;      // local centre
    private static double wcx, wcy, wcz;      // world centre
    private static final double[] P = new double[12];

    /** Skip faces that look away from the camera (closed convex models). */
    public static boolean cull = true;
    /** Apply per-face lighting. */
    public static boolean shade = true;

    // light direction (normalised in the static block)
    private static final double LX, LY, LZ;
    static {
        double x = 0.38, y = 0.80, z = 0.46, l = Math.sqrt(x * x + y * y + z * z);
        LX = x / l; LY = y / l; LZ = z / l;
    }

    private Xf() {}

    /** Rotation = Ry(yaw) * Rx(pitch) * Rz(roll), radians. Scale per axis is applied before rotation. */
    public static void set(double x, double y, double z, double yaw, double pitch, double roll,
                           double scaleX, double scaleY, double scaleZ) {
        ox = x; oy = y; oz = z;
        sx = scaleX; sy = scaleY; sz = scaleZ;
        double cy = Math.cos(yaw), sY = Math.sin(yaw);
        double cp = Math.cos(pitch), sp = Math.sin(pitch);
        double cr = Math.cos(roll), sr = Math.sin(roll);
        double a00 = cr, a01 = -sr, a02 = 0;
        double a10 = cp * sr, a11 = cp * cr, a12 = -sp;
        double a20 = sp * sr, a21 = sp * cr, a22 = cp;
        m00 = cy * a00 + sY * a20; m01 = cy * a01 + sY * a21; m02 = cy * a02 + sY * a22;
        m10 = a10;                 m11 = a11;                 m12 = a12;
        m20 = -sY * a00 + cy * a20; m21 = -sY * a01 + cy * a21; m22 = -sY * a02 + cy * a22;
        ccx = ccy = ccz = 0;
        wcx = x; wcy = y; wcz = z;
        cull = true; shade = true;
    }

    public static void set(double x, double y, double z, double yaw, double pitch, double roll, double scale) {
        set(x, y, z, yaw, pitch, roll, scale, scale, scale);
    }

    /** Local point used to decide which side of a face is "outside". Default is the local origin. */
    public static void center(double lx, double ly, double lz) {
        ccx = lx; ccy = ly; ccz = lz;
        wcx = wx(lx, ly, lz); wcy = wy(lx, ly, lz); wcz = wz(lx, ly, lz);
    }

    /** Cosine between the world direction of local vector (lx,ly,lz) (a unit normal) and the direction from (x,y,z) to the camera. */
    public static double facing(double lx, double ly, double lz, double x, double y, double z) {
        double dx = m00 * lx + m01 * ly + m02 * lz, dy = m10 * lx + m11 * ly + m12 * lz, dz = m20 * lx + m21 * ly + m22 * lz;
        double cx = Gfx.camX - x, cy = Gfx.camY - y, cz = Gfx.camZ - z;
        double cl = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (cl < 1e-9) return 1;
        return (dx * cx + dy * cy + dz * cz) / cl;
    }

    /** Largest axis scale of the current transform. */
    public static double scale() { return Math.max(sx, Math.max(sy, sz)); }

    public static double wx(double x, double y, double z) { return ox + m00 * x * sx + m01 * y * sy + m02 * z * sz; }
    public static double wy(double x, double y, double z) { return oy + m10 * x * sx + m11 * y * sy + m12 * z * sz; }
    public static double wz(double x, double y, double z) { return oz + m20 * x * sx + m21 * y * sy + m22 * z * sz; }

    private static void put(int i, double x, double y, double z) {
        P[i * 3] = wx(x, y, z); P[i * 3 + 1] = wy(x, y, z); P[i * 3 + 2] = wz(x, y, z);
    }

    // ------------------------------------------------------------------ faces

    public static void quad(double x1, double y1, double z1, int c1,
                            double x2, double y2, double z2, int c2,
                            double x3, double y3, double z3, int c3,
                            double x4, double y4, double z4, int c4) {
        put(0, x1, y1, z1); put(1, x2, y2, z2); put(2, x3, y3, z3); put(3, x4, y4, z4);
        double nx = (P[7] - P[1]) * (P[11] - P[5]) - (P[8] - P[2]) * (P[10] - P[4]);
        double ny = (P[8] - P[2]) * (P[9] - P[3]) - (P[6] - P[0]) * (P[11] - P[5]);
        double nz = (P[6] - P[0]) * (P[10] - P[4]) - (P[7] - P[1]) * (P[9] - P[3]);
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (nl < 1e-12) return;
        nx /= nl; ny /= nl; nz /= nl;
        double fx = (P[0] + P[3] + P[6] + P[9]) * 0.25, fy = (P[1] + P[4] + P[7] + P[10]) * 0.25, fz = (P[2] + P[5] + P[8] + P[11]) * 0.25;
        double toCam = nx * (Gfx.camX - fx) + ny * (Gfx.camY - fy) + nz * (Gfx.camZ - fz);
        if (cull) {
            if (nx * (fx - wcx) + ny * (fy - wcy) + nz * (fz - wcz) < 0) { nx = -nx; ny = -ny; nz = -nz; toCam = -toCam; }
            if (toCam <= 0) return;
        } else if (toCam < 0) { nx = -nx; ny = -ny; nz = -nz; }
        if (shade) {
            double d = nx * LX + ny * LY + nz * LZ;                 // -1..1
            float s = (float) (0.60 + 0.50 * (0.5 + 0.5 * d));       // 0.60 .. 1.10
            c1 = lit(c1, s); c2 = lit(c2, s); c3 = lit(c3, s); c4 = lit(c4, s);
        }
        Gfx.quad(P[0], P[1], P[2], c1, P[3], P[4], P[5], c2, P[6], P[7], P[8], c3, P[9], P[10], P[11], c4);
    }

    public static void quad(double x1, double y1, double z1, double x2, double y2, double z2,
                            double x3, double y3, double z3, double x4, double y4, double z4, int c) {
        quad(x1, y1, z1, c, x2, y2, z2, c, x3, y3, z3, c, x4, y4, z4, c);
    }

    public static void tri(double x1, double y1, double z1, int c1, double x2, double y2, double z2, int c2,
                           double x3, double y3, double z3, int c3) {
        quad(x1, y1, z1, c1, x2, y2, z2, c2, x3, y3, z3, c3, x3, y3, z3, c3);
    }

    /** Thin camera-facing line between two local points. hw is in local units (scaled by the largest axis scale). */
    public static void line(double ax, double ay, double az, double bx, double by, double bz, double hw, int c) {
        double k = Math.max(sx, Math.max(sy, sz));
        Gfx.line(wx(ax, ay, az), wy(ax, ay, az), wz(ax, ay, az), wx(bx, by, bz), wy(bx, by, bz), wz(bx, by, bz), hw * k, c);
    }

    // ------------------------------------------------------------------ colour helpers

    /** Multiplies RGB by s (alpha untouched), clamped. */
    public static int lit(int c, float s) {
        int r = Math.min(255, Math.round(((c >> 16) & 255) * s));
        int g = Math.min(255, Math.round(((c >> 8) & 255) * s));
        int b = Math.min(255, Math.round((c & 255) * s));
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
