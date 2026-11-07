package dev.deltavisuals.social;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.deltavisuals.DeltaVisuals;
import dev.deltavisuals.util.Compat;
import dev.deltavisuals.util.I18n;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who else uses Delta Visuals.
 *
 * A game server cannot tell clients about each other's mods, so this works through a small presence server that YOU run
 * (presence-server/server.py, plain Python, no dependencies). Every ~20 s the client sends the UUIDs of the players in the
 * tab list (plus its own UUID, if "Announce Me" is on) and gets back which of them are online with the mod.
 * Nothing but UUIDs is sent: no IP of the game server, no chat, no coordinates. All network work is asynchronous.
 *
 * Without a server address the mod still works from a manual list: /delta user add NICK.
 *
 * Files in .minecraft/config/delta_visuals/: presence.json (server address), delta_users.json (manual list).
 */
public final class Presence {
    private static final Logger LOG = LoggerFactory.getLogger("DeltaVisuals/Presence");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final long INTERVAL_MS = 20_000;

    private static final Set<UUID> ONLINE = ConcurrentHashMap.newKeySet();
    private static final Set<String> FRIENDS = ConcurrentHashMap.newKeySet();   // lower-case nicks

    private static volatile String statusKey = "Not started", statusArg = "";
    private static volatile boolean inFlight;
    private static String baseUrl = "";
    private static long nextAt;
    private static int fails;
    private static HttpClient http;

    private Presence() {}

    private static Path dir() { return FabricLoader.getInstance().getConfigDir().resolve("delta_visuals"); }

    // ------------------------------------------------------------------ config

    public static void init() {
        loadConfig();
        loadFriends();
    }

    public static void loadConfig() {
        baseUrl = "";
        try {
            Files.createDirectories(dir());
            Path f = dir().resolve("presence.json");
            if (!Files.exists(f)) {
                JsonObject o = new JsonObject();
                o.addProperty("url", "");
                o.addProperty("_help", "Address of your Delta Visuals presence server, for example http://203.0.113.5:8787 . "
                    + "Leave empty to use only the manual list (/delta user add NICK).");
                Files.writeString(f, GSON.toJson(o), StandardCharsets.UTF_8);
            } else {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                String u = o.has("url") ? o.get("url").getAsString().trim() : "";
                while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
                if (u.startsWith("http://") || u.startsWith("https://")) baseUrl = u;
                else if (!u.isEmpty()) LOG.warn("presence.json: url must start with http:// or https://");
            }
        } catch (Exception e) {
            LOG.warn("Cannot read presence.json", e);
        }
        fails = 0;
        nextAt = 0;
        setStatus(baseUrl.isEmpty() ? "No server set (manual list only)" : "Waiting", "");
    }

    private static void setStatus(String key, String arg) { statusKey = key; statusArg = arg; }

    /** Status line for the menu, translated at read time so a language switch applies at once. */
    public static String status() {
        String a = statusArg;
        return I18n.t(statusKey) + (a.isEmpty() ? "" : " " + a);
    }
    public static String serverUrl() { return baseUrl; }
    public static int onlineCount() { return ONLINE.size(); }

    // ------------------------------------------------------------------ manual list

    private static void loadFriends() {
        FRIENDS.clear();
        try {
            Path f = dir().resolve("delta_users.json");
            if (!Files.exists(f)) return;
            JsonElement e = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
            if (e.isJsonArray()) for (JsonElement n : e.getAsJsonArray()) FRIENDS.add(n.getAsString().toLowerCase(Locale.ROOT));
        } catch (Exception ex) {
            LOG.warn("Cannot read delta_users.json", ex);
        }
    }

    private static void saveFriends() {
        try {
            Files.createDirectories(dir());
            JsonArray a = new JsonArray();
            for (String n : new TreeSet<>(FRIENDS)) a.add(n);
            Files.writeString(dir().resolve("delta_users.json"), GSON.toJson(a), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Cannot write delta_users.json", e);
        }
    }

    public static boolean addFriend(String nick) {
        boolean added = FRIENDS.add(nick.toLowerCase(Locale.ROOT));
        if (added) saveFriends();
        return added;
    }

    public static boolean removeFriend(String nick) {
        boolean removed = FRIENDS.remove(nick.toLowerCase(Locale.ROOT));
        if (removed) saveFriends();
        return removed;
    }

    public static List<String> friends() { return new ArrayList<>(new TreeSet<>(FRIENDS)); }

    // ------------------------------------------------------------------ lookup

    /** True if this player is known to use Delta Visuals (presence server answer or manual list). Cheap, any thread. */
    public static boolean isUser(UUID id, String name) {
        if (id != null && ONLINE.contains(id)) return true;
        return name != null && !FRIENDS.isEmpty() && FRIENDS.contains(name.toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ sync

    /** Call every client tick while the module is on; does a request at most every ~20 s, never blocks. */
    public static void tick(MinecraftClient mc, boolean announce) {
        if (mc.world == null || mc.player == null || mc.getNetworkHandler() == null) return;
        if (baseUrl.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (inFlight || now < nextAt) return;

        JsonArray uuids = new JsonArray();
        int n = 0;
        for (Object o : Compat.playerList(mc.getNetworkHandler())) {
            if (!(o instanceof PlayerListEntry e)) continue;
            UUID id = Compat.profileId(e.getProfile());
            if (id != null && n++ < 200) uuids.add(id.toString());
        }
        JsonObject body = new JsonObject();
        if (announce) body.addProperty("self", mc.player.getUuid().toString());
        body.addProperty("v", DeltaVisuals.VERSION);
        body.add("uuids", uuids);

        inFlight = true;
        nextAt = now + INTERVAL_MS;
        try {
            if (http == null) http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/sync"))
                .timeout(Duration.ofSeconds(6))
                .header("Content-Type", "application/json")
                .header("User-Agent", "DeltaVisuals/" + DeltaVisuals.VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
                .build();
            http.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .whenComplete((resp, err) -> finish(resp, err));
        } catch (Throwable t) {
            fail(t.getMessage());
        }
    }

    private static void finish(HttpResponse<String> resp, Throwable err) {
        try {
            if (err != null) { fail(err.getClass().getSimpleName()); return; }
            if (resp.statusCode() != 200) { fail("HTTP " + resp.statusCode()); return; }
            JsonObject o = JsonParser.parseString(resp.body()).getAsJsonObject();
            Set<UUID> fresh = new java.util.HashSet<>();
            if (o.has("online")) {
                for (JsonElement e : o.getAsJsonArray("online")) {
                    try { fresh.add(UUID.fromString(e.getAsString())); } catch (IllegalArgumentException ignored) { }
                }
            }
            ONLINE.retainAll(fresh);
            ONLINE.addAll(fresh);
            fails = 0;
            setStatus("Connected, online:", String.valueOf(fresh.size()));
        } catch (Throwable t) {
            fail("bad answer");
        } finally {
            inFlight = false;
        }
    }

    private static void fail(String why) {
        fails = Math.min(fails + 1, 5);
        nextAt = System.currentTimeMillis() + Math.min(120_000L, INTERVAL_MS << fails);
        setStatus("Server unreachable", "(" + why + ")");
        inFlight = false;
    }

    /** Forget the answers (used when the module is turned off or you leave a world). */
    public static void clearOnline() { ONLINE.clear(); }
}
