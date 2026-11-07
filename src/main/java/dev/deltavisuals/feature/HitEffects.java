package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.render.Gfx;
import dev.deltavisuals.render.Models;
import dev.deltavisuals.render.Xf;
import dev.deltavisuals.util.ColorUtil;
import dev.deltavisuals.util.Hash;
import dev.deltavisuals.util.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Solid 3D pieces that burst out of the target when you hit it (bigger on a kill).
 * Every piece is a low-poly model (stars, skulls, hearts, crystal shards, lightning bolts) or a ring (shockwave),
 * drawn through Gfx / Xf / Models. No Minecraft particles.
 *
 * Motion is a closed-form function of the burst age (position, spin, scale, alpha), so nothing needs per-tick state
 * and the animation is perfectly smooth at any frame rate (age includes the partial tick).
 */
public final class HitEffects extends Module {
    public static final String[] MODES = {"Stars", "Skulls", "Hearts", "Crystal Shards", "Lightning", "Shockwave"};
    private static final String[] COLOR_MODES = {"Auto", "Static", "Gradient", "Rainbow", "Fire", "Neon"};
    private static final int[] AUTO_A = {0xFFD34D, 0xFF3A2E, 0xFF2D55, 0x4DE3FF, 0x62B8FF, 0xFFFFFF};
    private static final int[] AUTO_B = {0xFFF6C8, 0xFF9A3C, 0xFF8FB0, 0xA06BFF, 0xE6F4FF, 0x6FD8FF};
    private static final double[] GRAVITY = {0.5, 3.0, -1.1, 5.0, 0.0, 0.0};
    private static final int BONE = 0xE9E4D2;
    private static final double TAU = Math.PI * 2;
    private static final int MAX_BURSTS = 12, MAX_PARTS = 64;

    private static final class Burst {
        boolean active, kill;
        int mode, seed, n;
        double x, y, z, start, dur, scale, speed;
        final float[] dx = new float[MAX_PARTS], dy = new float[MAX_PARTS], dz = new float[MAX_PARTS];
        final float[] spd = new float[MAX_PARTS], size = new float[MAX_PARTS], delay = new float[MAX_PARTS];
        final float[] yaw0 = new float[MAX_PARTS], pit0 = new float[MAX_PARTS], rol0 = new float[MAX_PARTS];
        final float[] spin = new float[MAX_PARTS], spin2 = new float[MAX_PARTS], phase = new float[MAX_PARTS];
    }

    private final Setting.Mode mode, colorMode;
    private final Setting.Num count, size, speed, duration, flow;
    private final Setting.Color c1, c2;
    private final Setting.Bool glow, playersOnly, bigKill;

    private final Burst[] bursts = new Burst[MAX_BURSTS];
    private final Map<Integer, Integer> lastHit = new HashMap<>();
    private final Map<Integer, Integer> killedAt = new HashMap<>();
    private final Palette pal = new Palette();
    private final Palette white = new Palette();
    private final double[] pts = new double[3 * 9];
    private final double[] hw = new double[9], hw2 = new double[9];
    private int ticks, counter;

    // scratch result of eval()
    private double px, py, pz, cs, ca, ct;

    public HitEffects() {
        super("Hit Effects", "Solid 3D stars, skulls, hearts, shards or bolts burst out of the target you hit", Category.VISUALS, false);
        mode = add(new Setting.Mode("Effect", MODES, 0));
        count = add(new Setting.Num("Count", 3, 40, 1, 14));
        size = add(new Setting.Num("Size", 0.3, 3, 0.05, 1.0));
        speed = add(new Setting.Num("Speed", 0.3, 3, 0.05, 1.0, "x"));
        duration = add(new Setting.Num("Duration", 0.3, 2.0, 0.05, 0.8, " s"));
        colorMode = add(new Setting.Mode("Color Mode", COLOR_MODES, 0));
        c1 = add(new Setting.Color("Main Color", 0xFF5DD2FF, false));
        c2 = add(new Setting.Color("Second Color", 0xFFFF5DB1, false));
        c1.visibleIf(() -> colorMode.is("Static") || colorMode.is("Gradient") || colorMode.is("Neon"));
        c2.visibleIf(() -> colorMode.is("Gradient") || colorMode.is("Neon"));
        flow = add(new Setting.Num("Color Flow Speed", 0.2, 5, 0.1, 1, "x"));
        flow.visibleIf(() -> !colorMode.is("Static") && !colorMode.is("Auto"));
        glow = add(new Setting.Bool("Glow", true));
        playersOnly = add(new Setting.Bool("Players Only", false));
        bigKill = add(new Setting.Bool("Bigger On Kill", true));
        add(new Setting.Info("Note", () -> "Shockwave: Count / 4 = number of rings"));
        for (int i = 0; i < MAX_BURSTS; i++) bursts[i] = new Burst();
        white.mode = "Static"; white.c1 = 0xFFFFFF;
    }

    @Override
    public void onDisable() {
        for (Burst b : bursts) b.active = false;
        lastHit.clear(); killedAt.clear();
    }

    // ------------------------------------------------------------------ events

    /** Called from the AttackEntityCallback in Modules (the same event Target ESP uses). */
    public void onAttack(Entity e, Vec3d hitPos) {
        if (!isEnabled() || !(e instanceof LivingEntity le)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (le == mc.player) return;
        if (playersOnly.get() && !(le instanceof PlayerEntity)) return;
        int id = le.getId();
        Integer dead = killedAt.get(id);
        if (dead != null && ticks - dead < 40) return;
        Integer last = lastHit.get(id);
        lastHit.put(id, ticks);
        if (last != null && ticks - last < 4) return;           // swing spam / invulnerability frames
        double x, y, z;
        if (hitPos != null) { x = hitPos.x; y = hitPos.y; z = hitPos.z; }
        else { x = le.getX(); y = le.getY() + le.getHeight() * 0.6; z = le.getZ(); }
        spawn(x, y, z, false, 1.0);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) { for (Burst b : bursts) b.active = false; lastHit.clear(); return; }
        if (mc.isPaused()) return;
        ticks++;
        for (Burst b : bursts) if (b.active && ticks - b.start > b.dur + 2) b.active = false;

        Iterator<Map.Entry<Integer, Integer>> it = lastHit.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> en = it.next();
            if (ticks - en.getValue() > 100) { it.remove(); continue; }
            Entity e = mc.world.getEntityById(en.getKey());
            if (e instanceof LivingEntity le && (le.isDead() || le.getHealth() <= 0f)) {
                killedAt.put(en.getKey(), ticks);
                it.remove();
                double hf = Math.max(0.8, Math.min(1.6, le.getHeight() / 1.8));
                spawn(le.getX(), le.getY() + le.getHeight() * 0.55, le.getZ(), true, hf);
            }
        }
        killedAt.values().removeIf(t -> ticks - t > 60);
    }

    // ------------------------------------------------------------------ spawn

    private void spawn(double x, double y, double z, boolean kill, double heightFactor) {
        Burst b = null;
        for (Burst c : bursts) if (!c.active) { b = c; break; }
        if (b == null) {                                        // all busy: recycle the oldest
            b = bursts[0];
            for (Burst c : bursts) if (c.start < b.start) b = c;
        }
        boolean big = kill && bigKill.get();
        int m = mode.index();
        int n = count.intValue();
        if (big) n = (int) Math.round(n * 1.6);
        if (m == 2) n = Math.min(n, big ? 28 : 22);            // hearts are the heaviest model
        n = Math.max(1, Math.min(MAX_PARTS, n));
        b.active = true; b.kill = big; b.mode = m; b.n = n;
        b.seed = ++counter * 7919 + 31;
        b.x = x; b.y = y; b.z = z;
        b.start = ticks;
        b.dur = duration.get() * 20.0 * (big ? 1.3 : 1.0);
        b.scale = size.get() * (big ? 1.45 * heightFactor : 1.0);
        b.speed = speed.get() * (big ? 1.1 : 1.0);
        double golden = 2.399963;
        double rot = Hash.f(b.seed, 999) * TAU;
        for (int i = 0; i < n; i++) {
            // fibonacci sphere: even spread, then a little jitter so it does not look like a grid
            double yy = 1.0 - 2.0 * (i + 0.5) / n;
            double rr = Math.sqrt(Math.max(0, 1 - yy * yy));
            double a = i * golden + rot;
            double jx = Hash.s(b.seed, i * 13 + 1) * 0.18, jy = Hash.s(b.seed, i * 13 + 2) * 0.18, jz = Hash.s(b.seed, i * 13 + 3) * 0.18;
            double dx = Math.cos(a) * rr + jx, dy = yy * 0.9 + 0.12 + jy, dz = Math.sin(a) * rr + jz;
            double l = Math.max(1e-6, Math.sqrt(dx * dx + dy * dy + dz * dz));
            b.dx[i] = (float) (dx / l); b.dy[i] = (float) (dy / l); b.dz[i] = (float) (dz / l);
            b.spd[i] = 2.0f + 2.6f * Hash.f(b.seed, i * 13 + 4);
            b.size[i] = 0.72f + 0.55f * Hash.f(b.seed, i * 13 + 5);
            b.delay[i] = Hash.f(b.seed, i * 13 + 6) * 0.1f * (float) b.dur;
            b.yaw0[i] = (float) (Hash.f(b.seed, i * 13 + 7) * TAU);
            b.pit0[i] = (float) (Hash.f(b.seed, i * 13 + 8) * TAU * 0.5);
            b.rol0[i] = (float) (Hash.f(b.seed, i * 13 + 9) * TAU);
            b.spin[i] = Hash.s(b.seed, i * 13 + 10) * 5.5f + (Hash.s(b.seed, i * 13 + 10) < 0 ? -2f : 2f);
            b.spin2[i] = Hash.s(b.seed, i * 13 + 11) * 3.5f;
            b.phase[i] = Hash.f(b.seed, i * 13 + 12);
        }
    }

    // ------------------------------------------------------------------ per piece animation

    /** Evaluates piece i of burst b at the given age (ticks). Fills px,py,pz (position), cs (scale), ca (alpha), ct (seconds). */
    private boolean eval(Burst b, int i, double age) {
        double a = age - b.delay[i];
        if (a < 0) return false;
        double span = Math.max(1.0, b.dur - b.delay[i]);
        double p = a / span;
        if (p >= 1.0) return false;
        double t = a / 20.0;
        ct = t;
        double k = 2.6;
        double disp = b.spd[i] * b.speed * Math.max(0.6, Math.min(2.0, Math.sqrt(b.scale))) * 1.8 * (1.0 - Math.exp(-k * t)) / k;
        double g = GRAVITY[b.mode];
        px = b.x + b.dx[i] * disp;
        py = b.y + b.dy[i] * disp - 0.5 * g * t * t * Math.min(1.0, b.speed);
        pz = b.z + b.dz[i] * disp;
        double grow = Hash.smooth((float) (p / 0.10));
        double shrink = 1.0 - 0.72 * Hash.smooth((float) ((p - 0.2) / 0.8));
        cs = b.scale * b.size[i] * grow * shrink;
        ca = 1.0 - Hash.smooth((float) ((p - 0.45) / 0.55));
        return cs > 1e-4 && ca > 0.004;
    }

    // ------------------------------------------------------------------ colours

    private void setupPalette(int m, double time) {
        if (colorMode.is("Auto")) pal.set("Gradient", AUTO_A[m], AUTO_B[m], 0.6, time);
        else pal.set(colorMode.get(), c1.rgb(), c2.rgb(), flow.get(), time);
    }

    private int col(double phase, double alpha) { return pal.at(phase, (float) alpha); }

    // ------------------------------------------------------------------ render

    @Override
    public void onWorldRender() {
        double time = ticks + Gfx.partial;
        boolean any = false;
        for (Burst b : bursts) if (b.active) { any = true; break; }
        if (!any) return;
        boolean xr = false;

        // pass 1: solid bodies (alpha layer)
        boolean opened = false;
        for (Burst b : bursts) {
            if (!b.active || b.mode > 3) continue;
            double age = ticks - b.start + Gfx.partial;
            if (age >= b.dur) continue;
            setupPalette(b.mode, time);
            if (!opened) { Gfx.use(false, xr); opened = true; }
            for (int i = 0; i < b.n; i++) {
                if (!Gfx.hasBudget(260)) break;
                if (eval(b, i, age)) body(b, i);
            }
        }

        // pass 2: glow, lightning, shockwave (additive layer)
        opened = false;
        for (Burst b : bursts) {
            if (!b.active) continue;
            double age = ticks - b.start + Gfx.partial;
            if (age >= b.dur) continue;
            setupPalette(b.mode, time);
            if (!opened) { Gfx.use(true, xr); opened = true; }
            if (b.mode == 4) lightning(b, age);
            else if (b.mode == 5) shockwaves(b, age, Math.max(1, Math.min(4, count.intValue() / 4)), 2.4);
            else if (glow.get()) {
                for (int i = 0; i < b.n; i++) {
                    if (!Gfx.hasBudget(120)) break;
                    if (eval(b, i, age)) halo(b, i);
                }
            }
            if (b.kill) killExtras(b, age);
        }
    }

    // ---- bodies

    private void body(Burst b, int i) {
        double ph = b.phase[i];
        switch (b.mode) {
            case 0 -> {                                                    // star
                Xf.set(px, py, pz, b.yaw0[i] + b.spin[i] * ct, b.pit0[i] + b.spin2[i] * ct, b.rol0[i] + b.spin[i] * ct * 0.5, cs * 0.36);
                Models.star(col(ph, ca));
            }
            case 1 -> {                                                    // skull, always looking at the player
                double yaw = Math.atan2(Gfx.camX - px, Gfx.camZ - pz) + Math.sin(ct * 3.1 + ph * 6) * 0.45;
                double pit = Math.sin(ct * 2.3 + ph * 9) * 0.35 - 0.1;
                Xf.set(px, py, pz, yaw, pit, Math.sin(ct * 2.0 + ph * 5) * 0.3, cs * 0.40);
                int bone = colorMode.is("Auto") ? BONE : ColorUtil.lerp(0xFF000000 | BONE, 0xFF000000 | (pal.at(ph) & 0xFFFFFF), 0.45f) & 0xFFFFFF;
                Models.skull(ColorUtil.withAlpha(bone, Math.round(255f * (float) ca)));
            }
            case 2 -> {                                                    // heart
                double beat = 1.0 + 0.10 * Math.sin(ct * 14 + ph * 6);
                double yaw = b.yaw0[i] + b.spin[i] * ct * 0.5;
                Xf.set(px, py, pz, yaw, Math.sin(ct * 2 + ph * 4) * 0.35, Math.sin(ct * 1.7 + ph * 8) * 0.4, cs * 0.32 * beat);
                int detail = (b.n > 14 || Gfx.distToCamera(px, py, pz) > 14) ? 2 : 1;
                Models.heart(col(ph, ca), detail);
            }
            default -> {                                                   // crystal shard, points along its flight direction
                double dx = b.dx[i], dy = b.dy[i], dz = b.dz[i];
                double pitch = Math.acos(Math.max(-1.0, Math.min(1.0, dy))) + Math.sin(ct * 4 + ph * 7) * 0.25;
                double yaw = Math.atan2(dx, dz);
                double s = cs * 0.32;
                Xf.set(px, py, pz, yaw, pitch, b.spin[i] * ct * 0.6, s * 0.55, s, s * 0.55);
                Models.crystal(5, 0.34, 0.36, 1.0, 0.75, b.seed + i, col(ph, ca * 0.92));
            }
        }
    }

    // ---- glow around bodies

    private void halo(Burst b, int i) {
        double ph = b.phase[i];
        float tw = (0.75f + 0.25f * (float) Math.sin(ct * 18 + ph * 40)) * (0.45f + 0.55f * Hash.smooth((float) (ct * 5)));
        switch (b.mode) {
            case 0 -> {
                Models.glow(px, py, pz, cs * 0.80, col(ph, ca * 0.42 * tw));
                Models.sparkle(px, py, pz, cs * 0.75, ct * 2.5 + ph * 6, ColorUtil.withAlpha(0xFFFFFF, Math.round(200f * (float) ca * tw)));
            }
            case 1 -> {
                double yaw = Math.atan2(Gfx.camX - px, Gfx.camZ - pz) + Math.sin(ct * 3.1 + ph * 6) * 0.45;
                double pit = Math.sin(ct * 2.3 + ph * 9) * 0.35 - 0.1;
                Xf.set(px, py, pz, yaw, pit, Math.sin(ct * 2.0 + ph * 5) * 0.3, cs * 0.40);
                Models.skullEyes(col(ph, ca * 0.9), 1.0);
                Models.glow(px, py + cs * 0.1, pz, cs * 0.75, col(ph, ca * 0.14));
            }
            case 2 -> Models.glow(px, py, pz, cs * 0.72, col(ph, ca * 0.36 * tw));
            default -> {
                Models.glow(px, py, pz, cs * 0.62, col(ph, ca * 0.34 * tw));
                Models.sparkle(px, py, pz, cs * 0.55, ph * 6, ColorUtil.withAlpha(0xFFFFFF, Math.round(150f * (float) ca * tw)));
            }
        }
    }

    // ---- lightning bolts

    private void lightning(Burst b, double age) {
        final int K = 9;
        int frame = (int) Math.floor(age * 0.55);
        for (int i = 0; i < b.n; i++) {
            if (!Gfx.hasBudget(80)) break;
            if (!eval(b, i, age)) continue;
            double ddx = b.dx[i], ddy = b.dy[i], ddz = b.dz[i];
            double len = (0.55 + 0.45 * b.size[i]) * b.scale * (1.0 - 0.35 * Hash.smooth((float) (age / b.dur)));
            double hx = px, hy = py, hz = pz;
            double tx = hx - ddx * len, ty = hy - ddy * len, tz = hz - ddz * len;
            // two perpendicular axes for the zigzag
            double ux = -ddz, uy = 0, uz = ddx;
            double ul = Math.sqrt(ux * ux + uz * uz);
            if (ul < 1e-4) { ux = 1; uz = 0; ul = 1; }
            ux /= ul; uz /= ul;
            double vx = ddy * uz - ddz * uy, vy = ddz * ux - ddx * uz, vz = ddx * uy - ddy * ux;
            int sd = b.seed * 131 + i * 17 + frame * 7;
            for (int j = 0; j < K; j++) {
                double f = j / (double) (K - 1);
                double amp = 0.20 * len * Math.sin(Math.PI * f) * (j == 0 || j == K - 1 ? 0 : 1);
                double ja = Hash.s(sd, j * 2) * amp, jb = Hash.s(sd, j * 2 + 1) * amp;
                pts[j * 3] = tx + (hx - tx) * f + ux * ja + vx * jb;
                pts[j * 3 + 1] = ty + (hy - ty) * f + uy * ja + vy * jb;
                pts[j * 3 + 2] = tz + (hz - tz) * f + uz * ja + vz * jb;
            }
            double w = 0.032 * b.scale * (1.0 - 0.45 * Hash.smooth((float) (age / b.dur)));
            Gfx.taper(hw, K, w * 0.15, w);
            double phase = b.phase[i];
            float a = (float) ca;
            if (glow.get()) {
                for (int j = 0; j < K; j++) hw2[j] = hw[j] * 3.4;
                Gfx.ribbon(pts, K, hw2, Gfx.FACE_CAMERA, 0, 0, 0, pal, a * 0.04f, a * 0.28f, phase);
            }
            Gfx.ribbon(pts, K, hw, Gfx.FACE_CAMERA, 0, 0, 0, pal, a * 0.15f, a, phase);
            for (int j = 0; j < K; j++) hw2[j] = hw[j] * 0.38;
            Gfx.ribbon(pts, K, hw2, Gfx.FACE_CAMERA, 0, 0, 0, white, a * 0.2f, a * 0.95f, 0);
            if (glow.get()) Models.glow(hx, hy, hz, 0.22 * b.scale, col(phase, a * 0.5));
        }
    }

    // ---- shockwave rings

    private void shockwaves(Burst b, double age, int rings, double maxR) {
        double base = b.scale * maxR * (b.mode == 5 ? b.speed : 1.0);
        for (int k = 0; k < rings; k++) {
            double ringDur = b.dur * 0.82;
            double q = (age - k * b.dur * 0.11) / ringDur;
            if (q <= 0 || q >= 1) continue;
            double e = 1.0 - Math.pow(1.0 - q, 3.0);
            double R = 0.12 + e * base;
            float a = (float) Math.pow(1.0 - q, 1.35);
            double ph = b.phase[k % MAX_PARTS] + k * 0.17;
            double ux, uy, uz, vx, vy, vz;
            switch (k % 3) {
                case 0 -> { ux = Gfx.rightX; uy = Gfx.rightY; uz = Gfx.rightZ; vx = Gfx.upX; vy = Gfx.upY; vz = Gfx.upZ; }
                case 1 -> { ux = 1; uy = 0; uz = 0; vx = 0; vy = 0; vz = 1; }
                default -> {
                    double c = 0.5, s = 0.866;
                    ux = Gfx.rightX; uy = Gfx.rightY; uz = Gfx.rightZ;
                    vx = Gfx.upX * c + Gfx.lookX * s; vy = Gfx.upY * c + Gfx.lookY * s; vz = Gfx.upZ * c + Gfx.lookZ * s;
                }
            }
            double band = R * (0.20 + 0.10 * (1 - q));
            Gfx.annulus(b.x, b.y, b.z, ux, uy, uz, vx, vy, vz, R - band, R, 0, TAU, 72, col(ph, 0), col(ph, a * 0.85f));
            Gfx.annulus(b.x, b.y, b.z, ux, uy, uz, vx, vy, vz, R, R + R * 0.05 + 0.01, 0, TAU, 72, col(ph + 0.1, a), ColorUtil.withAlpha(0xFFFFFF, 0));
            if (glow.get()) {
                Gfx.annulus(b.x, b.y, b.z, ux, uy, uz, vx, vy, vz, R + R * 0.05, R + band * 1.4, 0, TAU, 72, col(ph, a * 0.22f), col(ph, 0));
            }
        }
    }

    // ---- extra flash + ring that makes a kill read bigger

    private void killExtras(Burst b, double age) {
        double p = age / b.dur;
        float f = (float) Math.pow(1.0 - Math.min(1.0, p / 0.45), 2.0);
        if (f > 0.01f) {
            Models.glow(b.x, b.y, b.z, (0.9 + 1.4 * Hash.smooth((float) (p / 0.45))) * b.scale, col(0.1, 0.34 * f));
            Models.sparkle(b.x, b.y, b.z, (1.0 + 1.6 * (1 - f)) * b.scale, age * 0.05, ColorUtil.withAlpha(0xFFFFFF, Math.round(150f * f)));
        }
        if (b.mode != 5) shockwaves(b, age, 2, 2.6);
    }
}
