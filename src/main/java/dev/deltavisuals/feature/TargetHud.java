package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.gui.UiFont;
import dev.deltavisuals.hud.HudModule;
import dev.deltavisuals.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;

/**
 * Small plate with the head, the name and "HP: N" of the player under your crosshair.
 *
 * The target is whatever the game itself says the crosshair points at, so a player under the Invisibility
 * effect is shown like anybody else. HP is read from the entity every frame (the value the client knows),
 * rounded to a whole number. When you look away the plate fades out.
 */
public final class TargetHud extends HudModule {
    private static final int HEAD = 24, GAP = 6, MIN_TEXT = 44;

    private final Setting.Bool showHead;
    private final Setting.Num holdTime;

    private UUID uuid;
    private String name = "";
    private int hp;
    private long lastSeen, lastNanos;
    private float fade;
    private int w;

    public TargetHud() {
        super("Target HUD", "Plate with head, name and HP of the player under your crosshair", Category.HUD, false, 0.5, 0.62);
        showHead = add(new Setting.Bool("Show Head", true));
        holdTime = add(new Setting.Num("Hold Time", 0, 2, 0.1, 0.3, " s"));
    }

    @Override public void onDisable() { uuid = null; fade = 0f; }

    @Override protected float alphaMul() { return fade; }
    @Override protected boolean accentBar() { return false; }

    @Override
    protected boolean prepare(MinecraftClient mc, boolean editing) {
        long nowN = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (nowN - lastNanos) / 1_000_000_000f);
        lastNanos = nowN;
        long now = System.currentTimeMillis();

        // the game's own crosshair target: invisible players are included
        PlayerEntity t = mc.targetedEntity instanceof PlayerEntity p && p != mc.player && p.isAlive() ? p : null;
        if (t != null) {
            uuid = t.getUuid();
            name = t.getName().getString();
            hp = Math.round(t.getHealth());
            lastSeen = now;
        } else if (uuid != null && mc.world != null) {
            PlayerEntity held = mc.world.getPlayerByUuid(uuid);   // keep the number live while the plate fades
            if (held != null && held.isAlive()) hp = Math.round(held.getHealth());
        }
        boolean show = t != null || (uuid != null && now - lastSeen < holdTime.get() * 1000.0);

        if (editing && uuid == null && mc.player != null) {       // sample for the HUD editor
            uuid = mc.player.getUuid();
            name = mc.player.getName().getString();
            hp = Math.round(mc.player.getHealth());
            lastSeen = now;
            show = true;
        }

        float target = show ? 1f : 0f;
        fade += (target - fade) * Math.min(1f, dt * 12f);
        if (target == 1f && fade > 0.98f) fade = 1f;
        if (!show && fade < 0.02f) { fade = 0f; uuid = null; return false; }
        if (uuid == null) return false;

        int tw = Math.max(MIN_TEXT, Math.max(mc.textRenderer.getWidth(UiFont.text(name)),
            mc.textRenderer.getWidth(UiFont.text("HP: " + hp))));
        w = (showHead.get() ? HEAD + GAP : 0) + tw;
        return true;
    }

    @Override protected int contentW() { return w; }
    @Override protected int contentH() { return HEAD; }

    @Override
    protected void draw(DrawContext g, MinecraftClient mc, int x, int y) {
        float f = fade;
        int tx = x;
        if (showHead.get()) {
            tx = x + HEAD + GAP;
            PlayerListEntry entry = mc.getNetworkHandler() == null || uuid == null ? null : mc.getNetworkHandler().getPlayerListEntry(uuid);
            if (entry != null) {
                // the head grows in and shrinks out with the plate (the skin drawer has no opacity argument)
                float k = 1f - (1f - f) * (1f - f);
                float cx = x + HEAD / 2f, cy = y + HEAD / 2f;
                var m = g.getMatrices();
                m.pushMatrix();
                m.translate(cx, cy);
                m.scale(k, k);
                m.translate(-cx, -cy);
                PlayerSkinDrawer.draw(g, entry.getSkinTextures(), x, y, HEAD);
                m.popMatrix();
            } else {
                g.fill(x, y, x + HEAD, y + HEAD, ColorUtil.mulAlpha(0xFF3A3550, f));
            }
        }
        if (f < 0.04f) return;     // the text renderer treats a near-zero alpha as opaque
        int base = color.get();
        g.drawText(mc.textRenderer, UiFont.text(name), tx, y + 4, ColorUtil.mulAlpha(base, f), true);
        g.drawText(mc.textRenderer, UiFont.text("HP: " + hp), tx, y + 14, ColorUtil.mulAlpha(ColorUtil.withAlpha(base, 205), f), true);
    }
}
