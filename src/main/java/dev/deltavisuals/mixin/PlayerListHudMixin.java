package dev.deltavisuals.mixin;

import dev.deltavisuals.feature.DeltaUsers;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the Delta Visuals logo to the name of a tab list entry. require = 0: if the method is renamed the logo just does not show. */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true, require = 0)
    private void deltaVisuals$logo(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        cir.setReturnValue(DeltaUsers.decorateTab(entry, cir.getReturnValue()));
    }
}
