package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/**
 * Night Vision: the world is fully lit, like with the potion, but only on your screen.
 *
 * It adds an endless Night Vision effect to the local player entity (client side only, the server is never told
 * and nothing is sent). An endless effect has no flicker near the end and no timer. The effect is re-applied if
 * the server removes it, and it is taken away again when the module is switched off or the world is left.
 * A real Night Vision effect from the server that still has plenty of time left is left untouched.
 */
public final class NightVision extends Module {
    /** Vanilla starts to flicker at 200 ticks left; a real effect with more than this is already steady. */
    private static final int STEADY_TICKS = 220;

    private final Setting.Bool hideIcon;
    /** True while the effect on the player is the one this module added. */
    private boolean applied;
    /** Another module (World Time) wants the world lit even though this module is switched off. */
    private boolean external;

    public NightVision() {
        super("Night Vision", "See in the dark: the world is fully lit (only on your screen, nothing is sent to the server)",
            Category.VISUALS, false);
        hideIcon = add(new Setting.Bool("Hide Effect Icon", true));
        tickWhenDisabled = true;   // so the effect is also handed back when only World Time was using it
    }

    /** World Time asks for a lit world while it shows a daytime clock. Has no effect on its own setting. */
    public void setExternal(boolean v) { external = v; }

    @Override
    public void onTick(MinecraftClient mc) {
        ClientPlayerEntity p = mc.player;
        if (p == null) { applied = false; return; }
        if (!isEnabled() && !external) { release(p); return; }

        StatusEffectInstance cur = p.getStatusEffect(StatusEffects.NIGHT_VISION);
        boolean ours = applied && cur != null && cur.isInfinite();
        if (ours) {
            // keep the icon setting live
            if (cur.shouldShowIcon() == hideIcon.get()) {
                p.removeStatusEffect(StatusEffects.NIGHT_VISION);
                applied = false;
                apply(p);
            }
            return;
        }
        applied = false;
        if (cur != null && (cur.isInfinite() || cur.getDuration() > STEADY_TICKS)) return; // a real, steady effect
        if (cur != null) p.removeStatusEffect(StatusEffects.NIGHT_VISION);                // a real one about to flicker out
        apply(p);
    }

    private void apply(ClientPlayerEntity p) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, StatusEffectInstance.INFINITE,
            0, true, false, !hideIcon.get()));
        applied = true;
    }

    private void release(ClientPlayerEntity p) {
        if (p != null && applied) {
            StatusEffectInstance cur = p.getStatusEffect(StatusEffects.NIGHT_VISION);
            if (cur != null && cur.isInfinite()) p.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
        applied = false;
    }

    @Override
    public void onDisable() {
        if (external) return;     // World Time still needs the lit world
        MinecraftClient mc = MinecraftClient.getInstance();
        release(mc == null ? null : mc.player);
    }
}
