package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.render.SkyPresets;
import dev.deltavisuals.render.SkyRenderer;
import dev.deltavisuals.util.Compat;
import net.minecraft.client.MinecraftClient;

/**
 * Replaces the look of the sky with a generated dome (gradients, animated noise clouds, nebula, stars, aurora,
 * black hole). The dome is drawn by {@link SkyRenderer} before every other effect and is depth tested, so the world
 * always stays in front of it. Purely visual; no textures, no particles.
 */
public final class CustomSky extends Module {
    private final Setting.Mode preset, quality;
    private final Setting.Num intensity, brightness, opacity, gradPower, hue, speed, glow;
    private final Setting.Num cloudAmount, cloudContrast, cloudScale, starDensity, sunAz, sunEl, holeSize, distance;
    private final Setting.Bool override, stars, sunGlow, hideClouds;
    private final Setting.Color top, horizon, bottom, glowCol, cloudLight, cloudDark;

    private final SkyRenderer.Params params = new SkyRenderer.Params();
    private double animTime;
    private long lastNanos;

    public CustomSky() {
        super("Custom Sky", "Generated sky dome: space, storm, sunsets, aurora, black hole and your own colours", Category.VISUALS, false);
        add(new Setting.Header("Sky Presets"));
        preset = add(new Setting.Mode("Preset", SkyPresets.NAMES, 1));

        add(new Setting.Header("Look"));
        intensity = add(new Setting.Num("Intensity", 0, 100, 1, 100, "%"));
        brightness = add(new Setting.Num("Brightness", 0.3, 1.8, 0.05, 1.0, "x"));
        opacity = add(new Setting.Num("Opacity", 20, 100, 1, 100, "%"));
        gradPower = add(new Setting.Num("Gradient Power", 0.4, 3.0, 0.05, 1.0));
        hue = add(new Setting.Num("Hue Shift", -180, 180, 1, 0, " deg"));
        glow = add(new Setting.Num("Glow", 0, 2, 0.05, 1.0, "x"));
        speed = add(new Setting.Num("Animation Speed", 0, 3, 0.05, 1.0, "x"));

        add(new Setting.Header("Sky Colors"));
        override = add(new Setting.Bool("Use My Colors On Preset", false));
        top = add(new Setting.Color("Top Color", 0xFF2646A8, false));
        horizon = add(new Setting.Color("Horizon Color", 0xFFFFC9A0, false));
        bottom = add(new Setting.Color("Bottom Color", 0xFF8FA8C8, false));
        glowCol = add(new Setting.Color("Glow Color", 0xFFFFD68A, false));
        add(new Setting.Info("Note", () -> "Custom / Gradient / Color Sky always use these"));
        add(new Setting.Action("Load Preset Colors", this::loadPresetColors));

        add(new Setting.Header("Clouds"));
        cloudAmount = add(new Setting.Num("Cloud Amount", 0, 150, 1, 100, "%"));
        cloudContrast = add(new Setting.Num("Cloud Contrast", 0.3, 2.0, 0.05, 1.0, "x"));
        cloudScale = add(new Setting.Num("Cloud Scale", 0.5, 2.5, 0.05, 1.0, "x"));
        cloudLight = add(new Setting.Color("Cloud Light Color", 0xFFFFFFFF, false));
        cloudDark = add(new Setting.Color("Cloud Shadow Color", 0xFF7A86B8, false));
        hideClouds = add(new Setting.Bool("Hide Vanilla Clouds", true));

        add(new Setting.Header("Stars and Sun"));
        stars = add(new Setting.Bool("Stars", true));
        starDensity = add(new Setting.Num("Star Density", 0, 200, 5, 100, "%"));
        sunGlow = add(new Setting.Bool("Sun / Moon Glow", true));
        sunAz = add(new Setting.Num("Sun Direction", 0, 360, 1, 40, " deg"));
        sunEl = add(new Setting.Num("Sun Height", -5, 60, 1, 8, " deg"));
        holeSize = add(new Setting.Num("Black Hole Size", 0.5, 2.0, 0.05, 1.0, "x"));
        holeSize.visibleIf(() -> preset.is("Black Hole"));

        add(new Setting.Header("Quality and Distance"));
        quality = add(new Setting.Mode("Quality", new String[]{"Low", "Medium", "High"}, 1));
        distance = add(new Setting.Num("Dome Distance (0 = auto)", 0, 1000, 10, 0));
        add(new Setting.Info("Tip", () -> "Presets: Default Sky = vanilla sky"));
    }

    public Setting.Mode preset() { return preset; }

    /** Used by the "Sky Preset Switch" key module: turns the sky on and moves to the next / previous preset. */
    public void cyclePreset(int direction) {
        if (!isEnabled()) setEnabled(true);
        if (direction >= 0) preset.next(); else preset.prev();
    }

    private void loadPresetColors() {
        SkyRenderer.Look l = SkyPresets.get(preset.index());
        if (l == null) return;
        top.set(0xFF000000 | l.top); horizon.set(0xFF000000 | l.horizon); bottom.set(0xFF000000 | l.bottom);
        glowCol.set(0xFF000000 | l.glow); cloudLight.set(0xFF000000 | l.cloudLight); cloudDark.set(0xFF000000 | l.cloudDark);
        override.set(true);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        Compat.hideClouds(mc, hideClouds.get() && preset.index() != 0);
    }

    @Override
    public void onDisable() {
        Compat.hideClouds(MinecraftClient.getInstance(), false);
        lastNanos = 0;
    }

    @Override
    public void onWorldRender() {
        SkyRenderer.Look look = SkyPresets.get(preset.index());
        if (look == null) { lastNanos = 0; return; }
        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1_000_000_000.0);
        lastNanos = now;
        animTime += dt * speed.get();

        SkyRenderer.Params p = params;
        p.look = look;
        boolean mine = override.get() || look.custom;
        p.top = mine ? top.get() : 0; p.horizon = mine ? horizon.get() : 0; p.bottom = mine ? bottom.get() : 0;
        p.glowColor = mine ? glowCol.get() : 0; p.cloudLight = mine ? cloudLight.get() : 0; p.cloudDark = mine ? cloudDark.get() : 0;
        p.intensity = intensity.floatValue() / 100f;
        p.brightness = brightness.floatValue();
        p.opacity = opacity.floatValue() / 100f;
        p.gradPower = gradPower.floatValue();
        p.hueShift = hue.floatValue();
        p.glow = glow.floatValue();
        p.cloudAmount = cloudAmount.floatValue() / 100f;
        p.cloudContrast = cloudContrast.floatValue();
        p.cloudScale = cloudScale.floatValue();
        p.starDensity = starDensity.floatValue() / 100f;
        p.stars = stars.get();
        p.sun = sunGlow.get();
        p.sunAz = Math.toRadians(sunAz.get());
        p.sunEl = Math.toRadians(sunEl.get());
        p.holeSize = holeSize.floatValue();
        p.quality = quality.index();
        p.time = animTime;
        double auto = Compat.viewDistanceChunks(mc) * 16.0 * 1.35 + 24.0;
        p.radius = distance.get() > 0 ? distance.get() : Math.max(160.0, Math.min(900.0, auto));
        SkyRenderer.draw(p);
    }
}
