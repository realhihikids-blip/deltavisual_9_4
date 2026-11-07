package dev.deltavisuals.mixin;

import dev.deltavisuals.feature.DeltaUsers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The name tag above a player's head is built from Entity.getDisplayName(). The logo is appended there for players
 * known to use Delta Visuals. A second mixin covers PlayerEntity in case it overrides the method; the logo is never added twice.
 */
@Mixin(Entity.class)
public abstract class EntityDisplayNameMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true, require = 0)
    private void deltaVisuals$logo(CallbackInfoReturnable<Text> cir) {
        if ((Object) this instanceof PlayerEntity p) cir.setReturnValue(DeltaUsers.decorateHead(p, cir.getReturnValue()));
    }
}
