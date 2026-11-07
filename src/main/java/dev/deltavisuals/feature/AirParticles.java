package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.render.Comfort;
import dev.deltavisuals.render.Gfx;
import dev.deltavisuals.util.ColorUtil;
import dev.deltavisuals.util.Hash;
import dev.deltavisuals.util.Palette;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;

/**
 * Soft floating particles around you: fireflies, dust, snow, embers, sparkles, petals.
 * Drawn as small solid shapes through {@link Gfx} (no Minecraft particles), nothing is stored per particle:
 * every position is a function of the clock, wrapped around you inside a box, so it costs almost nothing.
 *
 * PvP comfort: nothing is drawn right next to the camera, near the crosshair the particles fade out,
 * and the whole effect fades away while you fight (you were hit or you hit someone) or while another player
 * is close. Invisible players do not count, so the effect cannot reveal them.
 */
public final class AirParticles extends Module {
    public static final String[] STYLES = {"Fireflies", "Dust", "Snow", "Embers", "Sparkles", "Petals"};
    private static final int FIREFLIES = 0, DUST = 1, SNOW = 2, EMBERS = 3, SPARKLES = 4, PETALS = 5;
    private static final double TAU = Math.PI * 2;
    // default colours of every style (used while Auto Color is on)
    private static final int[] AUTO_1 = {0xFFEB6A, 0xFFF3D6, 0xFFFFFF, 0xFF9A3C, 0x66E8FF, 0xFFB3D4};
    private static final int[] AUTO_2 = {0x8CFF9E, 0xBFD8FF, 0xCFE6FF, 0xFF3C1E, 0xFF7ADE, 0xFFFFFF};
    private static final double[] BASE_SIZE = {0.045, 0.022, 0.040, 0.035, 0.050, 0.065};

    private final Setting.Mode style, colorMode;
    private final Setting.Num amount, radius, size, speed, opacity, flow, clearRadius, clearAngle, hideSecs, playerRange;
    private final Setting.Color c1, c2;
    private final Setting.Bool autoColor, glow, fadeAim, hideCombat, hidePlayers, openSkyOnly;

    private final Palette pal = new Palette();
    private int ticks, lastCombat = -1000;
    private float vis, prevVis;

    public AirParticles() {
        super("Air Particles", "Soft floating particles around you (fireflies, snow, embers...). Fades away in a fight, never in your way",
            Category.WORLD, false);
        add(new Setting.Header("Particles"));
        style = add(new Setting.Mode("Style", STYLES, 0));
        amount = add(new Setting.Num("Amount", 10, 300, 5, 70));
        radius = add(new Setting.Num("Area Radius", 6, 32, 1, 16, " m"));
        size = add(new Setting.Num("Size", 0.3, 3, 0.05, 1.0));
        speed = add(new Setting.Num("Speed", 0.1, 3, 0.1, 1.0, "x"));
        opacity = add(new Setting.Num("Opacity", 10, 100, 5, 70, "%"));
        glow = add(new Setting.Bool("Glow", true));

        add(new Setting.Header("Color"));
        autoColor = add(new Setting.Bool("Auto Color", true));
        colorMode = add(new Setting.Mode("Color Mode", Palette.PLAIN, 1));
        c1 = add(new Setting.Color("Main Color", 0xFFFFEB6A, false));
        c2 = add(new Setting.Color("Second Color", 0xFF8CFF9E, false));
        colorMode.visibleIf(() -> !autoColor.get());
        c1.visibleIf(() -> !autoColor.get() && !colorMode.is("Rainbow") && !colorMode.is("Fire"));
        c2.visibleIf(() -> !autoColor.get() && (colorMode.is("Gradient") || colorMode.is("Neon")));
        flow = add(new Setting.Num("Color Flow Speed", 0.2, 5, 0.1, 1, "x"));
        flow.visibleIf(() -> !autoColor.get() && !colorMode.is("Static"));

        add(new Setting.Header("PvP Comfort"));
        clearRadius = add(new Setting.Num("Clear Around Camera", 1.5, 6, 0.5, 3.0, " m"));
        fadeAim = add(new Setting.Bool("Fade Near Crosshair", true));
        clearAngle = add(new Setting.Num("Crosshair Clear Zone", 4, 30, 1, 12, " deg"));
        clearAngle.visibleIf(fadeAim::get);
        hideCombat = add(new Setting.Bool("Hide In Combat", true));
        hideSecs = add(new Setting.Num("Hide For", 2, 15, 1, 6, " s"));
        hideSecs.visibleIf(hideCombat::get);
        hidePlayers = add(new Setting.Bool("Hide Near Players", true));
        playerRange = add(new Setting.Num("Players Range", 6, 32, 1, 14, " m"));
        playerRange.visibleIf(hidePlayers::get);
        openSkyOnly = add(new Setting.Bool("Only Under Open Sky", false));
    }

    @Override public void onDisable() { vis = prevVis = 0f; }

    /** Called from the AttackEntityCallback in Modules: you hit something, so you are fighting. */
    public void onAttack() { lastCombat = ticks; }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) { vis = prevVis = 0f; return; }
        if (mc.isPaused()) return;
        ticks++;
        prevVis = vis;

        boolean hide = false;
        if (mc.player.hurtTime > 0) lastCombat = ticks;
        if (hideCombat.get() && ticks - lastCombat < hideSecs.get() * 20.0) hide = true;
        if (!hide && hidePlayers.get()) {
            double r = playerRange.get(), r2 = r * r;
            for (AbstractClientPlayerEntity o : mc.world.getPlayers()) {
                if (o == mc.player || o.isSpectator() || o.isInvisible()) continue;   // invisible players must not be given away
                if (o.squaredDistanceTo(mc.player) < r2) { hide = true; break; }
            }
        }
        boolean sky = !openSkyOnly.get() || mc.world.isSkyVisible(mc.player.getBlockPos());
        float target = (!hide && sky) ? 1f : 0f;
        vis += (target - vis) * (target < vis ? 0.35f : 0.06f);   // leave a fight quickly, come back slowly
        if (vis < 0.002f) vis = 0f;
    }

    private static double mod(double a, double l) { return ((a % l) + l) % l; }

    @Override
    public void onWorldRender() {
        float v = prevVis + (vis - prevVis) * Gfx.partial;
        if (v <= 0.01f) return;
        int st = style.index();
        int n = (int) amount.get();
        double R = radius.get(), L = R * 2.0, Hv = Math.max(6.0, R * 0.8);
        double t = (ticks + Gfx.partial) / 20.0 * speed.get();
        double sc = size.get() * BASE_SIZE[st];

        if (autoColor.get()) pal.set("Gradient", AUTO_1[st], AUTO_2[st], 1.0, ticks + Gfx.partial);
        else pal.set(colorMode.get(), c1.rgb(), c2.rgb(), flow.get(), ticks + Gfx.partial);

        boolean additive = st != SNOW && st != PETALS;
        Gfx.use(additive, false);

        double ox = Gfx.camX - R, oy = Gfx.camY - Hv * 0.45, oz = Gfx.camZ - R;
        float master = v * opacity.floatValue() / 100f;
        double clear = clearRadius.get();
        boolean aim = fadeAim.get();
        double zone = clearAngle.get();

        for (int i = 0; i < n; i++) {
            if (!Gfx.hasBudget(40)) break;
            int seed = i * 7919 + 1337;
            double ph = Hash.f(seed, 4) * TAU;
            float rnd = Hash.f(seed, 5);
            double dx, dy, dz;
            float pulse = 1f;
            switch (st) {
                case FIREFLIES -> {
                    dx = Math.sin(t * 0.55 + ph) * 1.4 + t * 0.15;
                    dy = Math.sin(t * 0.40 + ph * 1.7) * 0.7;
                    dz = Math.cos(t * 0.50 + ph * 0.6) * 1.4 + t * 0.10;
                    double q = 0.5 + 0.5 * Math.sin(t * 1.8 + ph * 3.0);
                    pulse = (float) (0.15 + 0.85 * q * q);
                }
                case DUST -> {
                    dx = t * 0.25 + Math.sin(t * 0.30 + ph) * 0.4;
                    dy = Math.sin(t * 0.20 + ph) * 0.3 - t * 0.02;
                    dz = t * 0.12 + Math.cos(t * 0.27 + ph * 1.3) * 0.4;
                    pulse = 0.45f + 0.35f * (float) Math.sin(t * 0.6 + ph);
                }
                case SNOW -> {
                    dx = Math.sin(t * 0.6 + ph) * 0.5 + t * 0.3;
                    dy = -t * 1.0 * (0.7 + 0.6 * rnd);
                    dz = Math.cos(t * 0.5 + ph) * 0.4;
                }
                case EMBERS -> {
                    dx = Math.sin(t * 0.9 + ph) * 0.6;
                    dy = t * 0.9 * (0.6 + 0.8 * rnd);
                    dz = Math.cos(t * 0.8 + ph) * 0.6;
                    pulse = 0.55f + 0.45f * (float) Math.sin(t * 7.0 + ph * 5.0);
                }
                case SPARKLES -> {
                    dx = Math.sin(t * 0.20 + ph * 2.0) * 0.5;
                    dy = Math.sin(t * 0.30 + ph) * 0.5;
                    dz = Math.cos(t * 0.22 + ph * 1.4) * 0.5;
                    double q = 0.5 + 0.5 * Math.sin(t * 2.6 + ph * 5.0);
                    pulse = (float) (q * q * q);
                }
                default -> { // petals
                    dx = Math.sin(t * 0.5 + ph) * 0.9 + t * 0.35;
                    dy = -t * 0.6 * (0.6 + 0.8 * rnd);
                    dz = Math.cos(t * 0.45 + ph) * 0.9;
                }
            }
            double lx = mod(Hash.f(seed, 1) * L + dx - ox, L);
            double ly = mod(Hash.f(seed, 2) * Hv + dy - oy, Hv);
            double lz = mod(Hash.f(seed, 3) * L + dz - oz, L);
            // fade towards the edges of the box, where particles wrap around
            double e = Math.max(Math.max(Math.abs(lx - R) / R, Math.abs(lz - R) / R), Math.abs(ly - Hv * 0.5) / (Hv * 0.5));
            double edge = 1.0 - smooth((e - 0.72) / 0.28);
            if (edge <= 0.01 || pulse <= 0.02f) continue;
            double x = ox + lx, y = oy + ly, z = oz + lz;
            double r = sc * (0.7 + 0.6 * rnd);
            float a = master * pulse * (float) edge * Comfort.factor(x, y, z, clear, 1.6, aim, zone, r * 2.0);
            if (a <= 0.01f) continue;
            double phase = Hash.f(seed, 9);

            switch (st) {
                case SNOW -> diamond(x, y, z, r, pal.at(phase, a));
                case PETALS -> petal(x, y, z, r, t * (1.5 + rnd) + ph, pal.at(phase, a), pal.at(phase + 0.3, a * 0.8f));
                case SPARKLES -> {
                    if (glow.get()) softDot(x, y, z, r * 4.0, pal.at(phase, a * 0.30f));
                    diamond(x, y, z, r * 0.8, pal.at(phase + 0.2, a));
                    double ll = r * 3.2 * (0.6 + 0.4 * pulse), rot = t * 0.4 + ph;
                    for (int k = 0; k < 2; k++) {
                        double ang = rot + k * Math.PI / 2;
                        double ax = Gfx.rightX * Math.cos(ang) + Gfx.upX * Math.sin(ang);
                        double ay = Gfx.rightY * Math.cos(ang) + Gfx.upY * Math.sin(ang);
                        double az = Gfx.rightZ * Math.cos(ang) + Gfx.upZ * Math.sin(ang);
                        Gfx.line(x - ax * ll, y - ay * ll, z - az * ll, x + ax * ll, y + ay * ll, z + az * ll, r * 0.16, pal.at(phase, a * 0.8f));
                    }
                }
                default -> {
                    if (glow.get()) softDot(x, y, z, r * (st == DUST ? 3.0 : 4.5), pal.at(phase, a * 0.35f));
                    diamond(x, y, z, r, pal.at(phase + 0.2, a));
                }
            }
        }
    }

    // ---------------------------------------------------------------- tiny primitives

    private static double smooth(double x) {
        double t = Math.max(0, Math.min(1, x));
        return t * t * (3 - 2 * t);
    }

    private static void diamond(double x, double y, double z, double s, int c) {
        Gfx.quad(x + Gfx.rightX * s, y + Gfx.rightY * s, z + Gfx.rightZ * s, c,
                 x + Gfx.upX * s, y + Gfx.upY * s, z + Gfx.upZ * s, c,
                 x - Gfx.rightX * s, y - Gfx.rightY * s, z - Gfx.rightZ * s, c,
                 x - Gfx.upX * s, y - Gfx.upY * s, z - Gfx.upZ * s, c);
    }

    /** Soft round glow facing the camera (one ring, bright centre to transparent edge). */
    private static void softDot(double x, double y, double z, double radius, int argb) {
        Gfx.annulus(x, y, z, Gfx.rightX, Gfx.rightY, Gfx.rightZ, Gfx.upX, Gfx.upY, Gfx.upZ,
            0, radius, 0, TAU, 8, argb, ColorUtil.withAlpha(argb, 0));
    }

    /** A petal: a long thin diamond that turns around the view axis. */
    private static void petal(double x, double y, double z, double s, double ang, int c1, int c2) {
        double ca = Math.cos(ang), sa = Math.sin(ang);
        double ax = (Gfx.rightX * ca + Gfx.upX * sa) * s * 1.7, ay = (Gfx.rightY * ca + Gfx.upY * sa) * s * 1.7, az = (Gfx.rightZ * ca + Gfx.upZ * sa) * s * 1.7;
        double bx = (-Gfx.rightX * sa + Gfx.upX * ca) * s * 0.8, by = (-Gfx.rightY * sa + Gfx.upY * ca) * s * 0.8, bz = (-Gfx.rightZ * sa + Gfx.upZ * ca) * s * 0.8;
        Gfx.quad(x + ax, y + ay, z + az, c1, x + bx, y + by, z + bz, c2, x - ax, y - ay, z - az, c1, x - bx, y - by, z - bz, c2);
    }
}
