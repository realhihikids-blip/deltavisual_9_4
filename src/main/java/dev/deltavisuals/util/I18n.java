package dev.deltavisuals.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.deltavisuals.core.Modules;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Translations of the mod interface. The English text of a label is its own key, so a label without a translation
 * simply stays in English. Files: assets/delta_visuals/lang/en_us.json and ru_ru.json (read straight from the jar).
 * The language is chosen in Theme -> Language and applies immediately, without a restart.
 */
public final class I18n {
    private static final Map<String, String> EN = load("en_us"), RU = load("ru_ru");

    private I18n() {}

    public static boolean russian() {
        return Modules.THEME != null && Modules.THEME.language.index() == 1;
    }

    /** Translated text for the current language, or the text itself when there is no translation. */
    public static String t(String s) {
        if (s == null || s.isEmpty()) return s;
        String v = (russian() ? RU : EN).get(s);
        return v != null ? v : s;
    }

    private static Map<String, String> load(String name) {
        Map<String, String> map = new HashMap<>();
        try (InputStream in = I18n.class.getResourceAsStream("/assets/delta_visuals/lang/" + name + ".json")) {
            if (in == null) return map;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) map.put(e.getKey(), e.getValue().getAsString());
        } catch (Exception ignored) { }
        return map;
    }
}
