package dev.deltavisuals.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.deltavisuals.util.KeyNames;

/** Immutable key / mouse-button binding. */
public final class Bind {
    public enum Type { NONE, KEY, MOUSE }

    public static final Bind NONE = new Bind(Type.NONE, 0);

    public final Type type;
    public final int code;

    private Bind(Type type, int code) { this.type = type; this.code = code; }

    public static Bind key(int code) { return new Bind(Type.KEY, code); }
    public static Bind mouse(int button) { return new Bind(Type.MOUSE, button); }

    public boolean isSet() { return type != Type.NONE; }

    public String label() {
        switch (type) {
            case KEY: return KeyNames.key(code);
            case MOUSE: return KeyNames.mouse(code);
            default: return "None";
        }
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("type", type.name());
        o.addProperty("code", code);
        return o;
    }

    public static Bind fromJson(JsonElement e, Bind fallback) {
        try {
            if (e == null || !e.isJsonObject()) return fallback;
            JsonObject o = e.getAsJsonObject();
            Type t = Type.valueOf(o.get("type").getAsString());
            int c = o.get("code").getAsInt();
            if (t == Type.NONE) return NONE;
            if (c < 0 || c > 400) return fallback;
            return t == Type.KEY ? key(c) : mouse(c);
        } catch (Exception ex) {
            return fallback;
        }
    }

    @Override public boolean equals(Object o) {
        return o instanceof Bind b && b.type == type && b.code == code;
    }
    @Override public int hashCode() { return type.ordinal() * 1000 + code; }
}
