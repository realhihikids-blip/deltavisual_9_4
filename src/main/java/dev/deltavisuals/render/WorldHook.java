package dev.deltavisuals.render;

import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Modules;
import dev.deltavisuals.util.Compat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.BufferAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hooks the Fabric world render event once and lets every enabled module draw its geometry through {@link Gfx}.
 *
 * Fabric API 0.137+ for 1.21.10/1.21.11 re-introduced the world render events in a NEW package:
 * net.fabricmc.fabric.api.client.rendering.v1.world (the old ...rendering.v1.WorldRenderEvents is gone,
 * and AFTER_TRANSLUCENT no longer exists). BEFORE_TRANSLUCENT is the event used by the official Fabric docs
 * page "Rendering in the World" for 1.21.11.
 *
 * We draw into our OWN VertexConsumerProvider.Immediate, so we do not depend on WorldRenderContext.consumers().
 */
public final class WorldHook {
    private static final Logger LOG = LoggerFactory.getLogger("DeltaVisuals/Render");

    private static final BufferAllocator ALLOCATOR = new BufferAllocator(1 << 18);
    private static final VertexConsumerProvider.Immediate BUFFERS = VertexConsumerProvider.immediate(ALLOCATOR);

    private WorldHook() {}

    public static void init() {
        WorldRenderEvents.BEFORE_TRANSLUCENT.register(WorldHook::render);
    }

    private static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;
        if (ctx.matrices() == null) return;

        Camera cam = mc.gameRenderer.getCamera();
        Gfx.begin(BUFFERS, ctx.matrices().peek().getPositionMatrix(), cam, Compat.partialTick(mc));
        try {
            for (Module m : Modules.all()) {
                if (!m.isEnabled()) continue;
                try {
                    m.onWorldRender();
                    m.crashes = 0;
                } catch (Throwable t) {
                    // only repeated crashes switch the module off (a single bad frame must not do it, the state is saved)
                    if (++m.crashes >= 3) {
                        LOG.error("Module {} crashed in onWorldRender {} times in a row and was disabled", m.name, m.crashes, t);
                        m.setEnabled(false);
                        m.crashes = 0;
                    } else {
                        LOG.warn("Module {} threw in onWorldRender (ignored, {}/3)", m.name, m.crashes, t);
                    }
                }
            }
        } finally {
            Gfx.end();
        }
    }
}
