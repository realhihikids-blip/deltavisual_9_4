package dev.deltavisuals;

import dev.deltavisuals.config.ConfigManager;
import dev.deltavisuals.core.BindManager;
import dev.deltavisuals.core.Modules;
import dev.deltavisuals.feature.DeltaCommands;
import dev.deltavisuals.feature.Markers;
import dev.deltavisuals.social.Presence;
import dev.deltavisuals.hud.HudRenderer;
import dev.deltavisuals.render.WorldHook;
import dev.deltavisuals.util.Compat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

public final class DeltaVisuals implements ClientModInitializer {
    public static final String ID = "delta_visuals";
    public static final String VERSION = "0.9.4";

    @Override
    public void onInitializeClient() {
        Modules.init();
        ConfigManager.init();
        WorldHook.init();
        Markers.init();
        Presence.init();
        dev.deltavisuals.feature.ScreenAnim.init();
        DeltaCommands.register();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            BindManager.poll(mc);
            Modules.tick(mc);
            ConfigManager.tick();
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.of(ID, "hud"), (graphics, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            BindManager.poll(mc); // per-frame poll so very short taps are not missed
            HudRenderer.render(graphics, mc);
            try {
                Markers.renderHud(graphics, mc);
            } catch (Throwable t) {
                if (Modules.MARKERS != null) Modules.MARKERS.setEnabled(false); // a broken tag must never take the HUD down
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
            Compat.restoreClouds(mc);        // give the vanilla cloud option back if a sky module had hidden it
            ConfigManager.save();
        });
    }
}
