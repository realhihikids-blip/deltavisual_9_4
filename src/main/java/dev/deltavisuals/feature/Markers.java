package dev.deltavisuals.feature;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.deltavisuals.core.Bind;
import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Modules;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.gui.Logo;
import dev.deltavisuals.gui.Theme;
import dev.deltavisuals.gui.Ui;
import dev.deltavisuals.render.Gfx;
import dev.deltavisuals.util.ColorUtil;
import dev.deltavisuals.util.I18n;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Personal waypoints. Only you see them (nothing is sent anywhere).
 *
 *  - Bind: puts a marker right where you stand (or where you look). Pressing it again next to an existing marker removes it.
 *  - Coordinates: /delta mark X Y Z [name], or "Mark From Clipboard" (understands "100 64 -200" and the F3+C text).
 *  - Drawn as a light beam, a ground ring and a floating crystal through the world, plus a name / distance tag on screen
 *    that stays on the screen edge when the marker is off screen.
 *
 * Markers are remembered per server (or singleplayer) and per dimension in config/delta_visuals/markers.json.
 */
public final class Markers extends Module {
    public record Marker(String server, String dim, String name, double x, double y, double z) { }

    private static final Logger LOG = LoggerFactory.getLogger("DeltaVisuals/Markers");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern NUM = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final double TAU = Math.PI * 2;
    private static final List<Marker> ALL = new ArrayList<>();

    private final Setting.Mode placeAt;
    private final Setting.Num maxMarkers, removeRadius, beamHeight, opacity;
    private final Setting.Bool beam, label;
    private final Setting.Color color;
    private final List<Marker> vis = new ArrayList<>();

    public Markers() {
        super("Markers", "Waypoints: a key puts a marker where you stand, /delta mark X Y Z puts one at coordinates. Visible only to you", Category.WORLD, true);
        action = true;                         // the key calls onBind(); on/off is the switch in the menu
        defaultBind(Bind.key(GLFW.GLFW_KEY_K));

        placeAt = add(new Setting.Mode("Key Places Marker", new String[]{"At My Feet", "Where I Look"}, 0));
        removeRadius = add(new Setting.Num("Key Removes Within", 1, 10, 0.5, 3, " m"));
        maxMarkers = add(new Setting.Num("Max Markers", 1, 32, 1, 12));
        add(new Setting.Header("Look"));
        color = add(new Setting.Color("Marker Color", 0xFFA77BFF, false));
        beam = add(new Setting.Bool("Light Beam", true));
        beamHeight = add(new Setting.Num("Beam Height", 8, 64, 1, 28, " m"));
        beamHeight.visibleIf(beam::get);
        label = add(new Setting.Bool("Name And Distance Tag", true));
        opacity = add(new Setting.Num("Opacity", 10, 100, 5, 80, "%"));
        add(new Setting.Header("Actions"));
        add(new Setting.Action("Mark Here", () -> placeHere(MinecraftClient.getInstance())));
        add(new Setting.Action("Mark From Clipboard", () -> markFromClipboard(MinecraftClient.getInstance())));
        add(new Setting.Action("Remove Nearest", () -> removeNearest(MinecraftClient.getInstance())));
        add(new Setting.Action("Clear All Here", () -> clearHere(MinecraftClient.getInstance())));
        add(new Setting.Info("Markers Here", () -> String.valueOf(visible(MinecraftClient.getInstance()).size())));
        add(new Setting.Info("By Coordinates", () -> "/delta mark X Y Z name"));
    }

    // ------------------------------------------------------------------ storage

    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("delta_visuals").resolve("markers.json"); }

    public static void init() {
        ALL.clear();
        try {
            if (!Files.exists(file())) return;
            JsonElement root = JsonParser.parseString(Files.readString(file(), StandardCharsets.UTF_8));
            if (!root.isJsonArray()) return;
            for (JsonElement e : root.getAsJsonArray()) {
                try {
                    JsonObject o = e.getAsJsonObject();
                    ALL.add(new Marker(o.get("server").getAsString(), o.get("dim").getAsString(), o.get("name").getAsString(),
                        o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble()));
                } catch (Exception ignored) { /* skip a broken entry, keep the rest */ }
            }
        } catch (Exception e) {
            LOG.warn("Cannot read markers.json", e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            JsonArray a = new JsonArray();
            for (Marker m : ALL) {
                JsonObject o = new JsonObject();
                o.addProperty("server", m.server());
                o.addProperty("dim", m.dim());
                o.addProperty("name", m.name());
                o.addProperty("x", m.x());
                o.addProperty("y", m.y());
                o.addProperty("z", m.z());
                a.add(o);
            }
            Files.writeString(file(), GSON.toJson(a), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Cannot write markers.json", e);
        }
    }

    // ------------------------------------------------------------------ context (server + dimension)

    private static String serverKey(MinecraftClient mc) {
        var s = mc.getCurrentServerEntry();
        return s == null ? "singleplayer" : s.address.toLowerCase(Locale.ROOT);
    }

    private static String dimKey(MinecraftClient mc) {
        return mc.world == null ? "" : mc.world.getRegistryKey().getValue().toString();
    }

    /** Markers of the current server and dimension. The returned list is reused: do not keep it. */
    public List<Marker> visible(MinecraftClient mc) {
        vis.clear();
        if (mc == null || mc.world == null) return vis;
        String sv = serverKey(mc), dim = dimKey(mc);
        for (Marker m : ALL) if (m.server().equals(sv) && m.dim().equals(dim)) vis.add(m);
        return vis;
    }

    private List<Marker> snapshot(MinecraftClient mc) { return new ArrayList<>(visible(mc)); }

    // ------------------------------------------------------------------ actions

    private static void say(MinecraftClient mc, String s) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("[Delta] " + I18n.t(s)), true);
    }

    /** Adds a marker; the oldest one of this server / dimension is dropped when the limit is reached. */
    public Marker addMarker(MinecraftClient mc, String name, double x, double y, double z) {
        if (mc.world == null) return null;
        String sv = serverKey(mc), dim = dimKey(mc);
        List<Marker> here = snapshot(mc);
        while (here.size() >= maxMarkers.intValue()) ALL.remove(here.remove(0));
        if (name == null || name.isBlank()) name = I18n.t("Mark") + " " + (here.size() + 1);
        if (name.length() > 32) name = name.substring(0, 32);
        Marker m = new Marker(sv, dim, name.trim(), x, y, z);
        ALL.add(m);
        save();
        return m;
    }

    public void placeHere(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        double x = Math.floor(mc.player.getX()) + 0.5, y = mc.player.getY(), z = Math.floor(mc.player.getZ()) + 0.5;
        if (placeAt.index() == 1 && mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            Vec3d p = mc.crosshairTarget.getPos();
            x = p.x; y = p.y; z = p.z;
        }
        Marker m = addMarker(mc, null, x, y, z);
        if (m != null) say(mc, "Marker placed: " + m.name());
    }

    public void markFromClipboard(MinecraftClient mc) {
        double[] c = parseCoords(mc.keyboard.getClipboard(), mc);
        if (c == null) { say(mc, "No coordinates in the clipboard (need X Y Z)"); return; }
        Marker m = addMarker(mc, null, c[0], c[1], c[2]);
        if (m != null) say(mc, "Marker placed: " + m.name());
    }

    public void removeNearest(MinecraftClient mc) {
        Marker m = nearest(mc, Double.MAX_VALUE);
        if (m == null) { say(mc, "No markers here"); return; }
        ALL.remove(m);
        save();
        say(mc, "Marker removed: " + m.name());
    }

    public void clearHere(MinecraftClient mc) {
        List<Marker> here = snapshot(mc);
        if (here.isEmpty()) { say(mc, "No markers here"); return; }
        ALL.removeAll(here);
        save();
        say(mc, "All markers removed");
    }

    /** 1-based index in the list of the current server / dimension. */
    public Marker removeIndex(MinecraftClient mc, int index) {
        List<Marker> here = snapshot(mc);
        if (index < 1 || index > here.size()) return null;
        Marker m = here.get(index - 1);
        ALL.remove(m);
        save();
        return m;
    }

    public List<Marker> list(MinecraftClient mc) { return snapshot(mc); }

    private Marker nearest(MinecraftClient mc, double maxDist) {
        if (mc.player == null) return null;
        Marker best = null;
        double bd = maxDist * maxDist;
        for (Marker m : visible(mc)) {
            double dx = m.x() - mc.player.getX(), dy = m.y() - mc.player.getY(), dz = m.z() - mc.player.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            if (d <= bd) { bd = d; best = m; }
        }
        return best;
    }

    /**
     * First three numbers in the text = X Y Z ("100 64 -200", "100, 64, -200", F3+C "/execute ... tp @s 100.5 64 -200 ..").
     * Two numbers are taken as X Z at your current height.
     */
    public static double[] parseCoords(String s, MinecraftClient mc) {
        if (s == null) return null;
        List<Double> n = new ArrayList<>();
        Matcher m = NUM.matcher(s);
        while (m.find() && n.size() < 3) {
            try { n.add(Double.parseDouble(m.group())); } catch (NumberFormatException ignored) { }
        }
        if (n.size() >= 3) return new double[]{n.get(0), n.get(1), n.get(2)};
        if (n.size() == 2 && mc != null && mc.player != null) return new double[]{n.get(0), mc.player.getY(), n.get(1)};
        return null;
    }

    // ------------------------------------------------------------------ key

    @Override
    public void onBind(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        Marker near = nearest(mc, removeRadius.get());
        if (near != null) {
            ALL.remove(near);
            save();
            say(mc, "Marker removed: " + near.name());
        } else {
            placeHere(mc);
        }
    }

    // ------------------------------------------------------------------ world render

    private int rgb(float alpha) { return ColorUtil.withAlpha(0xFF000000 | color.rgb(), Math.round(255f * Math.max(0f, Math.min(1f, alpha)))); }

    @Override
    public void onWorldRender() {
        MinecraftClient mc = MinecraftClient.getInstance();
        List<Marker> list = visible(mc);
        if (list.isEmpty()) return;
        boolean xray = false;
        float op = opacity.floatValue() / 100f;
        double time = System.nanoTime() / 1_000_000_000.0;

        // pass 1: additive glow (beam + ground ring)
        Gfx.use(true, xray);
        for (Marker m : list) {
            double d = Gfx.distToCamera(m.x(), m.y(), m.z());
            float fade = d > 160 ? (float) Math.max(0, 1 - (d - 160) / 60) : 1f;
            if (fade <= 0f) continue;
            fade *= (float) Math.min(1.0, d / 3.0);                 // soften when you stand inside it
            double k = Math.max(1.0, d / 24.0);                      // keep a readable size at a distance
            if (beam.get()) {
                double hw = 0.10 * k, h = beamHeight.get() * Math.min(k, 2.5);
                int cb = rgb(0.55f * op * fade), ct = rgb(0f);
                Gfx.quad(m.x() - hw, m.y(), m.z(), cb, m.x() + hw, m.y(), m.z(), cb, m.x() + hw, m.y() + h, m.z(), ct, m.x() - hw, m.y() + h, m.z(), ct);
                Gfx.quad(m.x(), m.y(), m.z() - hw, cb, m.x(), m.y(), m.z() + hw, cb, m.x(), m.y() + h, m.z() + hw, ct, m.x(), m.y() + h, m.z() - hw, ct);
            }
            Gfx.ringFlat(m.x(), m.y() + 0.03, m.z(), 0.42 * k, 0.60 * k, time, TAU, 32, rgb(0.15f * op * fade), rgb(0.75f * op * fade));
        }

        // pass 2: the floating crystal (normal alpha so it looks solid)
        Gfx.use(false, xray);
        for (Marker m : list) {
            double d = Gfx.distToCamera(m.x(), m.y(), m.z());
            float fade = d > 160 ? (float) Math.max(0, 1 - (d - 160) / 60) : 1f;
            if (fade <= 0f) continue;
            fade *= (float) Math.min(1.0, d / 3.0);
            double k = Math.max(1.0, d / 24.0);
            double r = 0.26 * k, cx = m.x(), cz = m.z();
            double cy = m.y() + (1.7 + 0.10 * Math.sin(time * 2.0)) * Math.min(k, 3.0);
            int base = color.rgb();
            int light = ColorUtil.lerp(0xFF000000 | base, 0xFFFFFFFF, 0.45f), dark = ColorUtil.lerp(0xFF000000 | base, 0xFF000000, 0.40f);
            float a = 0.95f * op * fade;
            double a0 = time * 1.3;
            for (int i = 0; i < 4; i++) {
                double an1 = a0 + i * Math.PI / 2, an2 = a0 + (i + 1) * Math.PI / 2;
                double x1 = cx + Math.cos(an1) * r, z1 = cz + Math.sin(an1) * r, x2 = cx + Math.cos(an2) * r, z2 = cz + Math.sin(an2) * r;
                int top = ColorUtil.withAlpha((i & 1) == 0 ? light : ColorUtil.lerp(light, 0xFF000000 | base, 0.55f), Math.round(255 * a));
                int bot = ColorUtil.withAlpha((i & 1) == 0 ? ColorUtil.lerp(dark, 0xFF000000 | base, 0.4f) : dark, Math.round(255 * a));
                Gfx.tri(cx, cy + r * 1.5, cz, x1, cy, z1, x2, cy, z2, top);
                Gfx.tri(cx, cy - r * 1.5, cz, x2, cy, z2, x1, cy, z1, bot);
            }
        }
    }

    // ------------------------------------------------------------------ on-screen tags

    private static String distText(double d) {
        boolean ru = I18n.russian();
        if (d < 1000) return Math.round(d) + (ru ? " м" : " m");
        return String.format(Locale.ROOT, "%.1f", d / 1000) + (ru ? " км" : " km");
    }

    /** Called every frame from the HUD callback. Projects every marker with the camera of the last world render. */
    public static void renderHud(DrawContext g, MinecraftClient mc) {
        Markers mod = Modules.MARKERS;
        if (mod == null || !mod.isEnabled() || !mod.label.get()) return;
        if (mc.player == null || mc.world == null || mc.options.hudHidden || mc.currentScreen != null) return;
        if (Gfx.lookX == 0f && Gfx.lookY == 0f && Gfx.lookZ == 0f) return;       // no world frame drawn yet
        List<Marker> list = mod.visible(mc);
        if (list.isEmpty()) return;

        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        double tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        double aspect = sw / (double) sh;
        int accent = 0xFF000000 | mod.color.rgb();
        int logoH = 10, logoW = Logo.width(logoH), th = Ui.th();
        int h = Math.max(th, logoH) + 6;

        for (Marker m : list) {
            double rx = m.x() - Gfx.camX, ry = m.y() + 2.3 - Gfx.camY, rz = m.z() - Gfx.camZ;
            double x = rx * Gfx.rightX + ry * Gfx.rightY + rz * Gfx.rightZ;
            double y = rx * Gfx.upX + ry * Gfx.upY + rz * Gfx.upZ;
            double z = rx * Gfx.lookX + ry * Gfx.lookY + rz * Gfx.lookZ;

            double nx, ny;
            boolean front = z > 0.05;
            if (front) {
                nx = x / (z * tanHalf * aspect);
                ny = y / (z * tanHalf);
            } else {                                   // behind the camera: point the way you must turn
                double l = Math.hypot(x, y);
                if (l < 1e-6) { nx = 0; ny = -1; } else { nx = -x / l * 10; ny = -y / l * 10; }
            }
            boolean on = front && Math.abs(nx) <= 0.9 && Math.abs(ny) <= 0.85;
            if (!on) {
                double s = Math.max(Math.abs(nx) / 0.9, Math.abs(ny) / 0.85);
                if (s < 1e-6) continue;
                nx /= s; ny /= s;
            }
            int cxp = (int) Math.round((nx * 0.5 + 0.5) * sw), cyp = (int) Math.round((0.5 - ny * 0.5) * sh);

            double dx = m.x() - mc.player.getX(), dy = m.y() - mc.player.getY(), dz = m.z() - mc.player.getZ();
            String dist = distText(Math.sqrt(dx * dx + dy * dy + dz * dz));
            String name = Ui.fit(m.name(), 130);
            int w = 6 + logoW + 5 + Ui.tw(name) + 8 + Ui.tw(dist) + 6;
            int px = Math.max(3, Math.min(sw - w - 3, cxp - w / 2)), py = Math.max(3, Math.min(sh - h - 3, cyp - h / 2));

            Ui.round(g, px, py, w, h, 5, on ? 0xC8120E24 : 0xE0120E24);
            Ui.rect(g, px + 5, py + h - 2, w - 10, 1, ColorUtil.withAlpha(accent, 150));
            Logo.draw(g, px + 6, py + (h - logoH) / 2, logoH);
            int ty = py + (h - th) / 2;
            Ui.text(g, name, px + 6 + logoW + 5, ty, Theme.text());
            Ui.textRight(g, dist, px + w - 6, ty, accent);
        }
    }
}
