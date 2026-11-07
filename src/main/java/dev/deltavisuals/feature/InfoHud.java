package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.hud.TextHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class InfoHud extends TextHud {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final Setting.Bool fps, ping, coords, facing, time;

    public InfoHud() {
        super("Info", "FPS, ping, coordinates, facing and clock", Category.HUD, true, 0.005, 0.01);
        fps = add(new Setting.Bool("FPS", true));
        ping = add(new Setting.Bool("Ping", true));
        coords = add(new Setting.Bool("Coordinates", true));
        facing = add(new Setting.Bool("Facing", false));
        time = add(new Setting.Bool("Clock", false));
    }

    @Override
    protected void collect(MinecraftClient mc, List<Line> out, boolean editing) {
        int col = color.get();
        if (mc.player == null) return;
        if (fps.get()) out.add(new Line(mc.getCurrentFps() + " FPS", col));
        if (ping.get() && mc.getNetworkHandler() != null) {
            PlayerListEntry e = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (e != null) out.add(new Line(e.getLatency() + " ms", col));
        }
        if (coords.get()) {
            out.add(new Line(String.format(Locale.ROOT, "XYZ %.1f %.1f %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()), col));
        }
        if (facing.get()) {
            String f = mc.player.getHorizontalFacing().asString();
            out.add(new Line("Facing " + Character.toUpperCase(f.charAt(0)) + f.substring(1), col));
        }
        if (time.get()) out.add(new Line(LocalTime.now().format(TIME), col));
    }
}
