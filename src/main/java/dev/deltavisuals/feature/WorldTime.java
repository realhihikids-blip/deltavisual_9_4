package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Modules;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.render.SkyRenderer;
import dev.deltavisuals.util.ColorUtil;
import dev.deltavisuals.util.Compat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;

import java.time.LocalTime;

/**
 * World Time: shows a time of day of your choice (day, sunset, night...), only on your screen.
 *
 * Nothing is sent to the server and the real world time is not touched. What it changes:
 *  - the sky: a generated dome with a gradient that follows the clock, a sun and a moon that move along the sky,
 *    clouds and stars (drawn through {@link SkyRenderer}, like Custom Sky);
 *  - the light: while the clock shows daytime the world is brightened with the local Night Vision effect
 *    (see {@link NightVision#setExternal}), so a dark night looks like day.
 *
 * Limit: the terrain keeps the real lighting of the server. A real day cannot be darkened into night this way,
 * only the sky shows the night.
 */
public final class WorldTime extends Module {
    private static final String[] CLOCK = {"Fixed", "Cycle", "Real Clock"};
    private static final String[] TIMES = {"Sunrise", "Morning", "Noon", "Afternoon", "Sunset", "Night", "Midnight", "Custom"};
    private static final int[] TIME_TICKS = {23500, 1500, 6000, 10000, 12000, 14500, 18000, 6000};
    private static final double TAU = Math.PI * 2;
    /** The sun path is tilted towards the south so it never runs through the exact zenith (no sky pole artefacts). */
    private static final double TILT = Math.toRadians(25);

    // keyframes: tick, top, horizon, bottom, glow (RGB)
    private static final int[] KT = {0, 1500, 6000, 10500, 12000, 13200, 14500, 18000, 21500, 24000};
    private static final int[] KTOP = {0x3E6BB5, 0x2F6FCB, 0x1D5FC4, 0x2A66C0, 0x2A3A82, 0x0D1340, 0x050A24, 0x02040D, 0x070C2B, 0x3E6BB5};
    private static final int[] KHOR = {0xFFA46B, 0xBFD9F0, 0xA7D3F5, 0xCFE0EE, 0xFF8A45, 0xB6507A, 0x1E3066, 0x14234F, 0x4A4F8E, 0xFFA46B};
    private static final int[] KBOT = {0xA8B4C8, 0x9CC0E4, 0x8EC2EC, 0x9DBBDF, 0xB88E80, 0x20203C, 0x080C1C, 0x050813, 0x0C1022, 0xA8B4C8};
    private static final int[] KGLO = {0xFFB070, 0xFFE3A8, 0xFFF2C4, 0xFFE0A0, 0xFFA050, 0xFF7090, 0xA9C3FF, 0xA9C3FF, 0xC0B0FF, 0xFFB070};

    private static final SkyRenderer.Look DAY = new SkyRenderer.Look("Time Day");
    private static final SkyRenderer.Look NIGHT = new SkyRenderer.Look("Time Night");
    static {
        DAY.top = 0x1D5FC4; DAY.mid = 0x4F9BE6; DAY.horizon = 0xA7D3F5; DAY.bottom = 0x8EC2EC;
        DAY.glow = 0xFFF2C4; DAY.cloudLight = 0xFFFFFF; DAY.cloudDark = 0x7FAFE6;
        DAY.cover = 0.45f; DAY.cloudAlpha = 0.8f; DAY.streakAz = 1.4f; DAY.streakEl = 7.5f; DAY.swirl = 0.3f; DAY.thickDark = 0f;
        DAY.sunStrength = 1.0f; DAY.sunWide = 0.42f; DAY.sunTall = 0.11f; DAY.sunCore = true;

        NIGHT.top = 0x02040D; NIGHT.mid = 0x09112D; NIGHT.horizon = 0x1B2B58; NIGHT.bottom = 0x070A16;
        NIGHT.glow = 0xA9C3FF; NIGHT.cloudLight = 0x3C4C80; NIGHT.cloudDark = 0x080C1C;
        NIGHT.cover = 0.3f; NIGHT.cloudAlpha = 0.5f; NIGHT.streakAz = 2.4f; NIGHT.streakEl = 5f; NIGHT.thickDark = 0.3f;
        NIGHT.stars = 1f; NIGHT.moon = true; NIGHT.sunStrength = 0.5f; NIGHT.sunWide = 0.5f; NIGHT.sunTall = 0.5f;
    }

    private final Setting.Mode clock, time;
    private final Setting.Num customTime, cycleSpeed, cloudAmount, brightness;
    private final Setting.Bool drawSky, hideClouds, stars, sunMoon, brighten;

    private final SkyRenderer.Params params = new SkyRenderer.Params();
    /** Shown time in ticks (0 = sunrise, 6000 = noon, 12000 = sunset, 18000 = midnight). */
    private double cyc = 6000, animTime;
    private long lastNanos;
    private double sunY = 1;          // sine of the sun elevation, for the state of the world light

    public WorldTime() {
        super("World Time", "Pick the time of day on your screen: day, sunset, night or a running clock (only visual)",
            Category.VISUALS, false);
        tickWhenDisabled = true;      // so the cloud option and the world light are handed back after switching off
        add(new Setting.Header("Time"));
        clock = add(new Setting.Mode("Clock", CLOCK, 0));
        time = add(new Setting.Mode("Time", TIMES, 2));
        time.visibleIf(() -> !clock.is("Real Clock"));
        customTime = add(new Setting.Num("Custom Time", 0, 24000, 100, 6000, " ticks"));
        customTime.visibleIf(() -> time.is("Custom") && !clock.is("Real Clock"));
        cycleSpeed = add(new Setting.Num("Cycle Speed", 1, 200, 1, 20, "x"));
        cycleSpeed.visibleIf(() -> clock.is("Cycle"));
        add(new Setting.Info("Shown", this::shownText));
        time.onChange = this::restartCycle;
        customTime.onChange = this::restartCycle;
        clock.onChange = this::restartCycle;

        add(new Setting.Header("Sky"));
        drawSky = add(new Setting.Bool("Draw Time Sky", true));
        hideClouds = add(new Setting.Bool("Hide Vanilla Clouds", true));
        cloudAmount = add(new Setting.Num("Cloud Amount", 0, 150, 1, 80, "%"));
        brightness = add(new Setting.Num("Sky Brightness", 0.6, 1.4, 0.05, 1.0, "x"));
        stars = add(new Setting.Bool("Stars", true));
        sunMoon = add(new Setting.Bool("Sun And Moon", true));

        add(new Setting.Header("World Light"));
        brighten = add(new Setting.Bool("Brighten World By Day", true));
        add(new Setting.Info("Note", () -> "A Custom Sky preset has priority over the time sky. Real light of the world stays the server's"));
    }

    // ---------------------------------------------------------------- the clock

    private double fixedTicks() {
        return time.is("Custom") ? customTime.get() : TIME_TICKS[time.index()];
    }

    private void restartCycle() { cyc = fixedTicks(); lastNanos = 0; }

    private static double realClockTicks() {
        double hours = LocalTime.now().toSecondOfDay() / 3600.0;
        double t = ((hours - 6.0) % 24.0 + 24.0) % 24.0;        // 6:00 = tick 0, like the game
        return t / 24.0 * 24000.0;
    }

    /** Advances a running clock by the real time passed since the last call (safe to call from tick and render). */
    private double ticksNow() {
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1_000_000_000.0);
        lastNanos = now;
        animTime += dt;
        if (clock.is("Real Clock")) return realClockTicks();
        if (clock.is("Cycle")) {
            cyc = (cyc + dt * 20.0 * cycleSpeed.get()) % 24000.0;
            return cyc;
        }
        return fixedTicks();
    }

    private String shownText() {
        double t = clock.is("Real Clock") ? realClockTicks() : clock.is("Cycle") ? cyc : fixedTicks();
        int h = (int) (Math.floor(t / 1000.0) + 6) % 24;
        int m = (int) ((t % 1000.0) * 0.06);
        return String.format("%02d:%02d  (%d)", h, m, Math.round(t));
    }

    private static boolean overworld(MinecraftClient mc) {
        return mc.world != null && World.OVERWORLD.equals(mc.world.getRegistryKey());
    }

    private boolean customSkyActive() {
        return Modules.SKY != null && Modules.SKY.isEnabled() && Modules.SKY.preset().index() != 0;
    }

    private boolean skyWanted(MinecraftClient mc) {
        return isEnabled() && drawSky.get() && !customSkyActive() && overworld(mc);
    }

    // ---------------------------------------------------------------- tick: clouds + world light

    @Override
    public void onTick(MinecraftClient mc) {
        boolean on = isEnabled() && mc.world != null && mc.player != null;
        if (on) {
            double t = ticksNow();
            sunY = Math.sin(TAU * t / 24000.0) * Math.cos(TILT);
        }
        try {
            Compat.hideClouds(mc, this, on && skyWanted(mc) && hideClouds.get());
        } catch (Throwable ignored) { }
        if (Modules.NIGHT_VISION != null) {
            Modules.NIGHT_VISION.setExternal(on && brighten.get() && overworld(mc) && sunY > -0.12);
        }
    }

    @Override
    public void onEnable() { restartCycle(); }

    @Override
    public void onDisable() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) Compat.hideClouds(mc, this, false);
        if (Modules.NIGHT_VISION != null) Modules.NIGHT_VISION.setExternal(false);
        lastNanos = 0;
    }

    // ---------------------------------------------------------------- the sky

    private static int sampleKey(int[] key, double t) {
        int i = 0;
        while (i < KT.length - 2 && t >= KT[i + 1]) i++;
        double f = (t - KT[i]) / (double) (KT[i + 1] - KT[i]);
        f = Math.max(0, Math.min(1, f));
        f = f * f * (3 - 2 * f);
        return ColorUtil.lerp(0xFF000000 | key[i], 0xFF000000 | key[i + 1], (float) f);
    }

    private static float smooth(double x) {
        double t = Math.max(0, Math.min(1, x));
        return (float) (t * t * (3 - 2 * t));
    }

    @Override
    public void onWorldRender() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!skyWanted(mc)) return;
        double t = ticksNow();
        double phi = TAU * t / 24000.0;
        // unit vector to the sun: east at tick 0, high in the south at noon, west at sunset
        double sx = Math.cos(phi), sy = Math.sin(phi) * Math.cos(TILT), sz = Math.sin(phi) * Math.sin(TILT);
        double sunEl = Math.asin(Math.max(-1, Math.min(1, sy)));
        sunY = sy;

        // colour keys use the quantised clock, so the dome is rebuilt at most ~20 times per second in Cycle mode
        double tq = Math.floor(t / 20.0) * 20.0;
        int top = sampleKey(KTOP, tq), hor = sampleKey(KHOR, tq), bot = sampleKey(KBOT, tq), glo = sampleKey(KGLO, tq);
        float dayAmt = smooth((sy + 0.10) / 0.45);
        float warm = (float) Math.max(0.0, 1.0 - Math.abs(sy) / 0.35);
        int cl = ColorUtil.lerp(0xFF3C4C80, 0xFFFFFFFF, dayAmt);
        cl = ColorUtil.lerp(cl, 0xFF000000 | glo, warm * 0.40f);
        int cd = ColorUtil.lerp(0xFF080C1C, 0xFF7FAFE6, dayAmt);
        cd = ColorUtil.lerp(cd, 0xFF000000 | hor, warm * 0.35f);

        boolean sunUp = sunEl > -0.04;
        SkyRenderer.Look look = sunUp ? DAY : NIGHT;
        NIGHT.stars = stars.get() ? smooth((-sy + 0.02) / 0.30) : 0f;

        SkyRenderer.Params p = params;
        p.look = look;
        p.top = top; p.horizon = hor; p.bottom = bot;
        p.glowColor = 0xFF000000 | (glo & 0xFFFFFF);
        p.cloudLight = cl; p.cloudDark = cd;
        p.intensity = 1f;
        p.brightness = brightness.floatValue();
        p.opacity = 1f;
        p.gradPower = 1f;
        p.hueShift = 0f;
        p.glow = 1f;
        p.cloudAmount = cloudAmount.floatValue() / 100f;
        p.cloudContrast = 1f;
        p.cloudScale = 1f;
        p.starDensity = 1f;
        p.stars = stars.get();
        p.sun = sunMoon.get();
        p.sunAz = Math.atan2(sx, sz);
        p.sunEl = sunEl;
        p.moonOverride = true;
        p.moonAz = Math.atan2(-sx, -sz);
        p.moonEl = -sunEl;
        p.holeSize = 1f;
        p.quality = 1;
        p.time = animTime;
        double auto = Compat.viewDistanceChunks(mc) * 16.0 * 1.35 + 24.0;
        p.radius = Math.max(160.0, Math.min(900.0, auto));
        SkyRenderer.draw(p);
    }
}
