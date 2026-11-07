package dev.deltavisuals.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.deltavisuals.DeltaVisuals;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

/**
 * Our own pipelines/layers. Position+colour quads, no culling (two-sided), no depth write.
 * Four variants: {alpha, additive} x {depth tested, through walls}.
 *
 * Built lazily on first use. This is the single place that talks to the 1.21.11 RenderPipeline/RenderLayer API.
 */
public final class Pipelines {
    private static final RenderLayer[] LAYERS = new RenderLayer[4];

    private Pipelines() {}

    public static RenderLayer layer(boolean additive, boolean xray) {
        int i = (additive ? 1 : 0) | (xray ? 2 : 0);
        RenderLayer l = LAYERS[i];
        if (l == null) {
            l = build((additive ? "add" : "alpha") + (xray ? "_xray" : ""), additive, xray);
            LAYERS[i] = l;
        }
        return l;
    }

    public static RenderLayer[] all() {
        for (int i = 0; i < 4; i++) layer((i & 1) != 0, (i & 2) != 0);
        return LAYERS;
    }

    private static RenderLayer build(String name, boolean additive, boolean xray) {
        RenderPipeline pipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                .withLocation(Identifier.of(DeltaVisuals.ID, "pipeline/" + name))
                .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                .withBlend(additive ? BlendFunction.ADDITIVE : BlendFunction.TRANSLUCENT)
                .withCull(false)
                .withDepthWrite(false)
                .withDepthTestFunction(xray ? DepthTestFunction.NO_DEPTH_TEST : DepthTestFunction.LEQUAL_DEPTH_TEST)
                .build());
        // 1.21.11 style. If this line fails to compile, the older form is:
        // RenderLayer.of("dv_" + name, 262144, false, true, pipeline, RenderLayer.MultiPhaseParameters.builder().build(false))
        return RenderLayer.of("dv_" + name, RenderSetup.builder(pipeline).expectedBufferSize(262144).translucent().build());
    }
}
