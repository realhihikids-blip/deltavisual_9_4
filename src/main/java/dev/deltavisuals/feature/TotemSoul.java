package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.render.Gfx;
import dev.deltavisuals.util.ColorUtil;
import dev.deltavisuals.util.Hash;
import dev.deltavisuals.util.I18n;
import dev.deltavisuals.util.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * When a player's Totem of Undying pops, a "soul" leaves their body and floats away.
 * Pure solid geometry (no particles), purely cosmetic.
 *
 * Triggered from LivingEntityStatusMixin (entity status 35 = totem used).
 *
 * PvP comfort: the soul is drawn without depth write, never blocks hits or the crosshair, gets transparent when it is
 * close to the crosshair or close to the camera, and is limited in count and range.
 */
public final class TotemSoul extends Module {
    public static final String[] STYLES = {"Ghost", "Wisp", "Spiral", "Ascend", "Burst"};
    private static final int WISP = 1, SPIRAL = 2, BURST = 4;
    private static final double TAU = Math.PI * 2;
    private static final int MAX_FX = 4, TAIL = 12;

    private static final class Soul {
        boolean active;
        int style, seed, start;
        double x, y, z, w, h;
    }

    private final Setting.Mode style, colorMode;
    private final Setting.Num duration, size, rise, opacity, flow, range, clearAngle, maxAtOnce;
    private final Setting.Color c1, c2;
    private final Setting.Bool glow, own, onlyMyTargets, fadeAim;

    private final Soul[] souls = new Soul[MAX_FX];
    private final Palette pal = new Palette();
    private final double[] pts = new double[3 * 24];
    private final double[] hw = new double[24];
    private final double[] hwGlow = new double[24];
    private final double[] tmp = new double[3];
    private int ticks, counter;
    private int lastHitId = -1, lastHitTick = -1000;

    public TotemSoul() {
        super("Totem Soul", "A soul flies out of a player when their totem pops. Solid 3D effect, never blocks your aim",
            Category.COMBAT_VISUALS, false);
        style = add(new Setting.Mode("Soul", STYLES, 0));
        duration = add(new Setting.Num("Duration", 1.0, 4.0, 0.1, 2.2, " s"));
        size = add(new Setting.Num("Size", 0.4, 3, 0.05, 1.0));
        rise = add(new Setting.Num("Rise Height", 0.5, 6, 0.1, 2.5, " m"));
        opacity = add(new Setting.Num("Opacity", 10, 100, 5, 80, "%"));
        add(new Setting.Header("Look"));
        colorMode = add(new Setting.Mode("Color Mode", Palette.PLAIN, 1));
        c1 = add(new Setting.Color("Main Color", 0xFF9FE8FF, false));
        c2 = add(new Setting.Color("Second Color", 0xFFB48CFF, false));
        c1.visibleIf(() -> !colorMode.is("Rainbow") && !colorMode.is("Fire"));
        c2.visibleIf(() -> colorMode.is("Gradient") || colorMode.is("Neon"));
        flow = add(new Setting.Num("Color Flow Speed", 0.2, 5, 0.1, 1, "x"));
        flow.visibleIf(() -> !colorMode.is("Static"));
        glow = add(new Setting.Bool("Glow", true));
        add(new Setting.Header("Who"));
        own = add(new Setting.Bool("Show My Own Pops", false));
        onlyMyTargets = add(new Setting.Bool("Only Targets I Hit", false));
        range = add(new Setting.Num("Max Distance", 8, 64, 1, 32, " m"));
        add(new Setting.Header("PvP Comfort"));
        fadeAim = add(new Setting.Bool("Fade Near Crosshair", true));
        clearAngle = add(new Setting.Num("Crosshair Clear Zone", 3, 25, 1, 10, " deg"));
        clearAngle.visibleIf(fadeAim::get);
        maxAtOnce = add(new Setting.Num("Max At Once", 1, 4, 1, 3));
        add(new Setting.Header("Actions"));
        add(new Setting.Action("Preview", this::preview));
        add(new Setting.Info("Note", () -> "Also very close to your camera the soul fades out, so it never covers the screen"));
        for (int i = 0; i < MAX_FX; i++) souls[i] = new Soul();
    }

    @Override public void onDisable() { for (Soul s : souls) s.active = false; }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) { for (Soul s : souls) s.active = false; return; }
        if (!mc.isPaused()) ticks++;
    }

    /** Called from the AttackEntityCallback in Modules. */
    public void onAttack(Entity e) {
        if (!isEnabled() || e == null) return;
        lastHitId = e.getId();
        lastHitTick = ticks;
    }

    /** Called from LivingEntityStatusMixin when an entity plays the totem-pop status. */
    public void onPop(LivingEntity e) {
        if (!isEnabled() || e == null || !(e instanceof PlayerEntity)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (e == mc.player) {
            if (!own.get()) return;
        } else {
            double r = range.get();
            if (e.squaredDistanceTo(mc.player) > r * r) return;
            if (onlyMyTargets.get() && !(e.getId() == lastHitId && ticks - lastHitTick <= 100)) return;
        }
        Vec3d p = e.getEntityPos();
        spawn(p.x, p.y, p.z, e.getWidth(), e.getHeight(), e.getId());
    }

    private void spawn(double x, double y, double z, double w, double h, int id) {
        int limit = maxAtOnce.intValue();
        Soul in = null;
        int activeCount = 0;
        Soul oldest = null;
        for (int i = 0; i < MAX_FX; i++) {
            Soul s = souls[i];
            if (s.active) {
                activeCount++;
                if (oldest == null || s.start < oldest.start) oldest = s;
            } else if (in == null) {
                in = s;
            }
        }
        if (in == null || activeCount >= limit) in = oldest != null ? oldest : souls[0];
        in.active = true;
        in.style = style.index();
        in.start = ticks;
        in.seed = ++counter * 7919 + id;
        in.x = x; in.y = y; in.z = z; in.w = w; in.h = h;
    }

    private void preview() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!isEnabled()) {
            mc.player.sendMessage(Text.literal("[Delta] " + I18n.t("Turn the module on first")), true);
            return;
        }
        spawn(Gfx.camX + Gfx.lookX * 4.0, mc.player.getY(), Gfx.camZ + Gfx.lookZ * 4.0, 0.6, 1.8, 0);
    }

    // ---------------------------------------------------------------- render

    @Override
    public void onWorldRender() {
        double durTicks = duration.get() * 20.0;
        for (Soul s : souls) {
            if (!s.active) continue;
            double age = ticks - s.start + Gfx.partial;
            float p = (float) (age / durTicks);
            if (p >= 1f) { s.active = false; continue; }
            float env = Hash.smooth(p / 0.12f) * (1f - Hash.smooth((p - 0.62f) / 0.38f));
            float a = env * opacity.floatValue() / 100f;
            if (a <= 0.004f) continue;
            double time = ticks + Gfx.partial;
            pal.set(colorMode.get(), c1.rgb(), c2.rgb(), flow.get(), time);
            switch (s.style) {
                case 0 -> ghost(s, p, a, time);
                case 3 -> ascend(s, p, a, time);
                default -> orbs(s, a, age, durTicks);
            }
        }
    }

    private static double clamp(double v, double lo, double hi) { return v < lo ? lo : Math.min(v, hi); }

    private double ease(double pp) { double q = 1 - clamp(pp, 0, 1); return 1 - q * q; }

    /**
     * 0..1 visibility factor so the soul never gets in the way: transparent right next to the camera and
     * (optionally) close to the crosshair.
     */
    private float comfort(double x, double y, double z) {
        double dx = x - Gfx.camX, dy = y - Gfx.camY, dz = z - Gfx.camZ;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1e-4) return 0f;
        double f = clamp((d - 0.9) / 1.6, 0, 1);
        if (fadeAim.get()) {
            double cos = (dx * Gfx.lookX + dy * Gfx.lookY + dz * Gfx.lookZ) / d;
            double ang = Math.acos(clamp(cos, -1, 1));
            ang = Math.max(0, ang - Math.atan2(0.45 * size.get(), d));   // the soul has a size, use its edge
            double lim = Math.toRadians(clearAngle.get());
            double t = clamp((ang - lim * 0.5) / (lim * 0.9), 0, 1);
            f *= 0.10 + 0.90 * (t * t * (3 - 2 * t));
        }
        return (float) f;
    }

    private int dark(float a) { return ColorUtil.withAlpha(0x0B0B1E, Math.round(255f * Hash.clamp01(a * 1.15f))); }

    // ---- Ghost: classic sheet ghost with a wavy tail and eyes

    private void ghost(Soul s, float p, float a, double time) {
        double sc = size.get();
        double cx = s.x + 0.22 * sc * Math.sin(time * 0.09) * (0.3 + p);
        double cz = s.z + 0.22 * sc * Math.cos(time * 0.07) * (0.3 + p);
        double r = 0.26 * sc;
        double headY = s.y + s.h * 0.85 + rise.get() * ease(p);
        a *= comfort(cx, headY - r, cz);
        if (a <= 0.004f) return;

        if (glow.get()) {
            Gfx.use(true, false);
            Gfx.sphere(cx, headY - r * 0.8, cz, r * 1.9, r * 2.6, r * 1.9, 8, 12, pal.at(0.0, a * 0.12f), pal.at(0.5, 0f), 0, 0);
        }
        Gfx.use(glow.get(), false);
        Gfx.sphere(cx, headY, cz, r, r * 1.05, r, 10, 14, pal.at(0.0, a), pal.at(0.3, a * 0.9f), 0.0, 0.0);

        int n = 10;
        for (int i = 0; i < n; i++) {
            double t = i / (double) (n - 1);
            double wave = Math.sin(time * 0.24 + t * 5.0) * 0.11 * sc * t;
            pts[i * 3] = cx + Gfx.rightX * wave;
            pts[i * 3 + 1] = headY - r * 0.55 - t * r * 3.3;
            pts[i * 3 + 2] = cz + Gfx.rightZ * wave;
            hw[i] = r * (1.0 - t * 0.92) * (1.0 + 0.10 * Math.sin(time * 0.35 + i * 2.1));
        }
        Gfx.ribbon(pts, n, hw, Gfx.FACE_CAMERA, 0, 0, 0, pal, a * 0.95f, 0f, 0.15);

        // eyes and mouth: small dark blobs pulled towards the camera
        Gfx.use(false, false);
        double fx = -Gfx.lookX * r * 0.85, fy = -Gfx.lookY * r * 0.85, fz = -Gfx.lookZ * r * 0.85;
        for (int e = -1; e <= 1; e += 2) {
            double ex = cx + fx + Gfx.rightX * 0.36 * r * e + Gfx.upX * 0.12 * r;
            double ey = headY + fy + Gfx.rightY * 0.36 * r * e + Gfx.upY * 0.12 * r;
            double ez = cz + fz + Gfx.rightZ * 0.36 * r * e + Gfx.upZ * 0.12 * r;
            Gfx.sphere(ex, ey, ez, r * 0.13, r * 0.2, r * 0.13, 4, 6, dark(a), dark(a), 0, 0);
        }
        double mx = cx + fx - Gfx.upX * 0.42 * r, my = headY + fy - Gfx.upY * 0.42 * r, mz = cz + fz - Gfx.upZ * 0.42 * r;
        Gfx.sphere(mx, my, mz, r * 0.11, r * 0.15, r * 0.11, 4, 6, dark(a * 0.9f), dark(a * 0.9f), 0, 0);
    }

    // ---- Ascend: a spirit body leaves the player, a ring expands on the ground, sparks rise

    private void ascend(Soul s, float p, float a, double time) {
        double sc = size.get();
        double e = ease(p);
        double cy = s.y + s.h * 0.5 + rise.get() * e;
        double bw = Math.max(0.3, s.w) * 0.55 * sc, bh = Math.max(0.9, s.h) * 0.5 * sc;
        float ab = a * comfort(s.x, cy, s.z);

        if (ab > 0.004f) {
            Gfx.use(glow.get(), false);
            float shrink = 1f - 0.45f * p;
            Gfx.sphere(s.x, cy, s.z, bw * shrink, bh * shrink, bw * shrink, 10, 14, pal.at(0.0, ab * 0.55f), pal.at(0.6, ab * 0.20f), 0.04, time * 0.3);
            Gfx.sphere(s.x, cy + bh * 0.95 * shrink, s.z, bw * 0.62 * shrink, bw * 0.62 * shrink, bw * 0.62 * shrink, 8, 12,
                pal.at(0.1, ab * 0.6f), pal.at(0.5, ab * 0.3f), 0.03, time * 0.25);
            if (glow.get()) {
                Gfx.use(true, false);
                Gfx.sphere(s.x, cy, s.z, bw * 1.8, bh * 1.5, bw * 1.8, 8, 12, pal.at(0.0, ab * 0.10f), pal.at(0.5, 0f), 0, 0);
            }
        }

        // expanding ring on the floor
        double rr = (0.3 + 1.6 * p) * sc;
        float ar = a * (1f - p) * 0.9f;
        if (ar > 0.004f) {
            Gfx.use(true, false);
            int ci = pal.at(0.2, 0f), co = pal.at(0.2, ar);
            Gfx.ringFlat(s.x, s.y + 0.04, s.z, rr * 0.8, rr, 0, TAU, 28, ci, co);
        }

        // rising sparks
        Gfx.use(true, false);
        for (int i = 0; i < 8; i++) {
            double ang = Hash.f(s.seed, i) * TAU + time * 0.04;
            double rad = (0.15 + 0.45 * Hash.f(s.seed, i + 20)) * sc;
            double sy = s.y + Hash.f(s.seed, i + 40) * s.h + rise.get() * e * (0.5 + 0.7 * Hash.f(s.seed, i + 60));
            double sx = s.x + Math.cos(ang) * rad, sz = s.z + Math.sin(ang) * rad;
            float as = a * comfort(sx, sy, sz);
            if (as <= 0.004f) continue;
            double q = 0.035 * sc;
            Gfx.sphere(sx, sy, sz, q, q, q, 4, 6, pal.at(i * 0.1, as), pal.at(i * 0.1 + 0.3, as * 0.7f), 0, 0);
        }
    }

    // ---- Wisp / Spiral / Burst: glowing orbs with a flowing tail

    private void orbs(Soul s, float a, double age, double durTicks) {
        int count = s.style == WISP ? 1 : s.style == SPIRAL ? 3 : 8;
        double headR = (s.style == BURST ? 0.07 : s.style == SPIRAL ? 0.09 : 0.14) * size.get();
        for (int i = 0; i < count; i++) orb(s, i, a, age, durTicks, headR);
    }

    /** Position of orb idx at the given age (ticks) written to tmp. */
    private void path(Soul s, int idx, double age, double durTicks) {
        double pp = clamp(age / durTicks, 0, 1);
        double sc = size.get(), rh = rise.get();
        double cx = s.x, cz = s.z, cy = s.y + s.h * 0.55;
        double e = ease(pp);
        switch (s.style) {
            case WISP -> {
                tmp[0] = cx + Math.sin(age * 0.16) * 0.5 * sc * (0.35 + pp);
                tmp[1] = cy + rh * e;
                tmp[2] = cz + Math.cos(age * 0.13) * 0.5 * sc * (0.35 + pp);
            }
            case SPIRAL -> {
                double ang = age * 0.21 + idx * TAU / 3.0, rad = (0.30 + 0.35 * pp) * sc;
                tmp[0] = cx + Math.cos(ang) * rad;
                tmp[1] = cy - 0.3 + rh * e;
                tmp[2] = cz + Math.sin(ang) * rad;
            }
            default -> {
                double ang = Hash.f(s.seed, idx) * TAU;
                double out = (0.5 + 0.9 * Hash.f(s.seed, idx + 30)) * sc * (1 - Math.pow(1 - pp, 3));
                double up = rh * Math.pow(pp, 1.4) * (0.45 + 0.55 * Hash.f(s.seed, idx + 60));
                tmp[0] = cx + Math.cos(ang) * out + Math.sin(age * 0.2 + idx) * 0.05 * sc;
                tmp[1] = cy + 0.25 * sc * Math.sin(pp * Math.PI) + up;
                tmp[2] = cz + Math.sin(ang) * out + Math.cos(age * 0.2 + idx) * 0.05 * sc;
            }
        }
    }

    private void orb(Soul s, int idx, float a, double age, double durTicks, double headR) {
        int n = TAIL;
        for (int k = 0; k < n; k++) {
            path(s, idx, age - k * 1.0, durTicks);
            int ii = n - 1 - k;                       // index 0 = tail, n-1 = head
            pts[ii * 3] = tmp[0]; pts[ii * 3 + 1] = tmp[1]; pts[ii * 3 + 2] = tmp[2];
        }
        double hx = pts[(n - 1) * 3], hy = pts[(n - 1) * 3 + 1], hz = pts[(n - 1) * 3 + 2];
        float av = a * comfort(hx, hy, hz);
        if (av <= 0.004f) return;
        Gfx.taper(hw, n, 0.0, headR * 0.85);
        double phase = idx * 0.2;
        if (glow.get()) {
            for (int i = 0; i < n; i++) hwGlow[i] = hw[i] * 2.3;
            Gfx.use(true, false);
            Gfx.ribbon(pts, n, hwGlow, Gfx.FACE_CAMERA, 0, 0, 0, pal, 0f, av * 0.18f, phase);
            Gfx.sphere(hx, hy, hz, headR * 2.5, headR * 2.5, headR * 2.5, 6, 10, pal.at(phase, av * 0.14f), pal.at(phase + 0.4, 0f), 0, 0);
        }
        Gfx.use(glow.get(), false);
        Gfx.ribbon(pts, n, hw, Gfx.FACE_CAMERA, 0, 0, 0, pal, 0f, av, phase);
        Gfx.sphere(hx, hy, hz, headR, headR, headR, 6, 10, pal.at(phase, av), pal.at(phase + 0.4, av * 0.9f), 0, 0);
    }
}
