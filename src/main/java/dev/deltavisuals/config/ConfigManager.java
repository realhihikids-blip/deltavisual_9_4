package dev.deltavisuals.config;

import com.google.gson.*;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Modules;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/** JSON profiles in .minecraft/config/delta_visuals/profiles. Debounced autosave, validated load, corrupt-file recovery. */
public final class ConfigManager {
    private static final Logger LOG = LoggerFactory.getLogger("DeltaVisuals/Config");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int VERSION = 1;

    private static volatile boolean dirty;
    private static volatile boolean loading;
    private static long dirtyAt;
    private static String profile = "default";

    private ConfigManager() {}

    private static Path root() { return FabricLoader.getInstance().getConfigDir().resolve("delta_visuals"); }
    private static Path profilesDir() { return root().resolve("profiles"); }
    private static Path file(String name) { return profilesDir().resolve(name + ".json"); }
    private static Path activeFile() { return root().resolve("active.txt"); }

    public static String profile() { return profile; }

    public static void init() {
        try {
            Files.createDirectories(profilesDir());
            if (Files.exists(activeFile())) {
                String p = sanitize(Files.readString(activeFile(), StandardCharsets.UTF_8).trim());
                if (!p.isEmpty()) profile = p;
            }
        } catch (IOException e) {
            LOG.error("Cannot prepare config directory", e);
        }
        load(profile);
    }

    public static void markDirty() {
        if (loading) return;
        dirty = true;
        dirtyAt = System.currentTimeMillis();
    }

    /** Called every client tick: writes the config ~1 s after the last change. */
    public static void tick() {
        if (dirty && System.currentTimeMillis() - dirtyAt > 1000) save();
    }

    public static synchronized void save() {
        dirty = false;
        try {
            JsonObject root = new JsonObject();
            root.addProperty("version", VERSION);
            JsonObject mods = new JsonObject();
            for (Module m : Modules.all()) mods.add(m.id, m.toJson());
            root.add("modules", mods);

            Files.createDirectories(profilesDir());
            Path target = file(profile);
            Path tmp = target.resolveSibling(profile + ".json.tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(activeFile(), profile, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOG.error("Failed to save config", e);
        }
    }

    /** @return true if the profile was loaded as stored, false if defaults had to be restored. */
    public static synchronized boolean load(String name) {
        Path f = file(name);
        loading = true;
        try {
            resetAllQuiet();
            if (!Files.exists(f)) { loading = false; save(); return true; }
            JsonElement root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) throw new JsonParseException("root is not an object");
            JsonObject o = root.getAsJsonObject();
            if (o.has("version") && o.get("version").getAsInt() > VERSION)
                LOG.warn("Profile {} was written by a newer version; unknown fields are ignored", name);
            JsonObject mods = o.has("modules") && o.get("modules").isJsonObject() ? o.getAsJsonObject("modules") : new JsonObject();
            for (Module m : Modules.all()) {
                JsonElement e = mods.get(m.id);
                if (e != null && e.isJsonObject()) m.fromJson(e.getAsJsonObject());
            }
            return true;
        } catch (Exception ex) {
            LOG.error("Profile '{}' is corrupt, restoring defaults", name, ex);
            try {
                Files.move(f, f.resolveSibling(name + ".corrupt-" + System.currentTimeMillis() + ".json"));
            } catch (Exception ignored) { }
            resetAllQuiet();
            loading = false;
            save();
            return false;
        } finally {
            loading = false;
            dirty = false;
        }
    }


    // ------------------------------------------------------------------ share key (fully local, no server)

    private static final String KEY_PREFIX = "DV1:";

    /** Whole current configuration as one compact string: JSON -> gzip -> Base64url. */
    public static String exportKey() {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        JsonObject mods = new JsonObject();
        for (Module m : Modules.all()) mods.add(m.id, m.toJson());
        root.add("modules", mods);
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
                gz.write(new Gson().toJson(root).getBytes(StandardCharsets.UTF_8));
            }
            return KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            LOG.error("Cannot build share key", e);
            return "";
        }
    }

    /** @return null on success, otherwise a short error message. Nothing is changed if the key is invalid. */
    public static String importKey(String key) {
        if (key == null) return "Clipboard is empty";
        key = key.trim();
        if (!key.startsWith(KEY_PREFIX)) return "Not a Delta Visuals key";
        try {
            byte[] raw = Base64.getUrlDecoder().decode(key.substring(KEY_PREFIX.length()));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                byte[] buf = new byte[8192];
                int r, total = 0;
                while ((r = gz.read(buf)) > 0) {
                    total += r;
                    if (total > 2_000_000) return "Key is too large";
                    out.write(buf, 0, r);
                }
            }
            JsonElement el = JsonParser.parseString(out.toString(StandardCharsets.UTF_8));
            if (!el.isJsonObject() || !el.getAsJsonObject().has("modules")) return "Key has no settings";
            JsonObject mods = el.getAsJsonObject().getAsJsonObject("modules");
            loading = true;
            try {
                resetAllQuiet();
                for (Module m : Modules.all()) {
                    JsonElement e = mods.get(m.id);
                    if (e != null && e.isJsonObject()) m.fromJson(e.getAsJsonObject());
                }
            } finally {
                loading = false;
            }
            save();
            return null;
        } catch (Exception ex) {
            return "Invalid key";
        }
    }

    /** Re-reads the active profile from disk without restarting the game. */
    public static String reload() {
        save();
        return load(profile) ? "Reloaded '" + profile + "'" : "Profile was damaged - defaults restored";
    }

    private static void resetAllQuiet() {
        for (Module m : Modules.all()) m.resetToDefaults();
    }

    /** User-triggered reset of every setting of the active profile. */
    public static void resetAll() {
        resetAllQuiet();
        save();
    }

    public static List<String> profiles() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(profilesDir())) {
            s.map(p -> p.getFileName().toString())
                .filter(n -> n.endsWith(".json") && !n.contains(".corrupt-"))
                .forEach(n -> out.add(n.substring(0, n.length() - 5)));
        } catch (IOException ignored) { }
        if (!out.contains("default")) out.add("default");
        Collections.sort(out);
        return out;
    }

    public static String switchProfile(String name) {
        name = sanitize(name);
        if (name.isEmpty() || name.equals(profile)) return profile;
        save();
        profile = name;
        boolean ok = load(name);
        try { Files.writeString(activeFile(), profile, StandardCharsets.UTF_8); } catch (IOException ignored) { }
        return ok ? "Profile: " + name : "Profile '" + name + "' was damaged - defaults restored";
    }

    public static String nextProfile() {
        List<String> l = profiles();
        int i = l.indexOf(profile);
        return switchProfile(l.get((i + 1) % l.size()));
    }

    /** Creates profile-N; copies the current settings when fromCurrent, otherwise starts from defaults. */
    public static String createProfile(boolean fromCurrent) {
        save();
        List<String> l = profiles();
        int n = 1;
        while (l.contains("profile-" + n)) n++;
        String name = "profile-" + n;
        if (!fromCurrent) {
            profile = name;
            resetAllQuiet();
            save();
            return "Created empty profile: " + name;
        }
        profile = name;
        save();
        return "Duplicated into: " + name;
    }

    public static String deleteProfile() {
        if (profile.equals("default")) return "The default profile cannot be deleted";
        String old = profile;
        try { Files.deleteIfExists(file(old)); } catch (IOException e) { return "Could not delete " + old; }
        profile = "default";
        load("default");
        try { Files.writeString(activeFile(), profile, StandardCharsets.UTF_8); } catch (IOException ignored) { }
        return "Deleted " + old;
    }

    private static String sanitize(String s) {
        return s == null ? "" : s.replaceAll("[^A-Za-z0-9_\\-]", "");
    }
}
