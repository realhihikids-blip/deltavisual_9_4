package dev.deltavisuals.mixin;

import dev.deltavisuals.feature.DeltaUsers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** See EntityDisplayNameMixin. Skipped silently when PlayerEntity does not declare getDisplayName itself. */
@Mixin(PlayerEntity.class)
public abstract class PlayerDisplayNameMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void deltaVisuals$logo(CallbackInfoReturnable<Text> cir) {
        cir.setReturnValue(DeltaUsers.decorateHead((PlayerEntity) (Object) this, cir.getReturnValue()));
    }
}
