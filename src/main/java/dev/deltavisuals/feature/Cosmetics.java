package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.render.CosmeticModels;
import dev.deltavisuals.render.Gfx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.math.Vec3d;

/**
 * Cape, wings and hat on your own character, drawn as solid geometry (no textures, no particles, nothing from
 * outside the mod). Visible only to you: other players do not receive anything. Hidden in first person unless
 * you ask for it, because your own model is not drawn there.
 */
public final class Cosmetics extends Module {
    private final Setting.Bool cape, capeCustom, wings, wingCustom, hat, hatCustom, firstPerson;
    private final Setting.Mode capeStyle, wingStyle, hatStyle;
    private final Setting.Num capeLength, wingSize, flapSpeed, hatSize, opacity;
    private final Setting.Color capeColor, capeTrim, wingColor, wingAccent, hatColor, hatAccent;

    private final CosmeticModels.Rig rig = new CosmeticModels.Rig();

    // tracked every tick, interpolated every frame
    private boolean init;
    private double lastX, lastY, lastZ;
    private double prevBody, curBody, prevHead, curHead, prevPitch, curPitch;
    private double prevFwd, curFwd, prevSide, curSide, prevUp, curUp;
    private double time;
    private long lastNanos;

    public Cosmetics() {
        super("Cosmetics", "Cape, wings and hat on your character. Solid models, visible only to you", Category.COSMETICS, false);
        add(new Setting.Header("Cape"));
        cape = add(new Setting.Bool("Cape", true));
        capeStyle = add(new Setting.Mode("Cape Style", CosmeticModels.CAPE_STYLES, 0));
        capeLength = add(new Setting.Num("Cape Length", 0.5, 1.4, 0.05, 1.0, "x"));
        capeCustom = add(new Setting.Bool("Custom Cape Colors", false));
        capeColor = add(new Setting.Color("Cape Color", 0xFF2F6FEB, false));
        capeTrim = add(new Setting.Color("Cape Trim Color", 0xFFE8EEFF, false));

        add(new Setting.Header("Wings"));
        wings = add(new Setting.Bool("Wings", false));
        wingStyle = add(new Setting.Mode("Wing Style", CosmeticModels.WING_STYLES, 0));
        wingSize = add(new Setting.Num("Wing Size", 0.6, 1.6, 0.05, 1.0, "x"));
        flapSpeed = add(new Setting.Num("Flap Speed", 0.5, 2.5, 0.05, 1.0, "x"));
        wingCustom = add(new Setting.Bool("Custom Wing Colors", false));
        wingColor = add(new Setting.Color("Wing Color", 0xFFFFFFFF, false));
        wingAccent = add(new Setting.Color("Wing Accent Color", 0xFFFFE3A0, false));

        add(new Setting.Header("Hat"));
        hat = add(new Setting.Bool("Hat", false));
        hatStyle = add(new Setting.Mode("Hat Style", CosmeticModels.HAT_STYLES, 0));
        hatSize = add(new Setting.Num("Hat Size", 0.6, 1.5, 0.05, 1.0, "x"));
        hatCustom = add(new Setting.Bool("Custom Hat Colors", false));
        hatColor = add(new Setting.Color("Hat Color", 0xFF1C1C24, false));
        hatAccent = add(new Setting.Color("Hat Accent Color", 0xFFB01E3C, false));

        add(new Setting.Header("General"));
        opacity = add(new Setting.Num("Opacity", 30, 100, 1, 100, "%"));
        firstPerson = add(new Setting.Bool("Show In First Person", false));
        add(new Setting.Info("Note", () -> "Visible only to you (client side)"));
    }

    private static double wrap(double a) {
        a %= 360.0;
        if (a >= 180.0) a -= 360.0;
        if (a < -180.0) a += 360.0;
        return a;
    }

    private static double lerpAngle(double a, double b, double t) { return a + wrap(b - a) * t; }

    @Override
    public void onDisable() { init = false; lastNanos = 0; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) { init = false; return; }
        if (mc.isPaused()) return;
        double head = p.getYaw(), pitch = p.getPitch();
        if (!init) {
            lastX = p.getX(); lastY = p.getY(); lastZ = p.getZ();
            prevBody = curBody = head; prevHead = curHead = head; prevPitch = curPitch = pitch;
            prevFwd = curFwd = prevSide = curSide = prevUp = curUp = 0;
            init = true;
            return;
        }
        double vx = p.getX() - lastX, vy = p.getY() - lastY, vz = p.getZ() - lastZ;
        lastX = p.getX(); lastY = p.getY(); lastZ = p.getZ();
        if (Math.hypot(vx, vz) > 4.0) { vx = 0; vy = 0; vz = 0; prevBody = curBody = head; }   // teleport

        prevBody = curBody; prevHead = curHead; prevPitch = curPitch;
        prevFwd = curFwd; prevSide = curSide; prevUp = curUp;

        // the body turns like the vanilla one: towards the movement direction, never more than 75 degrees from the head
        double body = curBody, target = body;
        if (vx * vx + vz * vz > 0.0025) {
            double h = Math.toDegrees(Math.atan2(vz, vx)) - 90.0;
            double k = Math.abs(wrap(head) - h);
            target = (k > 95.0 && k < 265.0) ? h - 180.0 : h;
        }
        body += wrap(target - body) * 0.3;
        double diff = wrap(head - body);
        diff = Math.max(-75.0, Math.min(75.0, diff));
        body = head - diff;
        if (diff * diff > 2500.0) body += diff * 0.2;
        curBody = body; curHead = head; curPitch = pitch;

        double by = Math.toRadians(curBody);
        double ffx = -Math.sin(by), ffz = Math.cos(by), rrx = -Math.cos(by), rrz = -Math.sin(by);
        curFwd += (vx * ffx + vz * ffz - curFwd) * 0.35;
        curSide += (vx * rrx + vz * rrz - curSide) * 0.35;
        curUp += (vy - curUp) * 0.35;
    }

    @Override
    public void onWorldRender() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity p = mc.player;
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : Math.min(0.1, (now - lastNanos) / 1_000_000_000.0);
        lastNanos = now;
        time += dt;
        if (p == null || mc.world == null || !init) return;
        if (p.isInvisible() || p.isSpectator()) return;
        if (mc.options.getPerspective().isFirstPerson() && !firstPerson.get()) return;
        EntityPose pose = p.getPose();
        if (pose != EntityPose.STANDING && pose != EntityPose.CROUCHING) return;

        double t = Gfx.partial;
        Vec3d pos = p.getLerpedPos(Gfx.partial);
        CosmeticModels.Rig r = rig;
        r.x = pos.x; r.y = pos.y; r.z = pos.z;
        r.bodyYaw = lerpAngle(prevBody, curBody, t);
        r.headYaw = lerpAngle(prevHead, curHead, t);
        r.headPitch = prevPitch + (curPitch - prevPitch) * t;
        r.eyeH = p.getEyeY() - p.getY();
        r.crouch = pose == EntityPose.CROUCHING;
        r.airborne = !p.isOnGround();
        r.fwd = prevFwd + (curFwd - prevFwd) * t;
        r.side = prevSide + (curSide - prevSide) * t;
        r.up = prevUp + (curUp - prevUp) * t;
        r.time = time;
        r.alpha = (int) Math.round(opacity.get() * 2.55);

        if (cape.get()) {
            int[] def = CosmeticModels.CAPE_COLORS[capeStyle.index()];
            CosmeticModels.cape(r, capeStyle.index(), capeCustom.get() ? capeColor.get() : def[0], capeCustom.get() ? capeTrim.get() : def[1], capeLength.get());
        }
        if (wings.get()) {
            int[] def = CosmeticModels.WING_COLORS[wingStyle.index()];
            CosmeticModels.wings(r, wingStyle.index(), wingCustom.get() ? wingColor.get() : def[0], wingCustom.get() ? wingAccent.get() : def[1], wingSize.get(), flapSpeed.get());
        }
        if (hat.get()) {
            int[] def = CosmeticModels.HAT_COLORS[hatStyle.index()];
            CosmeticModels.hat(r, hatStyle.index(), hatCustom.get() ? hatColor.get() : def[0], hatCustom.get() ? hatAccent.get() : def[1], hatSize.get());
        }
    }
}
