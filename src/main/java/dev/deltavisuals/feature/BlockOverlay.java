package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.render.Gfx;
import dev.deltavisuals.util.Palette;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

/** Fill + outline of the block under your crosshair, following its real VoxelShape. */
public final class BlockOverlay extends Module {
    private final Setting.Bool fill, outline, pulse;
    private final Setting.Num fillAlpha, lineAlpha, lineWidth, pulseSpeed, pulseAmount, flow;
    private final Setting.Mode colorMode;
    private final Setting.Color c1, c2;
    private final Palette pal = new Palette();
    private int ticks;

    public BlockOverlay() {
        super("Block Overlay", "Highlights the block you are looking at (fill + outline)", Category.WORLD, false);
        fill = add(new Setting.Bool("Fill", true));
        outline = add(new Setting.Bool("Outline", true));
        fillAlpha = add(new Setting.Num("Fill Opacity", 5, 100, 5, 30, "%"));
        fillAlpha.visibleIf(fill::get);
        lineAlpha = add(new Setting.Num("Outline Opacity", 10, 100, 5, 90, "%"));
        lineAlpha.visibleIf(outline::get);
        lineWidth = add(new Setting.Num("Outline Width", 0.5, 4, 0.1, 1.5));
        lineWidth.visibleIf(outline::get);
        pulse = add(new Setting.Bool("Pulse", true));
        pulseSpeed = add(new Setting.Num("Pulse Speed", 0.2, 4, 0.1, 1, "x"));
        pulseSpeed.visibleIf(pulse::get);
        pulseAmount = add(new Setting.Num("Pulse Amount", 5, 100, 5, 40, "%"));
        pulseAmount.visibleIf(pulse::get);
        colorMode = add(new Setting.Mode("Color", Palette.PLAIN, 0));
        c1 = add(new Setting.Color("Main Color", 0xFF33CCFF, false));
        c2 = add(new Setting.Color("Second Color", 0xFFFF4DA6, false));
        c1.visibleIf(() -> !colorMode.is("Rainbow") && !colorMode.is("Fire"));
        c2.visibleIf(() -> colorMode.is("Gradient") || colorMode.is("Neon"));
        flow = add(new Setting.Num("Color Flow Speed", 0.2, 5, 0.1, 1, "x"));
        flow.visibleIf(() -> !colorMode.is("Static"));
    }

    @Override public void onTick(MinecraftClient mc) { if (!mc.isPaused()) ticks++; }

    @Override
    public void onWorldRender() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || !(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos pos = hit.getBlockPos();
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return;
        VoxelShape shape = state.getOutlineShape(mc.world, pos);
        if (shape.isEmpty()) return;

        double time = ticks + Gfx.partial;
        float pm = 1f;
        if (pulse.get()) pm = 1f - pulseAmount.floatValue() / 100f * (0.5f + 0.5f * (float) Math.sin(time * 0.12 * pulseSpeed.get()));
        pal.set(colorMode.get(), c1.rgb(), c2.rgb(), flow.get(), time);
        final double ox = pos.getX(), oy = pos.getY(), oz = pos.getZ();
        final double e = 0.002;
        boolean xr = false;

        if (fill.get()) {
            final int col = pal.at(0.0, fillAlpha.floatValue() / 100f * pm);
            Gfx.use(false, xr);
            shape.forEachBox((x1, y1, z1, x2, y2, z2) ->
                Gfx.boxFill(ox + x1 - e, oy + y1 - e, oz + z1 - e, ox + x2 + e, oy + y2 + e, oz + z2 + e, col));
        }
        if (outline.get()) {
            final double hw = 0.012 * lineWidth.get();
            final float la = lineAlpha.floatValue() / 100f * (0.6f + 0.4f * pm);
            Gfx.use(true, xr);
            shape.forEachEdge((x1, y1, z1, x2, y2, z2) -> {
                double mid = ((x1 + x2) * 0.5 + (y1 + y2) * 0.5 * 1.7 + (z1 + z2) * 0.5 * 0.6) % 1.0;
                Gfx.line(ox + x1, oy + y1, oz + z1, ox + x2, oy + y2, oz + z2, hw, pal.at(mid, la));
            });
        }
    }
}
