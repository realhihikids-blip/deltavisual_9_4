package dev.deltavisuals.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.deltavisuals.config.ConfigManager;
import dev.deltavisuals.util.ColorUtil;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Base class for every persistent / displayable setting of a module. */
public abstract class Setting {
    public final String name;
    public final String id;
    public boolean hidden;
    public Module owner;
    /** Called only when the USER (or code) changes the value through set(); not on load/reset. */
    public Runnable onChange;
    /** UI animation scratch values (hover etc.). */
    public final float[] anim = new float[4];
    private BooleanSupplier visible = () -> true;

    protected Setting(String name) {
        this.name = name;
        this.id = name.toLowerCase().replace(' ', '_');
    }

    public Setting visibleIf(BooleanSupplier v) { this.visible = v; return this; }
    public boolean isVisible() { return !hidden && visible.getAsBoolean(); }
    public boolean persistent() { return true; }

    public abstract JsonElement save();
    public abstract void load(JsonElement e);
    public abstract void reset();

    protected void changed() {
        ConfigManager.markDirty();
        if (onChange != null) onChange.run();
    }

    // ------------------------------------------------------------------ Bool
    public static final class Bool extends Setting {
        private boolean value;
        private final boolean def;
        public Bool(String name, boolean def) { super(name); this.def = def; this.value = def; }
        public boolean get() { return value; }
        public void set(boolean v) { if (v != value) { value = v; changed(); } }
        public void toggle() { set(!value); }
        @Override public JsonElement save() { return new JsonPrimitive(value); }
        @Override public void load(JsonElement e) {
            try { value = e.getAsBoolean(); } catch (Exception ex) { value = def; }
        }
        @Override public void reset() { value = def; }
    }

    // ------------------------------------------------------------------- Num
    public static final class Num extends Setting {
        public final double min, max, step, def;
        public final String suffix;
        private double value;
        public Num(String name, double min, double max, double step, double def) { this(name, min, max, step, def, ""); }
        public Num(String name, double min, double max, double step, double def, String suffix) {
            super(name);
            this.min = min; this.max = max; this.step = step; this.def = def; this.suffix = suffix;
            this.value = clamp(def);
        }
        private double clamp(double v) {
            if (Double.isNaN(v) || Double.isInfinite(v)) return def;
            if (step > 0) v = Math.round((v - min) / step) * step + min;
            v = Math.round(v * 1_000_000d) / 1_000_000d;
            return Math.max(min, Math.min(max, v));
        }
        public double get() { return value; }
        public float floatValue() { return (float) value; }
        public int intValue() { return (int) Math.round(value); }
        public void set(double v) {
            double n = clamp(v);
            if (n != value) { value = n; changed(); }
        }
        public float fraction() { return max == min ? 0f : (float) ((value - min) / (max - min)); }
        @Override public JsonElement save() { return new JsonPrimitive(value); }
        @Override public void load(JsonElement e) {
            try { value = clamp(e.getAsDouble()); } catch (Exception ex) { value = clamp(def); }
        }
        @Override public void reset() { value = clamp(def); }
    }

    // ------------------------------------------------------------------ Mode
    public static final class Mode extends Setting {
        public final String[] modes;
        private final int def;
        private int index;
        public Mode(String name, String[] modes, int def) {
            super(name);
            this.modes = modes; this.def = def; this.index = def;
        }
        public int index() { return index; }
        public String get() { return modes[index]; }
        public boolean is(String m) { return modes[index].equals(m); }
        public void set(int i) {
            int n = Math.floorMod(i, modes.length);
            if (n != index) { index = n; changed(); }
        }
        public void next() { set(index + 1); }
        public void prev() { set(index - 1); }
        @Override public JsonElement save() { return new JsonPrimitive(modes[index]); }
        @Override public void load(JsonElement e) {
            index = def;
            try {
                String s = e.getAsString();
                for (int i = 0; i < modes.length; i++) if (modes[i].equals(s)) { index = i; return; }
            } catch (Exception ignored) { }
        }
        @Override public void reset() { index = def; }
    }

    // ----------------------------------------------------------------- Color
    public static final class Color extends Setting {
        public final boolean hasAlpha;
        private final int def;
        private int value;
        public Color(String name, int def, boolean hasAlpha) {
            super(name);
            this.hasAlpha = hasAlpha;
            this.def = hasAlpha ? def : ColorUtil.withAlpha(def, 255);
            this.value = this.def;
        }
        public int get() { return value; }
        public int rgb() { return value & 0xFFFFFF; }
        public void set(int argb) {
            int n = hasAlpha ? argb : ColorUtil.withAlpha(argb, 255);
            if (n != value) { value = n; changed(); }
        }
        @Override public JsonElement save() { return new JsonPrimitive(ColorUtil.hex(value, true)); }
        @Override public void load(JsonElement e) {
            try {
                Integer c = ColorUtil.parseHex(e.getAsString(), 255);
                value = c == null ? def : (hasAlpha ? c : ColorUtil.withAlpha(c, 255));
            } catch (Exception ex) { value = def; }
        }
        @Override public void reset() { value = def; }
    }

    // ------------------------------------------------------------------- Str
    /** Persistent single-line text value (e.g. a saved chat command). Edited through actions / client commands. */
    public static final class Str extends Setting {
        public static final int MAX = 256;
        private final String def;
        private String value;
        public Str(String name, String def) { super(name); this.def = def == null ? "" : def; this.value = this.def; }
        public String get() { return value; }
        public void set(String v) {
            String n = clean(v);
            if (!n.equals(value)) { value = n; changed(); }
        }
        private static String clean(String v) {
            if (v == null) return "";
            String n = v.replace('\n', ' ').replace('\r', ' ').strip();
            return n.length() > MAX ? n.substring(0, MAX) : n;
        }
        @Override public JsonElement save() { return new JsonPrimitive(value); }
        @Override public void load(JsonElement e) {
            try { value = clean(e.getAsString()); } catch (Exception ex) { value = def; }
        }
        @Override public void reset() { value = def; }
    }

    // ---------------------------------------------------------------- Action
    public static final class Action extends Setting {
        public final Runnable run;
        public Action(String name, Runnable run) { super(name); this.run = run; }
        @Override public boolean persistent() { return false; }
        @Override public JsonElement save() { return null; }
        @Override public void load(JsonElement e) { }
        @Override public void reset() { }
    }

    // ------------------------------------------------------------------ Info
    public static final class Info extends Setting {
        public final Supplier<String> text;
        public Info(String name, Supplier<String> text) { super(name); this.text = text; }
        @Override public boolean persistent() { return false; }
        @Override public JsonElement save() { return null; }
        @Override public void load(JsonElement e) { }
        @Override public void reset() { }
    }

    // ---------------------------------------------------------------- Header
    /** Non-persistent section title drawn in the settings panel (groups the settings below it). */
    public static final class Header extends Setting {
        public Header(String name) { super(name); }
        @Override public boolean persistent() { return false; }
        @Override public JsonElement save() { return null; }
        @Override public void load(JsonElement e) { }
        @Override public void reset() { }
    }
}
