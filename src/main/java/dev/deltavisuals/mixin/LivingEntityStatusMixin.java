package dev.deltavisuals.mixin;

import dev.deltavisuals.core.Modules;
import dev.deltavisuals.feature.TotemSoul;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The server tells clients about a popped totem with entity status 35. handleStatus runs on the client only
 * (also in singleplayer the client-side copy of the entity), so this is a pure notification: nothing is changed.
 * Skipped silently if the method cannot be found (require = 0).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityStatusMixin {
    @Inject(method = "handleStatus", at = @At("HEAD"), require = 0)
    private void deltaVisuals$totemPop(byte status, CallbackInfo ci) {
        if (status != 35) return;
        try {
            TotemSoul t = Modules.TOTEM_SOUL;
            if (t != null) t.onPop((LivingEntity) (Object) this);
        } catch (Throwable ignored) { /* never break the game over a cosmetic effect */ }
        try {
            var pc = Modules.POP_COUNTER;
            if (pc != null) pc.onPop((LivingEntity) (Object) this);
        } catch (Throwable ignored) { }
    }
}
