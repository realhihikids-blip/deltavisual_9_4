package dev.deltavisuals.mixin;

import dev.deltavisuals.feature.Zoom;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Multiplies the camera field of view while Zoom is active. Skipped silently if the method cannot be found. */
@Mixin(GameRenderer.class)
public abstract class GameRendererFovMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void deltaVisuals$zoom(CallbackInfoReturnable<Float> cir) {
        try {
            Float v = cir.getReturnValue();
            if (v != null) cir.setReturnValue(Zoom.apply(v));
        } catch (Throwable ignored) { /* never break rendering over a cosmetic effect */ }
    }
}
