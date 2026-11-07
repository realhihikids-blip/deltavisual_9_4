package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.render.Gfx;
import dev.deltavisuals.util.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Solid ribbon behind a player. Stores the last N positions, smooths them with Catmull-Rom,
 * tapers the width and fades the alpha towards the tail. Used twice: "Trails" and the denser "Blink".
 * 0.6.0: thinner (default width 0.3, range 0.02..0.5) and shorter (default 12 ticks) by default.
 */
public final class Trails extends Module {
    public static final String[] MODES = {"Vertical", "Horizontal", "Spiral", "Wave"};
    private static final double TAU = Math.PI * 2;
    private static final int CAP = 128, MAX_PTS = CAP * 6 + 8;

    private static final class Buf {
        final double[] p = new double[CAP * 3];
        int count, seen;
        void push(double x, double y, double z) {
            if (count == CAP) dropOldest();
            p[count * 3] = x; p[count * 3 + 1] = y; p[count * 3 + 2] = z; count++;
        }
        void dropOldest() {
            if (count == 0) return;
            System.arraycopy(p, 3, p, 0, (count - 1) * 3);
            count--;
        }
        void clear() { count = 0; }
    }

    private static final double[] BLUR_SCALE = {2.8, 1.8};
    private static final float[] BLUR_ALPHA = {0.14f, 0.28f};

    private final boolean blink;
    private final Setting.Mode mode, colorMode;
    private final Setting.Num length, width, heightAbove, opacity, flow, smooth, spiralRadius, waveAmp;
    private final Setting.Color c1, c2;
    private final Setting.Bool glow, firstPerson, others;

    private final Palette pal = new Palette();
    private final Palette white = new Palette();
    private final Buf own = new Buf();
    private final Map<UUID, Buf> otherBufs = new HashMap<>();
    private final double[] src = new double[CAP * 3 + 6];
    private final double[] dst = new double[MAX_PTS * 3];
    private final double[] work = new double[MAX_PTS * 3];
    private final double[] hw = new double[MAX_PTS];
    private final double[] hw2 = new double[MAX_PTS];
    private Object lastWorld;
    private int ticks;

    public Trails(String name, boolean blink) {
        super(name, blink ? "Dense bright motion-blur ribbon behind you" : "Solid ribbon trail behind you",
            Category.COSMETICS, false);
        this.blink = blink;
        mode = add(new Setting.Mode("Mode", MODES, 0));
        // ids changed (Trail Length / Trail Width) on purpose: old saved profiles then pick up the new, shorter and thinner defaults
        length = add(new Setting.Num("Trail Length", 3, 100, 1, blink ? 8 : 12, " ticks"));
        width = add(new Setting.Num("Trail Width", 0.02, 0.5, 0.01, blink ? 0.4 : 0.3));
        heightAbove = add(new Setting.Num("Height Above Feet", 0, 1.8, 0.05, 0.9));
        heightAbove.visibleIf(() -> !mode.is("Horizontal"));
        spiralRadius = add(new Setting.Num("Spiral Radius", 0.1, 1.2, 0.05, 0.4));
        spiralRadius.visibleIf(() -> mode.is("Spiral"));
        waveAmp = add(new Setting.Num("Wave Height", 0.05, 1.0, 0.05, 0.3));
        waveAmp.visibleIf(() -> mode.is("Wave"));
        smooth = add(new Setting.Num("Smoothing", 1, 6, 1, blink ? 5 : 3));
        opacity = add(new Setting.Num("Opacity", 10, 100, 5, 85, "%"));
        colorMode = add(new Setting.Mode("Color", Palette.PLAIN, blink ? 4 : 1));
        c1 = add(new Setting.Color("Main Color", 0xFF55FFFF, false));
        c2 = add(new Setting.Color("Second Color", 0xFFFF4DA6, false));
        c1.visibleIf(() -> !colorMode.is("Rainbow") && !colorMode.is("Fire"));
        c2.visibleIf(() -> colorMode.is("Gradient") || colorMode.is("Neon"));
        flow = add(new Setting.Num("Color Flow Speed", 0.2, 5, 0.1, 1, "x"));
        flow.visibleIf(() -> !colorMode.is("Static"));
        glow = add(new Setting.Bool("Glow", true));
        firstPerson = add(new Setting.Bool("Show In First Person", true));
        others = add(new Setting.Bool("Other Players", false));
        white.mode = "Static"; white.c1 = 0xFFFFFF;
    }

    @Override public void onDisable() { own.clear(); otherBufs.clear(); }

    // ---------------------------------------------------------------- tick: record positions

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) { own.clear(); otherBufs.clear(); return; }
        if (mc.isPaused()) return;
        ticks++;
        if (lastWorld != mc.world) { lastWorld = mc.world; own.clear(); otherBufs.clear(); }
        int max = length.intValue();
        sample(own, mc.player, max);

        if (others.get()) {
            int n = 0;
            for (AbstractClientPlayerEntity pl : mc.world.getPlayers()) {
                if (pl == mc.player || n >= 16 || pl.squaredDistanceTo(mc.player) > 48 * 48 || pl.isInvisible()) continue;
                n++;
                Buf b = otherBufs.computeIfAbsent(pl.getUuid(), k -> new Buf());
                b.seen = ticks;
                sample(b, pl, max);
            }
            Iterator<Buf> it = otherBufs.values().iterator();
            while (it.hasNext()) if (it.next().seen != ticks) it.remove();
        } else if (!otherBufs.isEmpty()) otherBufs.clear();
    }

    private void sample(Buf b, Entity e, int max) {
        double x = e.getX(), y = e.getY(), z = e.getZ();
        boolean moved = true;
        if (b.count > 0) {
            int li = (b.count - 1) * 3;
            double dx = x - b.p[li], dy = y - b.p[li + 1], dz = z - b.p[li + 2];
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > 64) b.clear();                  // teleport: break the ribbon
            else moved = d2 > 1e-4;
        }
        if (moved) b.push(x, y, z); else b.dropOldest();
        while (b.count > max) b.dropOldest();
    }

    // ---------------------------------------------------------------- render

    @Override
    public void onWorldRender() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        pal.set(colorMode.get(), c1.rgb(), c2.rgb(), flow.get(), ticks + Gfx.partial);
        if (firstPerson.get() || !mc.options.getPerspective().isFirstPerson()) {
            draw(own, mc.player.getLerpedPos(Gfx.partial));
        }
        if (others.get() && mc.world != null) {
            for (AbstractClientPlayerEntity pl : mc.world.getPlayers()) {
                Buf b = otherBufs.get(pl.getUuid());
                if (b != null && pl != mc.player && !pl.isInvisible()) draw(b, pl.getLerpedPos(Gfx.partial));
            }
        }
    }

    private void draw(Buf b, Vec3d head) {
        if (b.count < 1) return;
        int n = b.count;
        System.arraycopy(b.p, 0, src, 0, n * 3);
        src[(n - 1) * 3] = head.x; src[(n - 1) * 3 + 1] = head.y; src[(n - 1) * 3 + 2] = head.z;
        if (n < 2) return;
        int sub = smooth.intValue();
        int m = Gfx.smooth(src, n, dst, sub);
        float alpha = opacity.floatValue() / 100f;
        double w = width.get();
        double time = ticks + Gfx.partial;

        switch (mode.index()) {
            case 0 -> { // vertical wall
                double yOff = heightAbove.get();
                for (int i = 0; i < m; i++) { work[i * 3] = dst[i * 3]; work[i * 3 + 1] = dst[i * 3 + 1] + yOff; work[i * 3 + 2] = dst[i * 3 + 2]; }
                Gfx.taper(hw, m, w * 0.1, w * 0.9);
                emit(work, m, Gfx.FACE_VERTICAL, alpha);
            }
            case 1 -> { // flat on the ground
                for (int i = 0; i < m; i++) { work[i * 3] = dst[i * 3]; work[i * 3 + 1] = dst[i * 3 + 1] + 0.04; work[i * 3 + 2] = dst[i * 3 + 2]; }
                Gfx.taper(hw, m, w * 0.08, w * 0.5);
                emit(work, m, Gfx.FACE_HORIZONTAL, alpha);
            }
            case 2 -> spiral(m, w, time, alpha);
            default -> { // wave
                double amp = waveAmp.get(), yOff = heightAbove.get();
                for (int i = 0; i < m; i++) {
                    double s = i / (double) (m - 1);
                    work[i * 3] = dst[i * 3];
                    work[i * 3 + 1] = dst[i * 3 + 1] + yOff + Math.sin(i * 0.28 - time * 0.45) * amp * s;
                    work[i * 3 + 2] = dst[i * 3 + 2];
                }
                Gfx.taper(hw, m, w * 0.045, w * 0.24);
                emit(work, m, Gfx.FACE_CAMERA, alpha);
            }
        }
    }

    /** Two ribbons winding around the path (double helix). */
    private void spiral(int m, double w, double time, float alpha) {
        double R = spiralRadius.get(), yOff = heightAbove.get();
        for (int strand = 0; strand < 2; strand++) {
            for (int i = 0; i < m; i++) {
                int a = Math.max(i - 1, 0) * 3, c = Math.min(i + 1, m - 1) * 3;
                double tx = dst[c] - dst[a], ty = dst[c + 1] - dst[a + 1], tz = dst[c + 2] - dst[a + 2];
                double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (tl < 1e-9) { tx = 0; ty = 0; tz = 1; } else { tx /= tl; ty /= tl; tz /= tl; }
                double rx = 0, ry = 1, rz = 0;
                if (Math.abs(ty) > 0.95) { rx = 1; ry = 0; }
                double nx = ty * rz - tz * ry, ny = tz * rx - tx * rz, nz = tx * ry - ty * rx;
                double nl = Math.max(1e-9, Math.sqrt(nx * nx + ny * ny + nz * nz)); nx /= nl; ny /= nl; nz /= nl;
                double bx = ty * nz - tz * ny, by = tz * nx - tx * nz, bz = tx * ny - ty * nx;
                double s = i / (double) (m - 1);
                double ang = i * 0.22 - time * 0.35 + strand * Math.PI;
                double r = R * (0.25 + 0.75 * s);
                double ca = Math.cos(ang) * r, sa = Math.sin(ang) * r;
                work[i * 3] = dst[i * 3] + nx * ca + bx * sa;
                work[i * 3 + 1] = dst[i * 3 + 1] + yOff + ny * ca + by * sa;
                work[i * 3 + 2] = dst[i * 3 + 2] + nz * ca + bz * sa;
            }
            Gfx.taper(hw, m, w * 0.03, w * 0.15);
            emit(work, m, Gfx.FACE_CAMERA, alpha, strand * 0.5);
        }
    }

    private void emit(double[] pts, int m, int face, float alpha) { emit(pts, m, face, alpha, 0); }

    private void emit(double[] pts, int m, int face, float alpha, double phase) {
        boolean xr = false;
        if (blink) {
            // motion blur: wide faint copies under the sharp ribbon, all additive
            Gfx.use(true, xr);
            double[] scale = BLUR_SCALE;
            float[] a = BLUR_ALPHA;
            for (int k = 0; k < 2; k++) {
                for (int i = 0; i < m; i++) hw2[i] = hw[i] * scale[k];
                Gfx.ribbon(pts, m, hw2, face, 0, 0, 0, pal, 0f, alpha * a[k], phase);
            }
            Gfx.ribbon(pts, m, hw, face, 0, 0, 0, pal, 0f, alpha, phase);
            for (int i = 0; i < m; i++) hw2[i] = hw[i] * 0.35;
            Gfx.ribbon(pts, m, hw2, face, 0, 0, 0, white, 0f, alpha * 0.9f, 0);
            return;
        }
        if (glow.get()) {
            Gfx.use(true, xr);
            for (int i = 0; i < m; i++) hw2[i] = hw[i] * 2.0;
            Gfx.ribbon(pts, m, hw2, face, 0, 0, 0, pal, 0f, alpha * 0.2f, phase);
        }
        Gfx.use(glow.get(), xr);
        Gfx.ribbon(pts, m, hw, face, 0, 0, 0, pal, 0f, alpha, phase);
    }
}
