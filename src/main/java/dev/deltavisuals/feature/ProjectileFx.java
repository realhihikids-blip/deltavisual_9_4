package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.render.Comfort;
import dev.deltavisuals.render.Gfx;
import dev.deltavisuals.util.ColorUtil;
import dev.deltavisuals.util.Hash;
import dev.deltavisuals.util.I18n;
import dev.deltavisuals.util.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Cosmetic effects for ender pearls and arrows (all solid geometry, no Minecraft particles):
 *  - a burst when a pearl is thrown / an arrow is shot,
 *  - a ribbon that follows the projectile in flight,
 *  - a predicted flight path while you hold a pearl or draw a bow.
 *
 * Nothing is sent to the server and nothing is changed in the game: projectiles are only observed.
 * PvP comfort: bursts and trails turn transparent next to the camera and (optionally) next to the crosshair,
 * the number of simultaneous bursts is limited, and by default only your own projectiles are shown.
 */
public final class ProjectileFx extends Module {
    public static final String[] PEARL_STYLES = {"Rings", "Sparks", "Vortex"};
    public static final String[] BOW_STYLES = {"Flash", "Shockwave", "Streaks"};
    private static final double TAU = Math.PI * 2;
    private static final int MAX_FX = 8, MAX_TRACKS = 48, CAP = 40, MAX_STEPS = 220, FADE_TICKS = 10;

    private static final class Fx {
        boolean active, arrow;
        int style, seed, start;
        double x, y, z, dx, dy, dz;
    }

    private static final class Track {
        boolean arrow, mine;
        Entity ref;
        final double[] p = new double[CAP * 3];
        int n, stamp, still, endTick = -1;
        void push(double x, double y, double z) {
            if (n == CAP) dropOldest();
            p[n * 3] = x; p[n * 3 + 1] = y; p[n * 3 + 2] = z; n++;
        }
        void dropOldest() {
            if (n == 0) return;
            System.arraycopy(p, 3, p, 0, (n - 1) * 3);
            n--;
        }
    }

    private final Setting.Bool pearlFx, bowFx, pearlTrail, arrowTrail, pearlPath, bowPath, landing, glow, fadeAim;
    private final Setting.Mode pearlStyle, bowStyle, pathStyle, colorMode, who;
    private final Setting.Num duration, size, trailLen, trailWidth, opacity, flow, range, clearAngle, maxAtOnce;
    private final Setting.Color pearlC1, pearlC2, arrowC1, arrowC2;

    private final Fx[] fx = new Fx[MAX_FX];
    private final Map<Integer, Track> tracks = new HashMap<>();
    private final Palette pearlPal = new Palette(), arrowPal = new Palette();
    private final double[] src = new double[CAP * 3 + 6];
    private final double[] dst = new double[(CAP * 2 + 4) * 3];
    private final double[] hw = new double[CAP * 2 + 4];
    private final double[] hwGlow = new double[CAP * 2 + 4];
    private final double[] pts = new double[3 * 24];
    private final double[] pw = new double[24];
    private final double[] path = new double[(MAX_STEPS + 2) * 3];
    private double hitX, hitY, hitZ;
    private boolean pathHit;
    private int pathN;
    private Object lastWorld;
    private int ticks, counter;

    public ProjectileFx() {
        super("Projectile FX", "Pearl throw and bow shot effects, flight trails and a predicted path. Never blocks your aim",
            Category.COMBAT_VISUALS, false);
        add(new Setting.Header("Throw Effects"));
        pearlFx = add(new Setting.Bool("Pearl Throw Effect", true));
        pearlStyle = add(new Setting.Mode("Pearl Style", PEARL_STYLES, 0));
        pearlStyle.visibleIf(pearlFx::get);
        bowFx = add(new Setting.Bool("Bow Shot Effect", true));
        bowStyle = add(new Setting.Mode("Bow Style", BOW_STYLES, 0));
        bowStyle.visibleIf(bowFx::get);
        duration = add(new Setting.Num("Effect Duration", 0.3, 2.0, 0.1, 0.8, " s"));
        size = add(new Setting.Num("Effect Size", 0.4, 3, 0.05, 1.0));

        add(new Setting.Header("Flight Trail"));
        pearlTrail = add(new Setting.Bool("Pearl Trail", true));
        arrowTrail = add(new Setting.Bool("Arrow Trail", true));
        trailLen = add(new Setting.Num("Trail Length", 6, 40, 1, 20, " ticks"));
        trailWidth = add(new Setting.Num("Trail Width", 0.02, 0.4, 0.01, 0.1));

        add(new Setting.Header("Predicted Path"));
        pearlPath = add(new Setting.Bool("Pearl Path", true));
        bowPath = add(new Setting.Bool("Bow Path", true));
        pathStyle = add(new Setting.Mode("Path Style", new String[]{"Solid", "Dashed"}, 0));
        landing = add(new Setting.Bool("Landing Marker", true));

        add(new Setting.Header("Look"));
        colorMode = add(new Setting.Mode("Color Mode", Palette.PLAIN, 1));
        pearlC1 = add(new Setting.Color("Pearl Main Color", 0xFFB05CFF, false));
        pearlC2 = add(new Setting.Color("Pearl Second Color", 0xFF4DE1FF, false));
        arrowC1 = add(new Setting.Color("Arrow Main Color", 0xFFFFD257, false));
        arrowC2 = add(new Setting.Color("Arrow Second Color", 0xFFFF5C5C, false));
        pearlC2.visibleIf(() -> colorMode.is("Gradient") || colorMode.is("Neon"));
        arrowC2.visibleIf(() -> colorMode.is("Gradient") || colorMode.is("Neon"));
        pearlC1.visibleIf(() -> !colorMode.is("Rainbow") && !colorMode.is("Fire"));
        arrowC1.visibleIf(() -> !colorMode.is("Rainbow") && !colorMode.is("Fire"));
        flow = add(new Setting.Num("Color Flow Speed", 0.2, 5, 0.1, 1, "x"));
        flow.visibleIf(() -> !colorMode.is("Static"));
        glow = add(new Setting.Bool("Glow", true));
        opacity = add(new Setting.Num("Opacity", 10, 100, 5, 80, "%"));

        add(new Setting.Header("Who And Comfort"));
        who = add(new Setting.Mode("Show For", new String[]{"Only Me", "Everyone"}, 0));
        range = add(new Setting.Num("Max Distance", 8, 96, 1, 48, " m"));
        range.visibleIf(() -> who.is("Everyone"));
        fadeAim = add(new Setting.Bool("Fade Near Crosshair", true));
        clearAngle = add(new Setting.Num("Crosshair Clear Zone", 3, 25, 1, 8, " deg"));
        clearAngle.visibleIf(fadeAim::get);
        maxAtOnce = add(new Setting.Num("Max Effects At Once", 1, 8, 1, 4));

        add(new Setting.Header("Actions"));
        add(new Setting.Action("Preview Pearl Effect", () -> preview(false)));
        add(new Setting.Action("Preview Bow Effect", () -> preview(true)));
        add(new Setting.Info("Note", () -> "The predicted path is drawn only for your own pearl or bow. Effects fade next to the camera"));
        for (int i = 0; i < MAX_FX; i++) fx[i] = new Fx();
    }

    @Override
    public void onDisable() { clearAll(); }

    private void clearAll() {
        for (Fx f : fx) f.active = false;
        tracks.clear();
    }

    // ---------------------------------------------------------------- tick: find projectiles

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) { clearAll(); return; }
        if (mc.isPaused()) return;
        ticks++;
        if (lastWorld != mc.world) { lastWorld = mc.world; clearAll(); }

        boolean everyone = who.is("Everyone");
        double maxD2 = range.get() * range.get();
        boolean wantPearl = pearlFx.get() || pearlTrail.get();
        boolean wantArrow = bowFx.get() || arrowTrail.get();

        if (wantPearl || wantArrow) {
            for (Entity e : mc.world.getEntities()) {
                boolean pearl = e instanceof EnderPearlEntity;
                boolean arrow = !pearl && e instanceof PersistentProjectileEntity && !(e instanceof TridentEntity);
                if (!pearl && !arrow) continue;
                if (pearl ? !wantPearl : !wantArrow) continue;
                if (e.isRemoved()) continue;
                int id = e.getId();
                Track t = tracks.get(id);
                if (t == null) {
                    if (e.age > 20 || tracks.size() >= MAX_TRACKS) continue;   // old = stuck in a block, or already far along
                    boolean mine = isMine(mc, e);
                    if (!mine && (!everyone || e.squaredDistanceTo(mc.player) > maxD2)) continue;
                    t = new Track();
                    t.arrow = arrow;
                    t.mine = mine;
                    tracks.put(id, t);
                    if (e.age <= 3) spawnFx(mc, e, arrow, mine);   // a projectile that was already flying is not a "throw"
                }
                t.ref = e;
                t.stamp = ticks;
                sample(t, ticks, e.getX(), e.getY(), e.getZ());
            }
        }

        Iterator<Track> it = tracks.values().iterator();
        while (it.hasNext()) {
            Track t = it.next();
            if (t.stamp != ticks) {                       // removed, unloaded or out of range
                t.ref = null;
                if (t.endTick < 0) t.endTick = ticks;
            }
            if (t.endTick >= 0) {
                t.dropOldest();                           // the ribbon retracts after the projectile is gone
                if (ticks - t.endTick > FADE_TICKS || t.n < 2) it.remove();
            }
        }
    }

    private void sample(Track t, int now, double x, double y, double z) {
        boolean moved = true;
        if (t.n > 0) {
            int li = (t.n - 1) * 3;
            double dx = x - t.p[li], dy = y - t.p[li + 1], dz = z - t.p[li + 2];
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > 2500) t.n = 0;                       // teleport: break the ribbon
            else moved = d2 > 1e-4;
        }
        if (moved) {
            t.still = 0;
            t.endTick = -1;
            t.push(x, y, z);
        } else if (++t.still >= 3 && t.endTick < 0) {
            t.endTick = now;                              // an arrow stuck in a block: let the ribbon fade out
        }
    }

    private boolean isMine(MinecraftClient mc, Entity e) {
        Entity owner = e instanceof ProjectileEntity pr ? pr.getOwner() : null;
        if (owner != null) return owner == mc.player;
        return e.age <= 3 && e.squaredDistanceTo(mc.player) < 6.25;   // owner not resolved yet, but it spawned at our hand
    }

    // ---------------------------------------------------------------- bursts

    private void spawnFx(MinecraftClient mc, Entity e, boolean arrow, boolean mine) {
        if (arrow ? !bowFx.get() : !pearlFx.get()) return;
        Vec3d v = e.getVelocity();
        double l = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
        if (l < 1e-4) return;
        double x = e.getX(), y = e.getY(), z = e.getZ();
        if (mine && mc.player != null) {
            // draw it at the hand, not in the middle of the screen
            double yaw = Math.toRadians(mc.player.getYaw());
            Vec3d look = mc.player.getRotationVec(1f);
            x += look.x * 0.8 - Math.cos(yaw) * 0.38;
            y += look.y * 0.8 - 0.28;
            z += look.z * 0.8 - Math.sin(yaw) * 0.38;
        }
        queueFx(x, y, z, v.x / l, v.y / l, v.z / l, arrow);
    }

    private void queueFx(double x, double y, double z, double dx, double dy, double dz, boolean arrow) {
        int limit = (int) maxAtOnce.get();
        Fx free = null, oldest = null;
        int active = 0;
        for (Fx f : fx) {
            if (f.active) {
                active++;
                if (oldest == null || f.start < oldest.start) oldest = f;
            } else if (free == null) {
                free = f;
            }
        }
        Fx s = (free == null || active >= limit) ? (oldest != null ? oldest : fx[0]) : free;
        s.active = true;
        s.arrow = arrow;
        s.style = arrow ? bowStyle.index() : pearlStyle.index();
        s.start = ticks;
        s.seed = ++counter * 7919 + 31;
        s.x = x; s.y = y; s.z = z;
        s.dx = dx; s.dy = dy; s.dz = dz;
    }

    private void preview(boolean arrow) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        if (!isEnabled()) {
            mc.player.sendMessage(Text.literal("[Delta] " + I18n.t("Turn the module on first")), true);
            return;
        }
        Vec3d look = mc.player.getRotationVec(1f);
        Vec3d eye = mc.player.getCameraPosVec(1f);
        queueFx(eye.x + look.x * 4.0, eye.y + look.y * 4.0 - 0.2, eye.z + look.z * 4.0, look.x, look.y, look.z, arrow);
    }

    // ---------------------------------------------------------------- render

    @Override
    public void onWorldRender() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        double time = ticks + Gfx.partial;
        pearlPal.set(colorMode.get(), pearlC1.rgb(), pearlC2.rgb(), flow.get(), time);
        arrowPal.set(colorMode.get(), arrowC1.rgb(), arrowC2.rgb(), flow.get(), time);

        for (Track t : tracks.values()) drawTrail(t);

        double durTicks = duration.get() * 20.0;
        for (Fx f : fx) {
            if (!f.active) continue;
            double age = ticks - f.start + Gfx.partial;
            float p = (float) (age / durTicks);
            if (p >= 1f) { f.active = false; continue; }
            drawFx(f, p, time);
        }

        drawPrediction(mc);
    }

    private float baseAlpha() { return opacity.floatValue() / 100f; }

    // ---- flight trail

    private void drawTrail(Track t) {
        if (t.arrow ? !arrowTrail.get() : !pearlTrail.get()) return;
        int n = Math.min(t.n, (int) trailLen.get());
        if (n < 2) return;
        System.arraycopy(t.p, (t.n - n) * 3, src, 0, n * 3);
        if (t.ref != null && !t.ref.isRemoved()) {
            Vec3d lp = t.ref.getLerpedPos(Gfx.partial);
            src[(n - 1) * 3] = lp.x; src[(n - 1) * 3 + 1] = lp.y; src[(n - 1) * 3 + 2] = lp.z;
        }
        int m = Gfx.smooth(src, n, dst, 2);
        if (m < 2) return;
        double hx = dst[(m - 1) * 3], hy = dst[(m - 1) * 3 + 1], hz = dst[(m - 1) * 3 + 2];
        float a = baseAlpha() * Comfort.factor(hx, hy, hz, 1.2, 2.0, fadeAim.get(), clearAngle.get(), 0.25);
        if (t.endTick >= 0) a *= Math.max(0f, 1f - (ticks + Gfx.partial - t.endTick) / (float) FADE_TICKS);
        if (a <= 0.004f) return;
        Palette pal = t.arrow ? arrowPal : pearlPal;
        double w = trailWidth.get();
        Gfx.taper(hw, m, w * 0.05, w);
        if (glow.get()) {
            for (int i = 0; i < m; i++) hwGlow[i] = hw[i] * 2.6;
            Gfx.use(true, false);
            Gfx.ribbon(dst, m, hwGlow, Gfx.FACE_CAMERA, 0, 0, 0, pal, 0f, a * 0.20f, 0.0);
            halo(hx, hy, hz, w * 4.0, pal.at(0.9, a * 0.5f));
        }
        Gfx.use(glow.get(), false);
        Gfx.ribbon(dst, m, hw, Gfx.FACE_CAMERA, 0, 0, 0, pal, 0f, a, 0.0);
    }

    // ---- bursts

    private static double easeOut(double p) { double q = 1 - Math.min(1, Math.max(0, p)); return 1 - q * q * q; }

    private double ux, uy, uz, vx, vy, vz;

    /** Two unit vectors perpendicular to the direction (and to each other). */
    private void basis(double dx, double dy, double dz) {
        double a, b, c;
        if (Math.abs(dy) > 0.98) { a = 1; b = 0; c = 0; } else { a = dz; b = 0; c = -dx; }
        double l = Math.sqrt(a * a + b * b + c * c);
        ux = a / l; uy = b / l; uz = c / l;
        vx = dy * uz - dz * uy; vy = dz * ux - dx * uz; vz = dx * uy - dy * ux;
    }

    private void drawFx(Fx f, float p, double time) {
        float env = Hash.smooth(p / 0.08f) * (1f - Hash.smooth((p - 0.55f) / 0.45f));
        float a = env * baseAlpha();
        double sc = size.get();
        a *= Comfort.factor(f.x, f.y, f.z, 0.5, 1.2, fadeAim.get(), clearAngle.get(), 0.45 * sc);
        if (a <= 0.004f) return;
        Palette pal = f.arrow ? arrowPal : pearlPal;
        basis(f.dx, f.dy, f.dz);
        if (f.arrow) {
            switch (f.style) {
                case 0 -> flash(f, p, a, pal, sc);
                case 1 -> shock(f, p, a, pal, sc);
                default -> streaks(f, p, a, pal, sc);
            }
        } else {
            switch (f.style) {
                case 0 -> rings(f, p, a, pal, sc);
                case 1 -> sparks(f, p, a, pal, sc);
                default -> vortex(f, p, a, pal, sc);
            }
        }
    }

    private void ring(Fx f, double r, float a, Palette pal, double phase) {
        double band = Math.max(0.03, r * 0.16);
        Gfx.annulus(f.x, f.y, f.z, ux, uy, uz, vx, vy, vz, r - band, r, 0, TAU, 28, pal.at(phase, 0f), pal.at(phase, a));
        Gfx.annulus(f.x, f.y, f.z, ux, uy, uz, vx, vy, vz, r, r + band * 0.6, 0, TAU, 28, pal.at(phase, a), pal.at(phase + 0.1, 0f));
    }

    // pearl: expanding rings with a soft flash
    private void rings(Fx f, float p, float a, Palette pal, double sc) {
        Gfx.use(true, false);
        halo(f.x, f.y, f.z, 0.7 * sc * (1.0 - 0.5 * p), pal.at(0.2, a * 0.55f * (1f - p)));
        for (int k = 0; k < 3; k++) {
            double pk = p * 1.25 - k * 0.16;
            if (pk <= 0 || pk >= 1) continue;
            double r = (0.12 + 1.15 * easeOut(pk)) * sc;
            ring(f, r, a * (float) Math.pow(1.0 - pk, 1.2), pal, k * 0.3);
        }
    }

    // pearl: sparks fly out in a cone around the throw direction
    private void sparks(Fx f, float p, float a, Palette pal, double sc) {
        int n = 12;
        double e = easeOut(p);
        for (int i = 0; i < n; i++) {
            double th = 0.15 + 0.85 * Hash.f(f.seed, i), ph = Hash.f(f.seed, i + 20) * TAU;
            double ca = Math.cos(ph), sa = Math.sin(ph), st = Math.sin(th), ct = Math.cos(th);
            double dx = f.dx * ct + (ux * ca + vx * sa) * st;
            double dy = f.dy * ct + (uy * ca + vy * sa) * st;
            double dz = f.dz * ct + (uz * ca + vz * sa) * st;
            double reach = (0.9 + 1.8 * Hash.f(f.seed, i + 40)) * sc;
            double fall = 0.55 * p * p * sc;
            for (int k = 0; k < 4; k++) {
                double kk = Math.max(0.0, e - (3 - k) * 0.055);        // index 0 = tail
                pts[k * 3] = f.x + dx * reach * kk;
                pts[k * 3 + 1] = f.y + dy * reach * kk - fall * kk;
                pts[k * 3 + 2] = f.z + dz * reach * kk;
                pw[k] = 0.035 * sc * (k / 3.0);
            }
            double hx = pts[9], hy = pts[10], hz = pts[11];
            Gfx.use(true, false);
            Gfx.ribbon(pts, 4, pw, Gfx.FACE_CAMERA, 0, 0, 0, pal, 0f, a, i / (double) n);
            diamond(hx, hy, hz, 0.03 * sc * (1f - 0.6f * p), pal.at(i / (double) n + 0.2, a));
        }
    }

    // pearl: three spiral arms that wind away along the throw direction
    private void vortex(Fx f, float p, float a, Palette pal, double sc) {
        int K = 14;
        double e = easeOut(p);
        for (int arm = 0; arm < 3; arm++) {
            for (int k = 0; k < K; k++) {
                double s = k / (double) (K - 1);
                double dist = s * 1.7 * sc * e;
                double rad = (0.55 * (1 - 0.8 * s) + 0.05) * sc * (0.4 + 0.6 * e);
                double ang = arm * TAU / 3 + s * 5.0 + p * 6.0;
                double ca = Math.cos(ang) * rad, sa = Math.sin(ang) * rad;
                pts[k * 3] = f.x + f.dx * dist + ux * ca + vx * sa;
                pts[k * 3 + 1] = f.y + f.dy * dist + uy * ca + vy * sa;
                pts[k * 3 + 2] = f.z + f.dz * dist + uz * ca + vz * sa;
                pw[k] = 0.05 * sc * (1.0 - 0.75 * s);
            }
            Gfx.use(true, false);
            Gfx.ribbon(pts, K, pw, Gfx.FACE_CAMERA, 0, 0, 0, pal, a, 0f, arm * 0.33);
        }
        halo(f.x, f.y, f.z, 0.5 * sc * (1.0 - p), pal.at(0.1, a * 0.5f));
    }

    // bow: a flash with a cross flare and a short forward streak
    private void flash(Fx f, float p, float a, Palette pal, double sc) {
        Gfx.use(true, false);
        double fade = 1.0 - p;
        halo(f.x, f.y, f.z, 0.75 * sc * (0.5 + 0.5 * fade), pal.at(0.0, a * 0.7f));
        double len = 0.8 * sc * fade;
        for (int k = 0; k < 2; k++) {
            double ang = k * Math.PI / 2 + 0.4;
            double dx = Gfx.rightX * Math.cos(ang) + Gfx.upX * Math.sin(ang);
            double dy = Gfx.rightY * Math.cos(ang) + Gfx.upY * Math.sin(ang);
            double dz = Gfx.rightZ * Math.cos(ang) + Gfx.upZ * Math.sin(ang);
            Gfx.line(f.x - dx * len, f.y - dy * len, f.z - dz * len, f.x + dx * len, f.y + dy * len, f.z + dz * len,
                0.02 * sc, pal.at(0.3 + k * 0.2, a * 0.8f));
        }
        double e = easeOut(p);
        pts[0] = f.x; pts[1] = f.y; pts[2] = f.z;
        pts[3] = f.x + f.dx * 1.2 * sc * e; pts[4] = f.y + f.dy * 1.2 * sc * e; pts[5] = f.z + f.dz * 1.2 * sc * e;
        pw[0] = 0.06 * sc * fade; pw[1] = 0.0;
        Gfx.ribbon(pts, 2, pw, Gfx.FACE_CAMERA, 0, 0, 0, pal, a, 0f, 0.0);
    }

    // bow: a fast shock ring that expands across the shooting direction
    private void shock(Fx f, float p, float a, Palette pal, double sc) {
        Gfx.use(true, false);
        halo(f.x, f.y, f.z, 0.45 * sc * (1.0 - p), pal.at(0.0, a * 0.6f));
        for (int k = 0; k < 2; k++) {
            double pk = p * 1.3 - k * 0.2;
            if (pk <= 0 || pk >= 1) continue;
            double r = (0.08 + 0.85 * easeOut(Math.sqrt(pk))) * sc;
            ring(f, r, a * (float) (1.0 - pk), pal, k * 0.25);
        }
    }

    // bow: speed lines streaming along the shooting direction
    private void streaks(Fx f, float p, float a, Palette pal, double sc) {
        int n = 10;
        double e = easeOut(p);
        Gfx.use(true, false);
        for (int i = 0; i < n; i++) {
            double ph = Hash.f(f.seed, i) * TAU, rr = (0.06 + 0.22 * Hash.f(f.seed, i + 20)) * sc;
            double ox = ux * Math.cos(ph) * rr + vx * Math.sin(ph) * rr;
            double oy = uy * Math.cos(ph) * rr + vy * Math.sin(ph) * rr;
            double oz = uz * Math.cos(ph) * rr + vz * Math.sin(ph) * rr;
            double L = (1.0 + 1.6 * Hash.f(f.seed, i + 40)) * sc;
            double s0 = e * L * 0.35, s1 = e * L;
            pts[0] = f.x + ox + f.dx * s0; pts[1] = f.y + oy + f.dy * s0; pts[2] = f.z + oz + f.dz * s0;
            pts[3] = f.x + ox + f.dx * s1; pts[4] = f.y + oy + f.dy * s1; pts[5] = f.z + oz + f.dz * s1;
            pw[0] = 0.0; pw[1] = 0.022 * sc;
            Gfx.ribbon(pts, 2, pw, Gfx.FACE_CAMERA, 0, 0, 0, pal, 0f, a * (1f - p), i / (double) n);
        }
        halo(f.x, f.y, f.z, 0.35 * sc * (1.0 - p), pal.at(0.0, a * 0.5f));
    }

    // ---------------------------------------------------------------- predicted path

    private void drawPrediction(MinecraftClient mc) {
        ClientPlayerEntity pl = mc.player;
        if (pl == null) return;
        double speed, gravity;
        boolean arrow;
        if (bowPath.get() && pl.isUsingItem() && pl.getActiveItem().isOf(Items.BOW)) {
            float f = pl.getItemUseTime() / 20f;
            f = (f * f + f * 2f) / 3f;
            if (f < 0.1f) return;                         // the bow would not shoot yet
            speed = Math.min(1f, f) * 3.0;
            gravity = 0.05;
            arrow = true;
        } else if (pearlPath.get() && (pl.getMainHandStack().isOf(Items.ENDER_PEARL) || pl.getOffHandStack().isOf(Items.ENDER_PEARL))) {
            speed = 1.5;
            gravity = 0.03;
            arrow = false;
        } else {
            return;
        }
        simulate(mc, pl, speed, gravity);
        if (pathN < 2) return;

        Palette pal = arrow ? arrowPal : pearlPal;
        float base = baseAlpha();
        // the line starts at the hand and joins the real path after a few steps
        double ox = Gfx.rightX * 0.30 - Gfx.upX * 0.24, oy = Gfx.rightY * 0.30 - Gfx.upY * 0.24, oz = Gfx.rightZ * 0.30 - Gfx.upZ * 0.24;
        boolean dashed = pathStyle.is("Dashed");
        for (int i = 1; i < pathN; i++) {
            if (dashed && ((i / 2) & 1) == 1) continue;
            double k0 = Math.max(0.0, 1.0 - (i - 1) / 10.0), k1 = Math.max(0.0, 1.0 - i / 10.0);
            double ax = path[(i - 1) * 3] + ox * k0, ay = path[(i - 1) * 3 + 1] + oy * k0, az = path[(i - 1) * 3 + 2] + oz * k0;
            double bx = path[i * 3] + ox * k1, by = path[i * 3 + 1] + oy * k1, bz = path[i * 3 + 2] + oz * k1;
            float a = base * Comfort.factor(bx, by, bz, 0.5, 1.6, false, 0, 0) * (1f - 0.55f * i / pathN);
            if (a <= 0.004f) continue;
            double t = i / (double) pathN;
            if (glow.get()) {
                Gfx.use(true, false);
                Gfx.line(ax, ay, az, bx, by, bz, 0.035, pal.at(t, a * 0.18f));
            }
            Gfx.use(glow.get(), false);
            Gfx.line(ax, ay, az, bx, by, bz, 0.011, pal.at(t, a));
        }
        if (landing.get() && pathHit) {
            double d = Gfx.distToCamera(hitX, hitY, hitZ);
            if (d < 1e-3) return;
            double s = 1.0 + 0.012 * d;
            double nx = (Gfx.camX - hitX) / d * 0.06, ny = (Gfx.camY - hitY) / d * 0.06, nz = (Gfx.camZ - hitZ) / d * 0.06;
            double mx = hitX + nx, my = hitY + ny, mz = hitZ + nz;
            float a = base * Comfort.factor(mx, my, mz, 0.8, 1.5, false, 0, 0);
            if (a <= 0.004f) return;
            Gfx.use(true, false);
            Gfx.annulus(mx, my, mz, Gfx.rightX, Gfx.rightY, Gfx.rightZ, Gfx.upX, Gfx.upY, Gfx.upZ,
                0.13 * s, 0.20 * s, 0, TAU, 20, pal.at(0.2, a * 0.9f), pal.at(0.6, a * 0.9f));
            Gfx.annulus(mx, my, mz, Gfx.rightX, Gfx.rightY, Gfx.rightZ, Gfx.upX, Gfx.upY, Gfx.upZ,
                0.20 * s, 0.34 * s, 0, TAU, 20, pal.at(0.6, a * 0.35f), pal.at(0.6, 0f));
            diamond(mx, my, mz, 0.045 * s, pal.at(0.4, a));
        }
    }

    /** Vanilla flight physics (drag 0.99, then gravity) from the player's eye, stopping at the first block. */
    private void simulate(MinecraftClient mc, ClientPlayerEntity pl, double speed, double gravity) {
        Vec3d eye = pl.getCameraPosVec(Gfx.partial);
        Vec3d dir = pl.getRotationVec(Gfx.partial);
        Vec3d pv = pl.getVelocity();
        double vx0 = dir.x * speed + pv.x;
        double vy0 = dir.y * speed + (pl.isOnGround() ? 0.0 : pv.y);   // the thrower's own motion is added by the game
        double vz0 = dir.z * speed + pv.z;
        double px = eye.x, py = eye.y - 0.1, pz = eye.z;
        pathN = 0;
        pathHit = false;
        path[pathN * 3] = px; path[pathN * 3 + 1] = py; path[pathN * 3 + 2] = pz; pathN++;
        double bottom = mc.world.getBottomY() - 8;
        for (int i = 0; i < MAX_STEPS; i++) {
            double nx = px + vx0, ny = py + vy0, nz = pz + vz0;
            BlockHitResult r = mc.world.raycast(new RaycastContext(new Vec3d(px, py, pz), new Vec3d(nx, ny, nz),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, pl));
            if (r != null && r.getType() != HitResult.Type.MISS) {
                Vec3d h = r.getPos();
                path[pathN * 3] = h.x; path[pathN * 3 + 1] = h.y; path[pathN * 3 + 2] = h.z; pathN++;
                hitX = h.x; hitY = h.y; hitZ = h.z;
                pathHit = true;
                return;
            }
            px = nx; py = ny; pz = nz;
            path[pathN * 3] = px; path[pathN * 3 + 1] = py; path[pathN * 3 + 2] = pz; pathN++;
            vx0 *= 0.99; vy0 *= 0.99; vz0 *= 0.99;
            vy0 -= gravity;
            if (py < bottom) return;
        }
    }

    // ---------------------------------------------------------------- tiny primitives

    private static void diamond(double x, double y, double z, double s, int c) {
        Gfx.quad(x + Gfx.rightX * s, y + Gfx.rightY * s, z + Gfx.rightZ * s, c,
                 x + Gfx.upX * s, y + Gfx.upY * s, z + Gfx.upZ * s, c,
                 x - Gfx.rightX * s, y - Gfx.rightY * s, z - Gfx.rightZ * s, c,
                 x - Gfx.upX * s, y - Gfx.upY * s, z - Gfx.upZ * s, c);
    }

    /** Soft round glow facing the camera; argb alpha = peak. Needs the additive layer to be selected. */
    private static void halo(double x, double y, double z, double radius, int argb) {
        int mid = ColorUtil.mulAlpha(argb, 0.40f), out = ColorUtil.withAlpha(argb, 0);
        Gfx.annulus(x, y, z, Gfx.rightX, Gfx.rightY, Gfx.rightZ, Gfx.upX, Gfx.upY, Gfx.upZ, 0, radius * 0.36, 0, TAU, 8, argb, mid);
        Gfx.annulus(x, y, z, Gfx.rightX, Gfx.rightY, Gfx.rightZ, Gfx.upX, Gfx.upY, Gfx.upZ, radius * 0.36, radius, 0, TAU, 8, mid, out);
    }
}
