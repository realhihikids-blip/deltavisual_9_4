package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Modules;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.render.Gfx;
import dev.deltavisuals.util.ColorUtil;
import dev.deltavisuals.util.Hash;
import dev.deltavisuals.util.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Cosmetic marker around the entity you hit / look at. Pure render: every mode is built from solid
 * geometry (ribbons, bands, spheres, tubes); no particles.
 */
public final class TargetEsp extends Module {
    public static final String[] MODES = {"Arcs", "Petals", "Rings", "Mechanical", "Lightning", "Saturn", "Ghosts", "Helix"};
    private static final double TAU = Math.PI * 2;

    private final Setting.Mode mode, colorMode;
    private final Setting.Num count, radius, height, speed, size, thickness, opacity, flow;
    private final Setting.Color c1, c2;
    private final Setting.Bool glow, lookAt, kill, instantKill;

    private final Palette pal = new Palette();
    private final double[] pts = new double[3 * 160];
    private final double[] hw = new double[160];

    private LivingEntity target;
    private int ticks, lastContact;
    private float appear, prevAppear, lastHp = 20f;

    public TargetEsp() {
        super("Target ESP", "Solid 3D marker around your current target (no particles)", Category.VISUALS, false);
        mode = add(new Setting.Mode("Mode", MODES, 0));
        count = add(new Setting.Num("Count", 1, 12, 1, 5));
        radius = add(new Setting.Num("Radius", 0.4, 3, 0.05, 1.0));
        height = add(new Setting.Num("Height", 0, 2, 0.05, 0.5));
        speed = add(new Setting.Num("Speed", 0.1, 4, 0.1, 1.0, "x"));
        size = add(new Setting.Num("Size", 0.3, 3, 0.05, 1.0));
        thickness = add(new Setting.Num("Thickness", 0.2, 3, 0.05, 1.0));
        opacity = add(new Setting.Num("Opacity", 10, 100, 5, 85, "%"));
        colorMode = add(new Setting.Mode("Color Mode", Palette.MODES, 0));
        c1 = add(new Setting.Color("Main Color", 0xFF33CCFF, false));
        c2 = add(new Setting.Color("Second Color", 0xFFFF4DA6, false));
        c2.visibleIf(() -> colorMode.is("Gradient") || colorMode.is("Neon"));
        c1.visibleIf(() -> !colorMode.is("Rainbow") && !colorMode.is("Health") && !colorMode.is("Fire"));
        flow = add(new Setting.Num("Color Flow Speed", 0.2, 5, 0.1, 1, "x"));
        flow.visibleIf(() -> !colorMode.is("Static") && !colorMode.is("Health"));
        glow = add(new Setting.Bool("Glow", true));
        lookAt = add(new Setting.Bool("Target Under Crosshair", true));
        kill = add(new Setting.Bool("Trigger Kill Effect", true));
        instantKill = add(new Setting.Bool("Detect Instant Removal", true));
        add(new Setting.Info("Note", () -> "Count: petals / rings / bolts / ghosts. Arcs always use 4"));
    }

    @Override public void onDisable() { target = null; appear = prevAppear = 0; }

    /** Called from the AttackEntityCallback in Modules. */
    public void onAttack(Entity e) {
        if (!isEnabled() || !(e instanceof LivingEntity le)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (le == mc.player) return;
        if (target != le) lastHp = le.getHealth();
        target = le;
        lastContact = ticks;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) { target = null; appear = prevAppear = 0; return; }
        if (mc.isPaused()) return;
        ticks++;
        prevAppear = appear;

        if (lookAt.get() && mc.targetedEntity instanceof LivingEntity le && le != mc.player && le.isAlive()) {
            if (target != le) lastHp = le.getHealth();
            target = le;
            lastContact = ticks;
        }
        boolean has = target != null && ticks - lastContact < 60 && target.squaredDistanceTo(mc.player) < 64 * 64;

        boolean dead = false;
        if (target != null) {
            dead = target.isDead() || target.getHealth() <= 0f;
            // Some servers delete a killed player at once, so the client never sees 0 health or the death animation.
            // A low-health target that disappears right after we fought it counts as killed.
            if (!dead && instantKill.get() && target.isRemoved() && lastHp <= 6f && ticks - lastContact < 40) dead = true;
            if (!dead && !target.isRemoved()) lastHp = target.getHealth();
        }
        if (target != null && dead) {
            if (kill.get() && ticks - lastContact < 100 && Modules.KILL != null && Modules.KILL.isEnabled()) Modules.KILL.trigger(target);
            target = null;
            has = false;
        }
        appear += has ? 0.1f : -0.07f;
        appear = Hash.clamp01(appear);
        if (!has && appear <= 0f) target = null;
    }

    @Override
    public void onWorldRender() {
        LivingEntity t = target;
        if (t == null) return;
        float a = prevAppear + (appear - prevAppear) * Gfx.partial;
        if (a <= 0.003f) return;
        float ease = Hash.smooth(a);
        float alpha = ease * opacity.floatValue() / 100f;
        double scale = 0.6 + 0.4 * ease;

        Vec3d p = t.getLerpedPos(Gfx.partial);
        double w = t.getWidth(), h = t.getHeight();
        double time = (ticks + Gfx.partial) * speed.get();
        float hp = t.getMaxHealth() > 0 ? t.getHealth() / t.getMaxHealth() : 1f;
        pal.set(colorMode.get(), c1.rgb(), c2.rgb(), flow.get(), ticks + Gfx.partial);
        pal.health = hp;

        double R = (w * 0.5 + 0.3) * radius.get() * scale;
        double cx = p.x, cy = p.y, cz = p.z;
        double midY = cy + h * height.get();
        int n = count.intValue();

        switch (mode.index()) {
            case 0 -> arcs(cx, midY, cz, w, h, time, alpha, scale);
            case 1 -> petals(cx, cy, cz, h, R, n, time, alpha, scale);
            case 2 -> rings(cx, cy, cz, h, R, n, time, alpha);
            case 3 -> mechanical(cx, midY, cz, R, time, alpha);
            case 4 -> lightning(cx, cy, cz, h, R, n, time, alpha);
            case 5 -> saturn(cx, midY, cz, R, time, alpha, scale);
            case 6 -> ghosts(cx, cy, cz, h, R, Math.max(3, Math.min(6, n)), time, alpha, scale);
            default -> helix(cx, cy, cz, h, R, time, alpha);
        }
    }

    // ---------------------------------------------------------------- helpers

    private void layer(boolean additive) { Gfx.use(additive, false); }

    /** Ribbon with an optional soft glow pass underneath. */
    private void ribbon(int nPts, int face, double fx, double fy, double fz, float aStart, float aEnd, double phase, double glowScale) {
        if (glow.get()) {
            layer(true);
            double[] g = glowW;
            for (int i = 0; i < nPts; i++) g[i] = hw[i] * glowScale;
            Gfx.ribbon(pts, nPts, g, face, fx, fy, fz, pal, aStart * 0.22f, aEnd * 0.22f, phase);
        }
        layer(glow.get());
        Gfx.ribbon(pts, nPts, hw, face, fx, fy, fz, pal, aStart, aEnd, phase);
    }
    private final double[] glowW = new double[160];

    // ---------------------------------------------------------------- 1. arcs (billboard)

    private void arcs(double cx, double cy, double cz, double w, double h, double time, float alpha, double scale) {
        double dist = Gfx.distToCamera(cx, cy, cz);
        double s = Math.max(w, h * 0.65) * 0.85 * size.get() * scale * (1.0 + 0.035 * dist);
        // pull the billboard in front of the body so the model does not cut it
        double dx = Gfx.camX - cx, dy = Gfx.camY - cy, dz = Gfx.camZ - cz, dl = Math.max(1e-6, Math.sqrt(dx * dx + dy * dy + dz * dz));
        double pull = Math.min(w * 0.5 + 0.25, dl * 0.5);
        double px = cx + dx / dl * pull, py = cy + dy / dl * pull, pz = cz + dz / dl * pull;
        double pulse = 1.0 + 0.07 * Math.sin(time * 0.15);
        double band = s * 0.13 * thickness.get();
        double rot = time * 0.04;
        for (int k = 0; k < 4; k++) {
            double a0 = rot + k * Math.PI / 2 - 0.45;
            double sweep = 0.9 + 0.35 * Math.sin(time * 0.12 + k * 1.3);
            double r1 = s * pulse, r0 = r1 - band;
            if (glow.get()) {
                layer(true);
                Gfx.annulus(px, py, pz, Gfx.rightX, Gfx.rightY, Gfx.rightZ, Gfx.upX, Gfx.upY, Gfx.upZ,
                    r0 - band * 0.8, r1 + band * 0.8, a0, sweep, 28, pal.at(k * 0.25, 0f), pal.at(k * 0.25, alpha * 0.18f));
            }
            layer(glow.get());
            Gfx.annulus(px, py, pz, Gfx.rightX, Gfx.rightY, Gfx.rightZ, Gfx.upX, Gfx.upY, Gfx.upZ,
                r0, r1, a0, sweep, 28, pal.at(k * 0.25, alpha * 0.25f), pal.at(k * 0.25 + 0.1, alpha));
        }
    }

    // ---------------------------------------------------------------- 2. petals

    private void petals(double cx, double cy, double cz, double h, double R, int n, double time, float alpha, double scale) {
        double open = 0.2 + 0.8 * (0.5 + 0.5 * Math.sin(time * 0.07));
        double alphaOpen = open * 1.15;
        int K = 12;
        double len = h * 0.95 * size.get() * scale;
        double maxW = 0.24 * size.get() * thickness.get() * scale;
        for (int j = 0; j < n; j++) {
            double th = j * TAU / n + time * 0.012;
            double rdx = Math.cos(th), rdz = Math.sin(th);
            double x = cx + rdx * R * 0.25, y = cy + 0.08, z = cz + rdz * R * 0.25;
            for (int k = 0; k < K; k++) {
                double s = k / (double) (K - 1);
                pts[k * 3] = x; pts[k * 3 + 1] = y; pts[k * 3 + 2] = z;
                double phi = alphaOpen * (0.25 + 1.0 * s);           // bends outward along the petal
                double step = len / (K - 1);
                x += rdx * Math.sin(phi) * step * R * 1.1;
                z += rdz * Math.sin(phi) * step * R * 1.1;
                y += Math.cos(phi) * step;
                hw[k] = maxW * Math.pow(Math.sin(Math.PI * Math.min(1.0, s * 0.92 + 0.04)), 0.8);
            }
            ribbon(K, Gfx.FACE_FIXED, -rdz, 0, rdx, alpha * 0.35f, alpha, j / (double) n, 1.6);
        }
    }

    // ---------------------------------------------------------------- 3. rings with trail

    private void rings(double cx, double cy, double cz, double h, double R, int n, double time, float alpha) {
        double halfH = 0.035 * thickness.get() * size.get();
        int trail = 6;
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < trail; k++) {
                double ph = time * 0.07 + i * TAU / n - k * 0.16;
                double yf = 0.5 + 0.5 * Math.sin(ph);
                float aa = alpha * (float) Math.pow(1.0 - k / (double) trail, 1.6);
                double y = cy + 0.05 + yf * (h - 0.1);
                double hh = halfH * (1.0 - 0.08 * k);
                int c = pal.at(i / (double) n + k * 0.02, aa);
                int cd = pal.at(i / (double) n + k * 0.02, aa * 0.35f);
                if (glow.get()) {
                    layer(true);
                    Gfx.band(cx, y, cz, R, -hh * 3, hh * 3, 40, ColorUtil.withAlpha(c, 0), ColorUtil.mulAlpha(cd, 0.5f));
                    Gfx.band(cx, y, cz, R, -hh * 3, hh * 3, 40, ColorUtil.mulAlpha(cd, 0.5f), ColorUtil.withAlpha(c, 0));
                }
                layer(glow.get());
                Gfx.band(cx, y, cz, R, -hh, hh, 40, c, c);
                Gfx.ringFlat(cx, y + hh, cz, R - hh * 1.6, R + hh * 1.6, 0, TAU, 40, cd, c);
            }
        }
    }

    // ---------------------------------------------------------------- 4. mechanical rings

    private void mechanical(double cx, double cy, double cz, double R, double time, float alpha) {
        double th = thickness.get();
        double[][] axes = {{0}, {0.18}, {-0.18}};
        for (int r = 0; r < 3; r++) {
            double tilt = axes[r][0];
            double vy = Math.sin(tilt), vz = Math.cos(tilt);
            double rad = R * (0.75 + 0.35 * r);
            int cA = pal.at(r * 0.33, alpha), cB = pal.at(r * 0.33 + 0.15, alpha * 0.35f);
            layer(glow.get());
            if (r == 0) {
                Gfx.gear(cx, cy, cz, 1, 0, 0, 0, vy, vz, rad * 0.86, rad, rad * 0.12 * th, 12, time * 0.04, cB, cA);
            } else if (r == 1) {
                double rot = -time * 0.03;
                for (int s = 0; s < 6; s++) {
                    Gfx.annulus(cx, cy, cz, 1, 0, 0, 0, vy, vz, rad - 0.045 * th, rad + 0.045 * th, rot + s * TAU / 6, TAU / 6 * 0.65, 10, cA, cA);
                }
                Gfx.ringFlat(cx, cy + 0.0, cz, rad * 0.93, rad * 0.94, 0, TAU, 40, cB, cB);
            } else {
                double rot = time * 0.015;
                Gfx.annulus(cx, cy, cz, 1, 0, 0, 0, vy, vz, rad - 0.015 * th, rad + 0.015 * th, 0, TAU, 48, cB, cA);
                for (int k = 0; k < 24; k++) {
                    double long_ = (k % 6 == 0) ? 0.12 : 0.06;
                    Gfx.annulus(cx, cy, cz, 1, 0, 0, 0, vy, vz, rad, rad + long_ * th, rot + k * TAU / 24, 0.035, 2, cA, cA);
                }
            }
            if (glow.get()) {
                layer(true);
                Gfx.annulus(cx, cy, cz, 1, 0, 0, 0, vy, vz, rad - 0.12 * th, rad + 0.12 * th, 0, TAU, 48,
                    ColorUtil.withAlpha(cA, 0), ColorUtil.mulAlpha(cB, 0.4f));
            }
        }
    }

    // ---------------------------------------------------------------- 5. lightning

    private void lightning(double cx, double cy, double cz, double h, double R, int n, double time, float alpha) {
        int K = 9;
        double cycle = time * 0.34;
        int seedBase = (int) Math.floor(cycle);
        float f = 1f - (float) Math.pow(cycle - Math.floor(cycle), 2.0);       // flash decay
        for (int j = 0; j < n; j++) {
            int seed = seedBase * 31 + j * 977;
            double th = Hash.f(seed, 1) * TAU;
            double y0 = cy + Hash.f(seed, 2) * h * 0.4;
            double len = h * (0.5 + 0.7 * Hash.f(seed, 3));
            boolean down = Hash.f(seed, 4) > 0.5;
            for (int k = 0; k < K; k++) {
                double s = k / (double) (K - 1);
                double ang = th + Hash.s(seed, 10 + k) * 0.7;
                double rad = R * (0.75 + 0.55 * Hash.f(seed, 30 + k));
                pts[k * 3] = cx + Math.cos(ang) * rad;
                pts[k * 3 + 1] = down ? y0 + len * (1 - s) : y0 + len * s;
                pts[k * 3 + 2] = cz + Math.sin(ang) * rad;
                hw[k] = 0.035 * thickness.get() * size.get() * (1.0 - 0.7 * s);
            }
            float a = alpha * (0.35f + 0.65f * f);
            ribbon(K, Gfx.FACE_CAMERA, 0, 0, 0, a, a * 0.4f, j / (double) n, 3.2);
            // white-hot core
            layer(true);
            for (int k = 0; k < K; k++) glowW[k] = hw[k] * 0.4;
            Gfx.ribbon(pts, K, glowW, Gfx.FACE_CAMERA, 0, 0, 0, whiten(), a, a * 0.4f, 0);
        }
    }
    private final Palette white = new Palette();
    private Palette whiten() { white.mode = "Static"; white.c1 = 0xFFFFFF; return white; }

    // ---------------------------------------------------------------- 6. saturn

    private void saturn(double cx, double cy, double cz, double R, double time, float alpha, double scale) {
        double rs = 0.28 * size.get() * scale;
        layer(glow.get());
        Gfx.sphere(cx, cy, cz, rs, rs, rs, 12, 18, pal.at(0.0, alpha), pal.at(0.5, alpha), 0, 0);
        if (glow.get()) {
            layer(true);
            Gfx.sphere(cx, cy, cz, rs * 1.3, rs * 1.3, rs * 1.3, 8, 14, pal.at(0.0, alpha * 0.2f), pal.at(0.5, alpha * 0.2f), 0, 0);
        }
        double tilt = 0.45, spin = time * 0.03;
        double cs = Math.cos(spin), sn = Math.sin(spin), ct = Math.cos(tilt), st = Math.sin(tilt);
        double ux = cs, uy = 0, uz = -sn;                   // u rotated about Y
        double vx = ct * sn, vy = st, vz = ct * cs;         // v tilted about X, then rotated about Y
        double rr = Math.max(rs * 1.9, R * 0.9);
        double band = 0.09 * thickness.get() * size.get();
        layer(glow.get());
        Gfx.annulus(cx, cy, cz, ux, uy, uz, vx, vy, vz, rr, rr + band, 0, TAU, 56, pal.at(0.2, alpha * 0.9f), pal.at(0.3, alpha));
        Gfx.annulus(cx, cy, cz, ux, uy, uz, vx, vy, vz, rr + band * 1.4, rr + band * 2.2, 0, TAU, 56, pal.at(0.4, alpha * 0.5f), pal.at(0.5, alpha * 0.6f));
        if (glow.get()) {
            layer(true);
            Gfx.annulus(cx, cy, cz, ux, uy, uz, vx, vy, vz, rr - band, rr + band * 3, 0, TAU, 56,
                pal.at(0.2, alpha * 0.1f), pal.at(0.3, alpha * 0.1f));
        }
    }

    // ---------------------------------------------------------------- 7. ghosts

    private void ghosts(double cx, double cy, double cz, double h, double R, int n, double time, float alpha, double scale) {
        int K = 12;
        double r = 0.11 * size.get() * scale;
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < K; k++) {
                double tt = time - k * 1.1;
                double ang = tt * 0.075 + i * TAU / n;
                double rad = R * (0.95 + 0.2 * Math.sin(tt * 0.05 + i));
                double yf = 0.5 + 0.5 * Math.sin(tt * 0.04 + i * 1.7);
                // reverse so index 0 = tail, K-1 = head
                int idx = K - 1 - k;
                pts[idx * 3] = cx + Math.cos(ang) * rad;
                pts[idx * 3 + 1] = cy + 0.1 + yf * (h - 0.1);
                pts[idx * 3 + 2] = cz + Math.sin(ang) * rad;
                hw[idx] = r * 0.9 * (1.0 - k / (double) K);
            }
            double hx = pts[(K - 1) * 3], hy = pts[(K - 1) * 3 + 1], hz = pts[(K - 1) * 3 + 2];
            ribbon(K, Gfx.FACE_CAMERA, 0, 0, 0, 0f, alpha, i / (double) n, 2.2);
            layer(glow.get());
            Gfx.sphere(hx, hy, hz, r, r * 1.15, r, 7, 10, pal.at(i / (double) n, alpha), pal.at(i / (double) n + 0.2, alpha * 0.8f), 0, 0);
            if (glow.get()) {
                layer(true);
                Gfx.sphere(hx, hy, hz, r * 2.2, r * 2.2, r * 2.2, 6, 9, pal.at(i / (double) n, alpha * 0.18f), pal.at(i / (double) n, alpha * 0.18f), 0, 0);
            }
        }
    }

    // ---------------------------------------------------------------- 8. double helix

    private void helix(double cx, double cy, double cz, double h, double R, double time, float alpha) {
        int K = 44;
        double turns = 2.5;
        for (int strand = 0; strand < 2; strand++) {
            for (int k = 0; k < K; k++) {
                double s = k / (double) (K - 1);
                double ang = s * turns * TAU + time * 0.08 + strand * Math.PI;
                double rad = R * (0.9 + 0.1 * Math.sin(s * 6 + time * 0.1));
                pts[k * 3] = cx + Math.cos(ang) * rad;
                pts[k * 3 + 1] = cy + 0.05 + s * (h * 1.02);
                pts[k * 3 + 2] = cz + Math.sin(ang) * rad;
                hw[k] = 0.05 * thickness.get() * size.get() * Math.pow(Math.sin(Math.PI * Math.min(1.0, s * 0.96 + 0.02)), 0.5);
            }
            ribbon(K, Gfx.FACE_CAMERA, 0, 0, 0, alpha, alpha, strand * 0.5, 2.4);
            if (strand == 0) { System.arraycopy(pts, 0, strandA, 0, K * 3); }
        }
        // rungs between the two strands
        layer(glow.get());
        for (int k = 3; k < K - 2; k += 5) {
            Gfx.line(strandA[k * 3], strandA[k * 3 + 1], strandA[k * 3 + 2], pts[k * 3], pts[k * 3 + 1], pts[k * 3 + 2],
                0.012 * thickness.get(), pal.at(k / (double) K, alpha * 0.6f));
        }
    }
    private final double[] strandA = new double[3 * 160];
}
