package dev.deltavisuals.feature;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.deltavisuals.core.Modules;
import dev.deltavisuals.social.Presence;
import dev.deltavisuals.util.I18n;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/**
 * Client-side commands (never sent to the server):
 *
 *   /delta mark here [name]            marker where you stand
 *   /delta mark X Y Z [name]           marker at coordinates
 *   /delta mark clip [name]            marker from coordinates in the clipboard
 *   /delta mark list | remove N | clear
 *   /delta user add|remove NICK | list   manual list of players who use Delta Visuals (logo next to their nick)
 */
public final class DeltaCommands {
    private DeltaCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> build(dispatcher));
    }

    private static void reply(CommandContext<FabricClientCommandSource> c, String s) {
        c.getSource().sendFeedback(Text.literal("[Delta] " + I18n.t(s)));
    }

    private static boolean ready(CommandContext<FabricClientCommandSource> c) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (Modules.MARKERS == null || mc.world == null || mc.player == null) { reply(c, "Join a world first"); return false; }
        if (!Modules.MARKERS.isEnabled()) { reply(c, "Turn the Markers module on in the menu first"); return false; }
        return true;
    }

    private static String optName(CommandContext<FabricClientCommandSource> c) {
        try { return StringArgumentType.getString(c, "name"); } catch (IllegalArgumentException e) { return null; }
    }

    private static String fmt(double v) { return String.format(Locale.ROOT, "%.1f", v); }

    private static int placed(CommandContext<FabricClientCommandSource> c, double x, double y, double z) {
        if (!ready(c)) return 0;
        MinecraftClient mc = MinecraftClient.getInstance();
        Markers.Marker m = Modules.MARKERS.addMarker(mc, optName(c), x, y, z);
        if (m == null) return 0;
        reply(c, I18n.t("Marker placed: ") + m.name() + "  (" + fmt(x) + " " + fmt(y) + " " + fmt(z) + ")");
        return 1;
    }

    private static void build(CommandDispatcher<FabricClientCommandSource> d) {
        var here = ClientCommandManager.literal("here")
            .executes(c -> placeHere(c))
            .then(ClientCommandManager.argument("name", StringArgumentType.greedyString()).executes(c -> placeHere(c)));

        var clip = ClientCommandManager.literal("clip")
            .executes(c -> fromClip(c))
            .then(ClientCommandManager.argument("name", StringArgumentType.greedyString()).executes(c -> fromClip(c)));

        var coords = ClientCommandManager.argument("x", DoubleArgumentType.doubleArg())
            .then(ClientCommandManager.argument("y", DoubleArgumentType.doubleArg())
                .then(ClientCommandManager.argument("z", DoubleArgumentType.doubleArg())
                    .executes(c -> placed(c, DoubleArgumentType.getDouble(c, "x"), DoubleArgumentType.getDouble(c, "y"), DoubleArgumentType.getDouble(c, "z")))
                    .then(ClientCommandManager.argument("name", StringArgumentType.greedyString())
                        .executes(c -> placed(c, DoubleArgumentType.getDouble(c, "x"), DoubleArgumentType.getDouble(c, "y"), DoubleArgumentType.getDouble(c, "z"))))));

        var list = ClientCommandManager.literal("list").executes(c -> {
            if (!ready(c)) return 0;
            List<Markers.Marker> l = Modules.MARKERS.list(MinecraftClient.getInstance());
            if (l.isEmpty()) { reply(c, "No markers here"); return 1; }
            for (int i = 0; i < l.size(); i++) {
                Markers.Marker m = l.get(i);
                c.getSource().sendFeedback(Text.literal((i + 1) + ". " + m.name() + "  " + fmt(m.x()) + " " + fmt(m.y()) + " " + fmt(m.z())));
            }
            return 1;
        });

        var remove = ClientCommandManager.literal("remove")
            .then(ClientCommandManager.argument("n", IntegerArgumentType.integer(1)).executes(c -> {
                if (!ready(c)) return 0;
                Markers.Marker m = Modules.MARKERS.removeIndex(MinecraftClient.getInstance(), IntegerArgumentType.getInteger(c, "n"));
                reply(c, m == null ? "No marker with this number" : I18n.t("Marker removed: ") + m.name());
                return m == null ? 0 : 1;
            }));

        var clear = ClientCommandManager.literal("clear").executes(c -> {
            if (!ready(c)) return 0;
            Modules.MARKERS.clearHere(MinecraftClient.getInstance());
            return 1;
        });

        var mark = ClientCommandManager.literal("mark").then(here).then(clip).then(list).then(remove).then(clear).then(coords);

        var userAdd = ClientCommandManager.literal("add")
            .then(ClientCommandManager.argument("nick", StringArgumentType.word()).executes(c -> {
                String n = StringArgumentType.getString(c, "nick");
                reply(c, Presence.addFriend(n) ? I18n.t("Added to the Delta users list: ") + n : I18n.t("Already in the list: ") + n);
                return 1;
            }));
        var userRemove = ClientCommandManager.literal("remove")
            .then(ClientCommandManager.argument("nick", StringArgumentType.word()).executes(c -> {
                String n = StringArgumentType.getString(c, "nick");
                reply(c, Presence.removeFriend(n) ? I18n.t("Removed from the list: ") + n : I18n.t("Not in the list: ") + n);
                return 1;
            }));
        var userList = ClientCommandManager.literal("list").executes(c -> {
            List<String> l = Presence.friends();
            reply(c, l.isEmpty() ? "The manual list is empty" : String.join(", ", l));
            return 1;
        });
        var user = ClientCommandManager.literal("user").then(userAdd).then(userRemove).then(userList);

        d.register(ClientCommandManager.literal("delta").then(mark).then(user));
    }

    private static int placeHere(CommandContext<FabricClientCommandSource> c) {
        if (!ready(c)) return 0;
        MinecraftClient mc = MinecraftClient.getInstance();
        return placed(c, Math.floor(mc.player.getX()) + 0.5, mc.player.getY(), Math.floor(mc.player.getZ()) + 0.5);
    }

    private static int fromClip(CommandContext<FabricClientCommandSource> c) {
        if (!ready(c)) return 0;
        MinecraftClient mc = MinecraftClient.getInstance();
        double[] p = Markers.parseCoords(mc.keyboard.getClipboard(), mc);
        if (p == null) { reply(c, "No coordinates in the clipboard (need X Y Z)"); return 0; }
        return placed(c, p[0], p[1], p[2]);
    }
}
