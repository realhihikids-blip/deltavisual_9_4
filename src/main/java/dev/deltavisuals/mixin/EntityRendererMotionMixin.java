package dev.deltavisuals.mixin;

import dev.deltavisuals.feature.ItemMotion;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Item Motion: scales the animation clock (state.age) of dropped items. Skipped silently if not found. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMotionMixin {
    @Inject(method = "updateRenderState", at = @At("RETURN"), require = 0)
    private void deltaVisuals$itemMotion(Entity entity, EntityRenderState state, float tickProgress, CallbackInfo ci) {
        try {
            if (entity instanceof ItemEntity) state.age = ItemMotion.scale(state.age);
        } catch (Throwable ignored) { }
    }
}
