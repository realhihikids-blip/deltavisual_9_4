package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.render.Gfx;
import dev.deltavisuals.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Hitbox outline with an optional translucent fill for players and mobs.
 *
 * The colour tells whether a hit would reach: white while the target is out of range, red inside it. The range is
 * measured the way the server does it, from your eyes to the CLOSEST POINT of the target's hitbox, and it is the
 * real interaction range of your player (so a changed range from the server or an item is respected), plus an
 * optional margin. Near the border the state has a small hysteresis and the colour is blended over a few frames,
 * so it never flickers.
 *
 * Pure render through {@link Gfx}; nothing is sent to the server and nothing is changed in the game.
 */
public final class HitboxFill extends Module {
    private static final String BY_DISTANCE = "White To Red (By Distance)", ONE_COLOR = "Always One Color";
    private static final double HYSTERESIS = 0.04, MAX_DIST = 64;

    private final Setting.Bool boxes, players, mobs, fill, realReach;
    private final Setting.Mode colorMode;
    private final Setting.Num fillOpacity, reach, margin, lineWidth;
    private final Setting.Color farColor, reachColor, singleColor;

    /** Per entity id: [0] colour blend 0 (far) .. 1 (in reach), [1] 1 if currently counted as in reach, [2] last frame seen. */
    private final Map<Integer, float[]> state = new HashMap<>();
    private long lastNanos;
    private int frame;

    public HitboxFill() {
        super("Hitbox Fill", "Hitbox outline and fill for players and mobs. Turns red when you can hit the target",
            Category.COMBAT_VISUALS, false);
        add(new Setting.Header("Hitboxes"));
        boxes = add(new Setting.Bool("Show Hitboxes", true));
        players = add(new Setting.Bool("Players", true));
        mobs = add(new Setting.Bool("Mobs", true));
        lineWidth = add(new Setting.Num("Outline Width", 0.004, 0.03, 0.002, 0.012));

        add(new Setting.Header("Fill"));
        fill = add(new Setting.Bool("Hitbox Fill", true));
        fillOpacity = add(new Setting.Num("Fill Opacity", 5, 80, 1, 25, "%"));
        fillOpacity.visibleIf(fill::get);

        add(new Setting.Header("Color"));
        colorMode = add(new Setting.Mode("Color Mode", new String[]{BY_DISTANCE, ONE_COLOR}, 0));
        farColor = add(new Setting.Color("Far Color", 0xFFFFFFFF, false));
        reachColor = add(new Setting.Color("In Reach Color", 0xFFFF3B3B, false));
        singleColor = add(new Setting.Color("Color", 0xFFFFFFFF, false));
        farColor.visibleIf(() -> colorMode.is(BY_DISTANCE));
        reachColor.visibleIf(() -> colorMode.is(BY_DISTANCE));
        singleColor.visibleIf(() -> colorMode.is(ONE_COLOR));

        add(new Setting.Header("Hit Distance"));
        realReach = add(new Setting.Bool("Use Real Reach", true));
        reach = add(new Setting.Num("Hit Distance", 2, 7, 0.05, 3.0, " m"));
        reach.visibleIf(() -> !realReach.get());
        margin = add(new Setting.Num("Reach Margin", -0.5, 1.0, 0.05, 0.0, " m"));
        colorMode.onChange = state::clear;
        add(new Setting.Info("Note", () -> "Distance = from your eyes to the nearest point of the hitbox, like the server checks a hit"));
    }

    @Override public void onDisable() { state.clear(); lastNanos = 0; }

    private double hitRange(MinecraftClient mc) {
        double base;
        if (realReach.get()) {
            try { base = mc.player.getEntityInteractionRange(); } catch (Throwable t) { base = 3.0; }
        } else {
            base = reach.get();
        }
        return base + margin.get();
    }

    private boolean wanted(Entity e, MinecraftClient mc) {
        if (e == mc.player || !(e instanceof LivingEntity) || e instanceof ArmorStandEntity) return false;
        if (e.isRemoved() || (e instanceof LivingEntity le && !le.isAlive())) return false;
        return e instanceof PlayerEntity ? players.get() : mobs.get();
    }

    @Override
    public void onWorldRender() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null || !boxes.get()) return;
        if (!players.get() && !mobs.get()) return;

        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        frame++;

        Vec3d eye = mc.player.getCameraPosVec(Gfx.partial);
        double range = hitRange(mc);
        boolean byDistance = colorMode.is(BY_DISTANCE);
        float fillA = fillOpacity.floatValue() / 100f;
        double hw = lineWidth.get();

        Gfx.use(false, false);
        for (Entity e : mc.world.getEntities()) {
            if (!wanted(e, mc)) continue;
            Vec3d lp = e.getLerpedPos(Gfx.partial);
            Box b = e.getBoundingBox().offset(lp.x - e.getX(), lp.y - e.getY(), lp.z - e.getZ());

            // distance from the eyes to the closest point of the box (0 when the eyes are inside it)
            double cx = Math.max(b.minX, Math.min(eye.x, b.maxX));
            double cy = Math.max(b.minY, Math.min(eye.y, b.maxY));
            double cz = Math.max(b.minZ, Math.min(eye.z, b.maxZ));
            double dx = eye.x - cx, dy = eye.y - cy, dz = eye.z - cz;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > MAX_DIST) continue;

            int color;
            if (byDistance) {
                float[] s = state.computeIfAbsent(e.getId(), k -> new float[]{0f, 0f, 0f});
                boolean was = s[1] > 0.5f;
                boolean in = was ? d < range + HYSTERESIS : d < range;   // a little slack on the way out: no flicker on the border
                s[1] = in ? 1f : 0f;
                s[2] = frame;
                s[0] += ((in ? 1f : 0f) - s[0]) * Math.min(1f, dt * 16f);
                color = ColorUtil.lerp(0xFF000000 | farColor.rgb(), 0xFF000000 | reachColor.rgb(), s[0]);
            } else {
                color = 0xFF000000 | singleColor.rgb();
            }

            if (!Gfx.hasBudget(16)) break;
            if (fill.get()) fillBox(b, ColorUtil.withAlpha(color, Math.round(255f * fillA)));
            edges(b, hw, ColorUtil.withAlpha(color, 230));
        }

        if (byDistance) {                                  // forget entities that were not drawn this frame
            Iterator<float[]> it = state.values().iterator();
            while (it.hasNext()) if (frame - (int) it.next()[2] > 120) it.remove();
        }
    }

    /** Only the faces that look at the camera, so the fill has one even layer and never covers the view from inside. */
    private static void fillBox(Box b, int c) {
        double x = Gfx.camX, y = Gfx.camY, z = Gfx.camZ;
        if (x > b.minX && x < b.maxX && y > b.minY && y < b.maxY && z > b.minZ && z < b.maxZ) return;
        if (x < b.minX) face(b.minX, b.minY, b.minZ, b.minX, b.maxY, b.minZ, b.minX, b.maxY, b.maxZ, b.minX, b.minY, b.maxZ, c);
        else if (x > b.maxX) face(b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, b.maxX, b.minY, b.maxZ, c);
        if (y < b.minY) face(b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, b.maxX, b.minY, b.maxZ, b.minX, b.minY, b.maxZ, c);
        else if (y > b.maxY) face(b.minX, b.maxY, b.minZ, b.maxX, b.maxY, b.minZ, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ, c);
        if (z < b.minZ) face(b.minX, b.minY, b.minZ, b.maxX, b.minY, b.minZ, b.maxX, b.maxY, b.minZ, b.minX, b.maxY, b.minZ, c);
        else if (z > b.maxZ) face(b.minX, b.minY, b.maxZ, b.maxX, b.minY, b.maxZ, b.maxX, b.maxY, b.maxZ, b.minX, b.maxY, b.maxZ, c);
    }

    private static void face(double ax, double ay, double az, double bx, double by, double bz,
                             double cx, double cy, double cz, double dx, double dy, double dz, int c) {
        Gfx.quad(ax, ay, az, c, bx, by, bz, c, cx, cy, cz, c, dx, dy, dz, c);
    }

    private static void edges(Box b, double hw, int c) {
        double x0 = b.minX, y0 = b.minY, z0 = b.minZ, x1 = b.maxX, y1 = b.maxY, z1 = b.maxZ;
        Gfx.line(x0, y0, z0, x1, y0, z0, hw, c); Gfx.line(x0, y1, z0, x1, y1, z0, hw, c);
        Gfx.line(x0, y0, z1, x1, y0, z1, hw, c); Gfx.line(x0, y1, z1, x1, y1, z1, hw, c);
        Gfx.line(x0, y0, z0, x0, y1, z0, hw, c); Gfx.line(x1, y0, z0, x1, y1, z0, hw, c);
        Gfx.line(x0, y0, z1, x0, y1, z1, hw, c); Gfx.line(x1, y0, z1, x1, y1, z1, hw, c);
        Gfx.line(x0, y0, z0, x0, y0, z1, hw, c); Gfx.line(x1, y0, z0, x1, y0, z1, hw, c);
        Gfx.line(x0, y1, z0, x0, y1, z1, hw, c); Gfx.line(x1, y1, z0, x1, y1, z1, hw, c);
    }
}
