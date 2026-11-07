package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.render.Gfx;
import dev.deltavisuals.util.ColorUtil;
import dev.deltavisuals.util.Hash;
import dev.deltavisuals.util.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/** Played when the Target ESP target dies. Three solid-geometry effects, 1-2 seconds, smooth fade. */
public final class KillEffect extends Module {
    public static final String[] TYPES = {"Decay", "Singularity", "Abyss"};
    private static final double TAU = Math.PI * 2;
    private static final int MAX_FX = 4, MAX_SHARDS = 128;

    private static final class Instance {
        boolean active;
        int type, seed, start;
        double x, y, z, w, h;
        int shards;
        final float[] sx = new float[MAX_SHARDS], sy = new float[MAX_SHARDS], sz = new float[MAX_SHARDS];
        final float[] vx = new float[MAX_SHARDS], vy = new float[MAX_SHARDS], vz = new float[MAX_SHARDS];
        final float[] a1x = new float[MAX_SHARDS], a1y = new float[MAX_SHARDS], a1z = new float[MAX_SHARDS];
        final float[] a2x = new float[MAX_SHARDS], a2y = new float[MAX_SHARDS], a2z = new float[MAX_SHARDS];
        final float[] sSize = new float[MAX_SHARDS], spin = new float[MAX_SHARDS];
    }

    private final Setting.Mode type, colorMode;
    private final Setting.Num duration, size, shardCount, tentacles, flow;
    private final Setting.Color c1, c2;
    private final Setting.Bool glow;

    private final Instance[] fx = new Instance[MAX_FX];
    private final Palette pal = new Palette();
    private final double[] pts = new double[3 * 16];
    private final double[] rad = new double[16];
    private int ticks, counter;

    public KillEffect() {
        super("Kill Effect", "Solid 3D effect when your Target ESP target dies", Category.VISUALS, false);
        type = add(new Setting.Mode("Effect", TYPES, 0));
        duration = add(new Setting.Num("Duration", 1.0, 2.0, 0.1, 1.5, " s"));
        size = add(new Setting.Num("Size", 0.4, 3, 0.05, 1.0));
        shardCount = add(new Setting.Num("Shards", 20, 120, 5, 70));
        shardCount.visibleIf(() -> type.is("Decay"));
        tentacles = add(new Setting.Num("Tentacles", 3, 10, 1, 6));
        tentacles.visibleIf(() -> type.is("Abyss"));
        colorMode = add(new Setting.Mode("Color Mode", Palette.PLAIN, 1));
        c1 = add(new Setting.Color("Main Color", 0xFF8A3CFF, false));
        c2 = add(new Setting.Color("Second Color", 0xFF33CCFF, false));
        c1.visibleIf(() -> !colorMode.is("Rainbow") && !colorMode.is("Fire"));
        c2.visibleIf(() -> colorMode.is("Gradient") || colorMode.is("Neon"));
        flow = add(new Setting.Num("Color Flow Speed", 0.2, 5, 0.1, 1, "x"));
        flow.visibleIf(() -> !colorMode.is("Static"));
        glow = add(new Setting.Bool("Glow", true));
        for (int i = 0; i < MAX_FX; i++) fx[i] = new Instance();
    }

    @Override public void onDisable() { for (Instance i : fx) i.active = false; }

    @Override public void onTick(MinecraftClient mc) { if (!mc.isPaused()) ticks++; }

    /** Starts the selected effect at the entity's last position. */
    public void trigger(LivingEntity e) {
        if (!isEnabled()) return;
        Instance in = null;
        for (Instance i : fx) if (!i.active) { in = i; break; }
        if (in == null) in = fx[0];
        Vec3d p = e.getEntityPos();
        in.active = true; in.type = type.index(); in.start = ticks; in.seed = ++counter * 7919 + e.getId();
        in.x = p.x; in.y = p.y; in.z = p.z; in.w = e.getWidth(); in.h = e.getHeight();
        if (in.type == 0) initShards(in);
    }

    private void initShards(Instance in) {
        in.shards = Math.min(MAX_SHARDS, shardCount.intValue());
        for (int i = 0; i < in.shards; i++) {
            int s = in.seed;
            in.sx[i] = (float) ((Hash.f(s, i * 11 + 1) - 0.5) * in.w);
            in.sy[i] = (float) (Hash.f(s, i * 11 + 2) * in.h);
            in.sz[i] = (float) ((Hash.f(s, i * 11 + 3) - 0.5) * in.w);
            float dx = in.sx[i], dz = in.sz[i], dl = (float) Math.max(0.05, Math.sqrt(dx * dx + dz * dz));
            float sp = 1.2f + 2.6f * Hash.f(s, i * 11 + 4);
            in.vx[i] = dx / dl * sp + Hash.s(s, i * 11 + 5) * 0.8f;
            in.vz[i] = dz / dl * sp + Hash.s(s, i * 11 + 6) * 0.8f;
            in.vy[i] = 0.8f + 2.4f * Hash.f(s, i * 11 + 7);
            // two orthonormal-ish axes spanning the shard plane
            float ax = Hash.s(s, i * 11 + 8), ay = Hash.s(s, i * 11 + 9), az = Hash.s(s, i * 11 + 10);
            float al = (float) Math.max(0.01, Math.sqrt(ax * ax + ay * ay + az * az));
            ax /= al; ay /= al; az /= al;
            float bx = ay, by = -ax, bz = 0;
            float bl = (float) Math.max(0.01, Math.sqrt(bx * bx + by * by));
            bx /= bl; by /= bl;
            in.a1x[i] = bx; in.a1y[i] = by; in.a1z[i] = bz;
            in.a2x[i] = ay * bz - az * by; in.a2y[i] = az * bx - ax * bz; in.a2z[i] = ax * by - ay * bx;
            in.sSize[i] = (float) ((0.10 + 0.16 * Hash.f(s, i * 11 + 11)) * Math.max(0.7, in.h * 0.6));
            in.spin[i] = Hash.s(s, i * 11 + 12) * 9f;
        }
    }

    @Override
    public void onWorldRender() {
        for (Instance in : fx) {
            if (!in.active) continue;
            double durTicks = duration.get() * 20.0;
            double age = ticks - in.start + Gfx.partial;
            float p = (float) (age / durTicks);
            if (p >= 1f) { in.active = false; continue; }
            float fade = 1f - Hash.smooth((p - 0.7f) / 0.3f);
            pal.set(colorMode.get(), c1.rgb(), c2.rgb(), flow.get(), ticks + Gfx.partial);
            switch (in.type) {
                case 0 -> decay(in, p, fade, durTicks / 20.0);
                case 1 -> singularity(in, p, fade);
                default -> abyss(in, p, fade);
            }
        }
    }

    private void layer(boolean additive) { Gfx.use(additive, false); }

    // ---------------------------------------------------------------- Decay

    private void decay(Instance in, float p, float fade, double durSec) {
        double sc = size.get();
        double s = p * durSec;                      // seconds since start
        layer(glow.get());
        if (p < 0.3f) {                             // the "model" dissolving
            float a = (1f - p / 0.3f) * 0.45f;
            Gfx.sphere(in.x, in.y + in.h * 0.5, in.z, in.w * 0.55, in.h * 0.52, in.w * 0.55, 10, 14,
                pal.at(0.0, a), pal.at(0.6, a), 0.03, ticks * 0.4);
        }
        for (int i = 0; i < in.shards; i++) {
            double px = in.x + in.sx[i] * sc + in.vx[i] * s * sc;
            double py = in.y + in.sy[i] + in.vy[i] * s - 4.4 * s * s;
            double pz = in.z + in.sz[i] * sc + in.vz[i] * s * sc;
            if (py < in.y + 0.02) py = in.y + 0.02;
            double r = in.sSize[i] * (1.0 - 0.75 * p) * sc;
            float a = (float) Math.pow(1f - p, 1.2) * fade * 0.95f;
            double ph = ((i * 37) % 100) / 100.0;
            int col = pal.at(ph, a);
            double ang = in.spin[i] * s;
            double t0 = ang, t1 = ang + TAU / 3, t2 = ang + 2 * TAU / 3;
            double c0 = Math.cos(t0), s0 = Math.sin(t0), c1_ = Math.cos(t1), s1 = Math.sin(t1), c2_ = Math.cos(t2), s2 = Math.sin(t2);
            Gfx.tri(
                px + (in.a1x[i] * c0 + in.a2x[i] * s0) * r, py + (in.a1y[i] * c0 + in.a2y[i] * s0) * r, pz + (in.a1z[i] * c0 + in.a2z[i] * s0) * r,
                px + (in.a1x[i] * c1_ + in.a2x[i] * s1) * r, py + (in.a1y[i] * c1_ + in.a2y[i] * s1) * r, pz + (in.a1z[i] * c1_ + in.a2z[i] * s1) * r,
                px + (in.a1x[i] * c2_ + in.a2x[i] * s2) * r, py + (in.a1y[i] * c2_ + in.a2y[i] * s2) * r, pz + (in.a1z[i] * c2_ + in.a2z[i] * s2) * r,
                col);
        }
    }

    // ---------------------------------------------------------------- Singularity

    private void singularity(Instance in, float p, float fade) {
        double sc = size.get();
        double cy = in.y + in.h * 0.5;
        float p1 = Hash.clamp01(p / 0.55f);
        float k = 1f - p1 * p1;                      // target shrinks into the centre
        if (k > 0.02f) {
            layer(glow.get());
            Gfx.sphere(in.x, cy, in.z, in.w * 0.55 * k + 0.01, in.h * 0.52 * k + 0.01, in.w * 0.55 * k + 0.01, 10, 14,
                pal.at(0.0, 0.4f * fade), pal.at(0.5, 0.4f * fade), 0.02 * k, ticks * 0.5);
        }
        double core = (0.12 + 0.30 * Math.sin(Math.PI * Hash.clamp01(p / 0.85f))) * sc * Math.max(0.8, in.h * 0.55);
        if (core > 0.02) {
            Gfx.use(false, false);
            Gfx.sphere(in.x, cy, in.z, core, core, core, 12, 18, 0xF2050010, 0xF2050010, 0, 0);
            layer(true);
            Gfx.sphere(in.x, cy, in.z, core * 1.25, core * 1.25, core * 1.25, 10, 16,
                pal.at(0.0, 0.35f * fade), pal.at(0.5, 0.15f * fade), 0.05, ticks * 0.6);
            // accretion band
            Gfx.annulus(in.x, cy, in.z, 1, 0, 0, 0, 0.2, 0.98, core * 1.5, core * 2.4, ticks * 0.3, TAU, 40,
                pal.at(0.2, 0.5f * fade), pal.at(0.6, 0f));
        }
        if (p > 0.5f) {                              // shockwave: 3 expanding rings
            float q = (p - 0.5f) / 0.5f;
            for (int i = 0; i < 3; i++) {
                float qi = q - i * 0.14f;
                if (qi <= 0f) continue;
                double R = Hash.smooth(qi) * 4.5 * sc + 0.3;
                float a = (float) Math.pow(1f - qi, 1.5) * fade;
                layer(true);
                Gfx.band(in.x, cy, in.z, R, -0.18 * (1 - qi), 0.18 * (1 - qi), 48, pal.at(i * 0.3, 0f), pal.at(i * 0.3, a * 0.8f));
                Gfx.band(in.x, cy, in.z, R, -0.18 * (1 - qi), 0.18 * (1 - qi), 48, pal.at(i * 0.3, a * 0.8f), pal.at(i * 0.3, 0f));
                Gfx.ringFlat(in.x, in.y + 0.04, in.z, R - 0.25 * sc, R + 0.1, 0, TAU, 48, pal.at(i * 0.3, 0f), pal.at(i * 0.3, a));
            }
        }
    }

    // ---------------------------------------------------------------- Abyss

    private void abyss(Instance in, float p, float fade) {
        double sc = size.get();
        int n = tentacles.intValue();
        float grow = p < 0.4f ? Hash.smooth(p / 0.4f) : 1f - Hash.smooth((p - 0.4f) / 0.6f);
        double baseR = in.w * 0.5 + 0.45 * sc;
        // dark pit on the ground
        float pit = (float) Math.sin(Math.PI * Hash.clamp01(p)) * fade;
        Gfx.use(false, false);
        Gfx.ringFlat(in.x, in.y + 0.03, in.z, 0, baseR * 1.6, 0, TAU, 40, ColorUtil.withAlpha(0x050008, (int) (224 * pit)), ColorUtil.withAlpha(0x1A0030, 0));
        // the target is dragged down
        float sink = Hash.smooth((p - 0.25f) / 0.6f);
        if (sink < 0.98f) {
            layer(glow.get());
            double kk = 1.0 - 0.6 * sink;
            Gfx.sphere(in.x, in.y + in.h * 0.5 - in.h * 0.9 * sink, in.z, in.w * 0.55 * kk, in.h * 0.5 * kk * (1 - 0.5 * sink), in.w * 0.55 * kk, 9, 12,
                pal.at(0.0, 0.3f * (1 - sink) * fade), pal.at(0.6, 0.3f * (1 - sink) * fade), 0, 0);
        }
        int K = 9;
        double L = (in.h * 1.25 + 0.7) * sc * grow;
        if (L < 0.05) return;
        for (int i = 0; i < n; i++) {
            double th = i * TAU / n + Hash.f(in.seed, i) * 0.5;
            for (int k = 0; k < K; k++) {
                double s = k / (double) (K - 1);
                double ang = th + 0.9 * Math.sin(s * 3.2 + p * 9 + i) * s;
                double r = baseR * (1.0 - 0.75 * Math.pow(s, 1.4)) + 0.12 * Math.sin(s * 5 + p * 12 + i * 2);
                pts[k * 3] = in.x + Math.cos(ang) * r;
                pts[k * 3 + 1] = in.y + s * L;
                pts[k * 3 + 2] = in.z + Math.sin(ang) * r;
                rad[k] = (0.15 * (1.0 - s) + 0.025) * sc;
            }
            Gfx.use(false, false);
            Gfx.tube(pts, K, rad, 7, pal, 0x1A0030, 0.95f * fade, 0.95f * fade);
            if (glow.get()) {
                layer(true);
                for (int k = 0; k < K; k++) rad[k] *= 1.45;
                Gfx.tube(pts, K, rad, 6, pal, -1, 0.0f, 0.22f * fade);
            }
        }
    }
}
