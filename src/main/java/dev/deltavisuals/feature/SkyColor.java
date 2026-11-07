package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.render.Gfx;
import dev.deltavisuals.util.ColorUtil;
import dev.deltavisuals.util.Compat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.World;

/**
 * Tints the vanilla sky: a see-through coloured dome is drawn over it, so the vanilla sun, moon, stars and clouds
 * stay and only the colour changes (red sky, purple sky, rainbow sky...). Depth tested, so terrain and entities
 * always stay in front of it. Purely visual, no textures, no particles.
 *
 * Known limit: terrain fog stays vanilla, so far terrain may keep a thin band of the old fog colour at the horizon.
 */
public final class SkyColor extends Module {
    public static final String[] PRESETS = {"Custom", "Red", "Orange", "Yellow", "Green", "Cyan", "Blue", "Purple", "Pink"};
    private static final int[] PRESET_RGB = {
        0, 0xFF2A2A, 0xFF8A2E, 0xFFE03C, 0x3CE05A, 0x2EE6E6, 0x2E6BFF, 0x9B3CFF, 0xFF5CB8
    };
    // elevation rows of the dome (deg), from below the horizon up to the zenith
    private static final double[] ROW_DEG = {-40, -12, -3, 0, 4, 10, 20, 34, 50, 70, 90};
    private static final int NR = ROW_DEG.length, NC = 48;
    private static final double TAU = Math.PI * 2;

    private final Setting.Mode colorMode, preset;
    private final Setting.Color main, second;
    private final Setting.Num strength, speed;
    private final Setting.Bool overworldOnly;

    private final float[] dx = new float[NR * NC], dy = new float[NR * NC], dz = new float[NR * NC];
    private final int[] rowColor = new int[NR];
    private double time;
    private long lastNanos;

    public SkyColor() {
        super("Sky Color", "Tints the vanilla sky any colour (red, purple, rainbow...). Sun, moon, stars and clouds stay",
            Category.VISUALS, false);
        add(new Setting.Header("Color"));
        colorMode = add(new Setting.Mode("Color Mode", new String[]{"Static", "Gradient", "Rainbow"}, 0));
        preset = add(new Setting.Mode("Color Preset", PRESETS, 1));
        main = add(new Setting.Color("Main Color", 0xFFFF2A2A, false));
        second = add(new Setting.Color("Horizon Color", 0xFFFF9A3C, false));
        main.visibleIf(() -> preset.is("Custom") && !colorMode.is("Rainbow"));
        second.visibleIf(() -> colorMode.is("Gradient"));
        preset.visibleIf(() -> !colorMode.is("Rainbow"));
        speed = add(new Setting.Num("Rainbow Speed", 0.1, 5, 0.1, 1.0, "x"));
        speed.visibleIf(() -> colorMode.is("Rainbow"));
        add(new Setting.Header("Look"));
        strength = add(new Setting.Num("Strength", 5, 100, 1, 55, "%"));
        overworldOnly = add(new Setting.Bool("Only In Overworld", true));
        add(new Setting.Info("Tip", () -> "Low strength keeps the sun and stars visible; 100% paints the sky over them"));

        for (int i = 0; i < NR; i++) {
            double el = Math.toRadians(ROW_DEG[i]), ce = Math.cos(el), se = Math.sin(el);
            for (int j = 0; j < NC; j++) {
                double az = j / (double) NC * TAU;
                int k = i * NC + j;
                dx[k] = (float) (Math.sin(az) * ce);
                dy[k] = (float) se;
                dz[k] = (float) (Math.cos(az) * ce);
            }
        }
    }

    @Override public void onDisable() { lastNanos = 0; }

    @Override
    public void onWorldRender() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        if (overworldOnly.get() && !World.OVERWORLD.equals(mc.world.getRegistryKey())) return;

        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1_000_000_000.0);
        lastNanos = now;
        time += dt;

        int top, hor;
        if (colorMode.is("Rainbow")) {
            top = ColorUtil.hsv((float) (time * 0.03 * speed.get()), 0.85f, 1f, 255);
            hor = ColorUtil.hsv((float) (time * 0.03 * speed.get() + 0.12), 0.6f, 1f, 255);
        } else {
            int rgb = preset.is("Custom") ? main.rgb() : PRESET_RGB[preset.index()];
            top = 0xFF000000 | rgb;
            hor = colorMode.is("Gradient") ? (0xFF000000 | second.rgb()) : ColorUtil.lerp(top, 0xFFFFFFFF, 0.20f);
        }
        int alpha = Math.round(255f * strength.floatValue() / 100f);
        for (int i = 0; i < NR; i++) {
            double e = ROW_DEG[i];
            int c;
            if (e <= 0) {
                c = hor;
            } else {
                float u = (float) Math.pow(Math.min(1.0, e / 90.0), 0.7);
                u = u * u * (3f - 2f * u);
                c = ColorUtil.lerp(hor, top, u);
            }
            rowColor[i] = ColorUtil.withAlpha(c, alpha);
        }

        double auto = Compat.viewDistanceChunks(mc) * 16.0 * 1.35 + 24.0;
        double R = Math.max(160.0, Math.min(900.0, auto)) * 0.99;   // just inside the Custom Sky dome
        double cx = Gfx.camX, cy = Gfx.camY, cz = Gfx.camZ;
        if (!Gfx.hasBudget(NR * NC + 8)) return;
        Gfx.use(false, false);
        for (int i = 0; i < NR - 1; i++) {
            int c0 = rowColor[i], c1 = rowColor[i + 1];
            for (int j = 0; j < NC; j++) {
                int j2 = (j + 1) % NC;
                int a = i * NC + j, b = i * NC + j2, c = (i + 1) * NC + j2, d = (i + 1) * NC + j;
                Gfx.quad(cx + dx[a] * R, cy + dy[a] * R, cz + dz[a] * R, c0,
                         cx + dx[b] * R, cy + dy[b] * R, cz + dz[b] * R, c0,
                         cx + dx[c] * R, cy + dy[c] * R, cz + dz[c] * R, c1,
                         cx + dx[d] * R, cy + dy[d] * R, cz + dz[d] * R, c1);
            }
        }
    }
}
