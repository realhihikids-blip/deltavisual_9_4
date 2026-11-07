package dev.deltavisuals.render;

import dev.deltavisuals.util.ColorUtil;
import dev.deltavisuals.util.Palette;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Immediate-mode geometry helper. Everything is emitted as QUADS (a triangle is a quad with a repeated vertex),
 * which renders exactly like a triangle strip but needs no special index handling.
 * All state is static and reused: nothing here allocates per frame.
 * World coordinates are doubles; the camera offset is subtracted at emit time (no float precision loss far from spawn).
 */
public final class Gfx {
    public static final int MAX_QUADS_PER_FRAME = 40000;
    private static final double TAU = Math.PI * 2;

    private static VertexConsumerProvider providers;
    private static Matrix4f mat = new Matrix4f();
    private static VertexConsumer vc;
    private static final boolean[] used = new boolean[4];
    private static int quads;

    /** Camera position and basis (world space). right/up = screen axes, look = view direction. */
    public static double camX, camY, camZ;
    public static float rightX, rightY, rightZ, upX, upY, upZ, lookX, lookY, lookZ;
    public static float partial;

    private static final Vector3f TMP = new Vector3f();
    // ribbon / tube scratch
    private static double lastSx, lastSy = 1, lastSz;
    private static final double[] SIDE = new double[3];
    private static final double[] RING_A = new double[3 * 24];
    private static final double[] RING_B = new double[3 * 24];

    private Gfx() {}

    // ------------------------------------------------------------------ frame control

    public static void begin(VertexConsumerProvider p, Matrix4f positionMatrix, Camera camera, float partialTick) {
        providers = p;
        mat = positionMatrix;
        partial = partialTick;
        quads = 0;
        used[0] = used[1] = used[2] = used[3] = false;
        vc = null;
        Vec3d c = dev.deltavisuals.util.Compat.cameraPos(camera);
        camX = c.x; camY = c.y; camZ = c.z;
        Quaternionf q = camera.getRotation();
        q.transform(1f, 0f, 0f, TMP);  rightX = TMP.x; rightY = TMP.y; rightZ = TMP.z;
        q.transform(0f, 1f, 0f, TMP);  upX = TMP.x;    upY = TMP.y;    upZ = TMP.z;
        q.transform(0f, 0f, -1f, TMP); lookX = TMP.x;  lookY = TMP.y;  lookZ = TMP.z;
    }

    /** Selects the pipeline for the following primitives. */
    public static void use(boolean additive, boolean xray) {
        int i = (additive ? 1 : 0) | (xray ? 2 : 0);
        used[i] = true;
        vc = providers.getBuffer(Pipelines.layer(additive, xray));
    }

    /** Draws everything we queued right now, so our layers are not left for a later (wrong) render state. */
    public static void end() {
        if (providers instanceof VertexConsumerProvider.Immediate imm) {
            for (int i = 0; i < 4; i++) {
                if (used[i]) imm.draw(Pipelines.layer((i & 1) != 0, (i & 2) != 0));
            }
        }
        vc = null;
    }

    public static boolean hasBudget(int needQuads) { return quads + needQuads < MAX_QUADS_PER_FRAME; }

    public static double distToCamera(double x, double y, double z) {
        double dx = x - camX, dy = y - camY, dz = z - camZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ------------------------------------------------------------------ primitives

    private static void vtx(double x, double y, double z, int argb) {
        vc.vertex(mat, (float) (x - camX), (float) (y - camY), (float) (z - camZ))
            .color((argb >> 16) & 255, (argb >> 8) & 255, (argb >>> 0) & 255, argb >>> 24);
    }

    public static void quad(double x1, double y1, double z1, int c1,
                            double x2, double y2, double z2, int c2,
                            double x3, double y3, double z3, int c3,
                            double x4, double y4, double z4, int c4) {
        if (vc == null || quads >= MAX_QUADS_PER_FRAME) return;
        quads++;
        vtx(x1, y1, z1, c1); vtx(x2, y2, z2, c2); vtx(x3, y3, z3, c3); vtx(x4, y4, z4, c4);
    }

    public static void tri(double x1, double y1, double z1, double x2, double y2, double z2,
                           double x3, double y3, double z3, int c) {
        quad(x1, y1, z1, c, x2, y2, z2, c, x3, y3, z3, c, x3, y3, z3, c);
    }

    /** Camera-facing line of the given half width. */
    public static void line(double ax, double ay, double az, double bx, double by, double bz, double hw, int c) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double mx = (ax + bx) * 0.5 - camX, my = (ay + by) * 0.5 - camY, mz = (az + bz) * 0.5 - camZ;
        double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;
        double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1e-9) { sx = rightX; sy = rightY; sz = rightZ; l = 1; }
        sx = sx / l * hw; sy = sy / l * hw; sz = sz / l * hw;
        quad(ax - sx, ay - sy, az - sz, c, ax + sx, ay + sy, az + sz, c, bx + sx, by + sy, bz + sz, c, bx - sx, by - sy, bz - sz, c);
    }

    /** Axis aligned box, six faces, flat colour. */
    public static void boxFill(double x1, double y1, double z1, double x2, double y2, double z2, int c) {
        quad(x1, y1, z1, c, x2, y1, z1, c, x2, y1, z2, c, x1, y1, z2, c);
        quad(x1, y2, z1, c, x1, y2, z2, c, x2, y2, z2, c, x2, y2, z1, c);
        quad(x1, y1, z1, c, x1, y2, z1, c, x2, y2, z1, c, x2, y1, z1, c);
        quad(x1, y1, z2, c, x2, y1, z2, c, x2, y2, z2, c, x1, y2, z2, c);
        quad(x1, y1, z1, c, x1, y1, z2, c, x1, y2, z2, c, x1, y2, z1, c);
        quad(x2, y1, z1, c, x2, y2, z1, c, x2, y2, z2, c, x2, y1, z2, c);
    }

    // ------------------------------------------------------------------ smoothing

    /** Catmull-Rom through n points (x,y,z packed). Writes (n-1)*sub+1 points into dst, returns that count. */
    public static int smooth(double[] src, int n, double[] dst, int sub) {
        if (n < 2) { for (int i = 0; i < n * 3; i++) dst[i] = src[i]; return n; }
        int o = 0;
        for (int i = 0; i < n - 1; i++) {
            int i0 = Math.max(i - 1, 0) * 3, i1 = i * 3, i2 = (i + 1) * 3, i3 = Math.min(i + 2, n - 1) * 3;
            for (int k = 0; k < sub; k++) {
                double t = k / (double) sub, t2 = t * t, t3 = t2 * t;
                for (int a = 0; a < 3; a++) {
                    double p0 = src[i0 + a], p1 = src[i1 + a], p2 = src[i2 + a], p3 = src[i3 + a];
                    dst[o * 3 + a] = 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3);
                }
                o++;
            }
        }
        dst[o * 3] = src[(n - 1) * 3]; dst[o * 3 + 1] = src[(n - 1) * 3 + 1]; dst[o * 3 + 2] = src[(n - 1) * 3 + 2];
        return o + 1;
    }

    // ------------------------------------------------------------------ ribbons

    public static final int FACE_CAMERA = 0, FACE_HORIZONTAL = 1, FACE_VERTICAL = 2, FACE_FIXED = 3;

    private static void sideAt(double[] p, int n, int i, int mode, double fx, double fy, double fz) {
        double sx, sy, sz;
        int a = Math.max(i - 1, 0) * 3, b = Math.min(i + 1, n - 1) * 3;
        double tx = p[b] - p[a], ty = p[b + 1] - p[a + 1], tz = p[b + 2] - p[a + 2];
        switch (mode) {
            case FACE_VERTICAL: sx = 0; sy = 1; sz = 0; break;
            case FACE_FIXED: sx = fx; sy = fy; sz = fz; break;
            case FACE_HORIZONTAL: sx = -tz; sy = 0; sz = tx; break;
            default: {
                double vx = p[i * 3] - camX, vy = p[i * 3 + 1] - camY, vz = p[i * 3 + 2] - camZ;
                sx = ty * vz - tz * vy; sy = tz * vx - tx * vz; sz = tx * vy - ty * vx;
            }
        }
        double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1e-7) { sx = lastSx; sy = lastSy; sz = lastSz; l = Math.sqrt(sx * sx + sy * sy + sz * sz); if (l < 1e-7) { sx = rightX; sy = rightY; sz = rightZ; l = 1; } }
        SIDE[0] = sx / l; SIDE[1] = sy / l; SIDE[2] = sz / l;
        lastSx = SIDE[0]; lastSy = SIDE[1]; lastSz = SIDE[2];
    }

    /**
     * Solid ribbon through n points. hw = half width per point. Point 0 uses aStart, point n-1 uses aEnd (alpha 0..1);
     * colour comes from the palette at phase (i/(n-1) + phaseOffset).
     */
    public static void ribbon(double[] p, int n, double[] hw, int mode, double fx, double fy, double fz,
                              Palette pal, float aStart, float aEnd, double phaseOffset) {
        if (n < 2 || vc == null || !hasBudget(n)) return;
        lastSx = rightX; lastSy = rightY; lastSz = rightZ;
        sideAt(p, n, 0, mode, fx, fy, fz);
        double lx = p[0] - SIDE[0] * hw[0], ly = p[1] - SIDE[1] * hw[0], lz = p[2] - SIDE[2] * hw[0];
        double rx = p[0] + SIDE[0] * hw[0], ry = p[1] + SIDE[1] * hw[0], rz = p[2] + SIDE[2] * hw[0];
        int c0 = col(pal, 0, aStart, aEnd, phaseOffset);
        for (int i = 1; i < n; i++) {
            sideAt(p, n, i, mode, fx, fy, fz);
            double nlx = p[i * 3] - SIDE[0] * hw[i], nly = p[i * 3 + 1] - SIDE[1] * hw[i], nlz = p[i * 3 + 2] - SIDE[2] * hw[i];
            double nrx = p[i * 3] + SIDE[0] * hw[i], nry = p[i * 3 + 1] + SIDE[1] * hw[i], nrz = p[i * 3 + 2] + SIDE[2] * hw[i];
            int c1 = col(pal, i / (double) (n - 1), aStart, aEnd, phaseOffset);
            quad(lx, ly, lz, c0, rx, ry, rz, c0, nrx, nry, nrz, c1, nlx, nly, nlz, c1);
            lx = nlx; ly = nly; lz = nlz; rx = nrx; ry = nry; rz = nrz; c0 = c1;
        }
    }

    private static int col(Palette pal, double t, float aStart, float aEnd, double phaseOffset) {
        float a = aStart + (aEnd - aStart) * (float) t;
        return pal.at(t + phaseOffset, a);
    }

    /** Fills hw[0..n) with a taper: width w0 at index 0 to w1 at index n-1 (linear). */
    public static void taper(double[] hw, int n, double w0, double w1) {
        for (int i = 0; i < n; i++) hw[i] = w0 + (w1 - w0) * (n > 1 ? i / (double) (n - 1) : 0);
    }

    // ------------------------------------------------------------------ tubes

    /**
     * Segmented tube (round cross-section, `sides` <= 24). radius[i] per point.
     * Colour: palette at phase t, blended from baseRgb (at t=0) into the palette colour; baseRgb < 0 = pure palette.
     */
    public static void tube(double[] p, int n, double[] radius, int sides, Palette pal, int baseRgb, float aStart, float aEnd) {
        if (n < 2 || vc == null) return;
        sides = Math.max(3, Math.min(24, sides));
        if (!hasBudget(n * sides)) return;
        double nx = 0, ny = 0, nz = 0;
        double[] prev = RING_A, cur = RING_B;
        int prevC = 0;
        for (int i = 0; i < n; i++) {
            int a = Math.max(i - 1, 0) * 3, b = Math.min(i + 1, n - 1) * 3;
            double tx = p[b] - p[a], ty = p[b + 1] - p[a + 1], tz = p[b + 2] - p[a + 2];
            double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (tl < 1e-9) { tx = 0; ty = 1; tz = 0; } else { tx /= tl; ty /= tl; tz /= tl; }
            // reference axis: world up unless almost parallel
            double rx = 0, ry = 1, rz = 0;
            if (Math.abs(ty) > 0.95) { rx = 1; ry = 0; }
            nx = ty * rz - tz * ry; ny = tz * rx - tx * rz; nz = tx * ry - ty * rx;
            double nl = Math.sqrt(nx * nx + ny * ny + nz * nz); nx /= nl; ny /= nl; nz /= nl;
            double bx = ty * nz - tz * ny, by = tz * nx - tx * nz, bz = tx * ny - ty * nx;
            for (int s = 0; s < sides; s++) {
                double ang = s * TAU / sides, ca = Math.cos(ang) * radius[i], sa = Math.sin(ang) * radius[i];
                cur[s * 3] = p[i * 3] + nx * ca + bx * sa;
                cur[s * 3 + 1] = p[i * 3 + 1] + ny * ca + by * sa;
                cur[s * 3 + 2] = p[i * 3 + 2] + nz * ca + bz * sa;
            }
            double t = i / (double) (n - 1);
            int rgb = baseRgb < 0 ? pal.at(t) : ColorUtil.lerp(0xFF000000 | baseRgb, pal.at(t), (float) Math.pow(t, 0.7));
            int c = ColorUtil.withAlpha(rgb, Math.round(255f * Math.max(0f, Math.min(1f, aStart + (aEnd - aStart) * (float) t))));
            if (i > 0) {
                for (int s = 0; s < sides; s++) {
                    int s2 = (s + 1) % sides;
                    quad(prev[s * 3], prev[s * 3 + 1], prev[s * 3 + 2], prevC, prev[s2 * 3], prev[s2 * 3 + 1], prev[s2 * 3 + 2], prevC,
                         cur[s2 * 3], cur[s2 * 3 + 1], cur[s2 * 3 + 2], c, cur[s * 3], cur[s * 3 + 1], cur[s * 3 + 2], c);
                }
            }
            double[] sw = prev; prev = cur; cur = sw; prevC = c;
        }
    }

    // ------------------------------------------------------------------ spheres

    private static double spCx, spCy, spCz, spRx, spRy, spRz, spAmp, spTime;
    private static int spTop, spBot;

    private static void spv(int i, int lat, int j, int lon) {
        double th = Math.PI * i / lat;            // 0..PI from the top
        double ph = TAU * j / lon;
        double st = Math.sin(th), ct = Math.cos(th);
        double k = 1.0 + (spAmp == 0 ? 0 : spAmp * Math.sin(4 * ph + spTime) * Math.cos(3 * th - spTime * 1.3));
        double x = spCx + Math.cos(ph) * st * spRx * k, y = spCy + ct * spRy * k, z = spCz + Math.sin(ph) * st * spRz * k;
        vtx(x, y, z, ColorUtil.lerp(spTop, spBot, (float) i / lat));
    }

    /** Ellipsoid. cTop/cBot are ARGB. wobble > 0 distorts the surface (hit bubble). */
    public static void sphere(double cx, double cy, double cz, double rx, double ry, double rz, int lat, int lon,
                              int cTop, int cBot, double wobble, double wobbleTime) {
        if (vc == null || rx <= 0 || ry <= 0 || rz <= 0) return;
        lat = Math.max(3, Math.min(32, lat)); lon = Math.max(4, Math.min(48, lon));
        if (!hasBudget(lat * lon)) return;
        spCx = cx; spCy = cy; spCz = cz; spRx = rx; spRy = ry; spRz = rz; spAmp = wobble; spTime = wobbleTime;
        spTop = cTop; spBot = cBot;
        for (int i = 0; i < lat; i++) {
            for (int j = 0; j < lon; j++) {
                quads++;
                spv(i, lat, j, lon); spv(i + 1, lat, j, lon); spv(i + 1, lat, j + 1, lon); spv(i, lat, j + 1, lon);
            }
        }
    }

    // ------------------------------------------------------------------ rings / discs / gears

    /**
     * Flat arc-annulus in the plane spanned by unit vectors u and v around (cx,cy,cz).
     * a0 = start angle, sweep = angle covered (TAU = full ring).
     */
    public static void annulus(double cx, double cy, double cz, double ux, double uy, double uz, double vx, double vy, double vz,
                               double r0, double r1, double a0, double sweep, int segs, int cInner, int cOuter) {
        if (vc == null || sweep == 0) return;
        segs = Math.max(2, Math.min(96, (int) Math.ceil(segs * Math.min(1.0, Math.abs(sweep) / TAU) + 1)));
        if (!hasBudget(segs)) return;
        double step = sweep / segs;
        for (int i = 0; i < segs; i++) {
            double a = a0 + i * step, b = a + step;
            double ca = Math.cos(a), sa = Math.sin(a), cb = Math.cos(b), sb = Math.sin(b);
            quad(cx + (ux * ca + vx * sa) * r0, cy + (uy * ca + vy * sa) * r0, cz + (uz * ca + vz * sa) * r0, cInner,
                 cx + (ux * ca + vx * sa) * r1, cy + (uy * ca + vy * sa) * r1, cz + (uz * ca + vz * sa) * r1, cOuter,
                 cx + (ux * cb + vx * sb) * r1, cy + (uy * cb + vy * sb) * r1, cz + (uz * cb + vz * sb) * r1, cOuter,
                 cx + (ux * cb + vx * sb) * r0, cy + (uy * cb + vy * sb) * r0, cz + (uz * cb + vz * sb) * r0, cInner);
        }
    }

    /** Horizontal annulus (XZ plane). */
    public static void ringFlat(double cx, double cy, double cz, double r0, double r1, double a0, double sweep, int segs, int cInner, int cOuter) {
        annulus(cx, cy, cz, 1, 0, 0, 0, 0, 1, r0, r1, a0, sweep, segs, cInner, cOuter);
    }

    /** Vertical cylinder wall (a ring seen from the side). */
    public static void band(double cx, double cy, double cz, double r, double y0, double y1, int segs, int cBottom, int cTop) {
        if (vc == null) return;
        segs = Math.max(6, Math.min(96, segs));
        if (!hasBudget(segs)) return;
        for (int i = 0; i < segs; i++) {
            double a = TAU * i / segs, b = TAU * (i + 1) / segs;
            double x1 = cx + Math.cos(a) * r, z1 = cz + Math.sin(a) * r, x2 = cx + Math.cos(b) * r, z2 = cz + Math.sin(b) * r;
            quad(x1, cy + y0, z1, cBottom, x2, cy + y0, z2, cBottom, x2, cy + y1, z2, cTop, x1, cy + y1, z1, cTop);
        }
    }

    /** Toothed ring: body annulus rIn..rOut plus `teeth` trapezoids reaching rOut+toothH. */
    public static void gear(double cx, double cy, double cz, double ux, double uy, double uz, double vx, double vy, double vz,
                            double rIn, double rOut, double toothH, int teeth, double rot, int cIn, int cOut) {
        annulus(cx, cy, cz, ux, uy, uz, vx, vy, vz, rIn, rOut, rot, TAU, teeth * 3, cIn, cOut);
        double pitch = TAU / teeth, w = pitch * 0.22;
        for (int k = 0; k < teeth; k++) {
            double a = rot + k * pitch;
            double a1 = a - w, a2 = a + w, b1 = a - w * 0.6, b2 = a + w * 0.6;
            double c1 = Math.cos(a1), s1 = Math.sin(a1), c2 = Math.cos(a2), s2 = Math.sin(a2);
            double d1 = Math.cos(b1), e1 = Math.sin(b1), d2 = Math.cos(b2), e2 = Math.sin(b2);
            double rt = rOut + toothH;
            quad(cx + (ux * c1 + vx * s1) * rOut, cy + (uy * c1 + vy * s1) * rOut, cz + (uz * c1 + vz * s1) * rOut, cOut,
                 cx + (ux * c2 + vx * s2) * rOut, cy + (uy * c2 + vy * s2) * rOut, cz + (uz * c2 + vz * s2) * rOut, cOut,
                 cx + (ux * d2 + vx * e2) * rt, cy + (uy * d2 + vy * e2) * rt, cz + (uz * d2 + vz * e2) * rt, cOut,
                 cx + (ux * d1 + vx * e1) * rt, cy + (uy * d1 + vy * e1) * rt, cz + (uz * d1 + vz * e1) * rt, cOut);
        }
    }
}
