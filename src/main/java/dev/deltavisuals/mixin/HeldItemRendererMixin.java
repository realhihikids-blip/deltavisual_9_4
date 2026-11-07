package dev.deltavisuals.mixin;

import dev.deltavisuals.feature.ItemPosition;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wraps the first-person item render in a matrix push/pop and applies the Item Position transform inside it
 * (after the vanilla equip / swing frame, so the swing animation stays untouched).
 * The push and pop are always paired, whether or not the module is enabled.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void deltaVisuals$begin(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand,
                                    float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                    OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        matrices.push();
        ItemPosition.apply(matrices, item, hand, player, swingProgress, equipProgress);
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void deltaVisuals$end(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand,
                                  float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                  OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
        matrices.pop();
    }
}
