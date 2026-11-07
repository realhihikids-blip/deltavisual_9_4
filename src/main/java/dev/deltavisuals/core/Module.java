package dev.deltavisuals.core;

import com.google.gson.JsonObject;
import dev.deltavisuals.config.ConfigManager;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.List;

public class Module {
    public final String name;
    public final String description;
    public final Category category;
    public final String id;
    public final boolean defaultEnabled;
    /** Always enabled, no on/off switch (GUI, theme, profiles...). */
    public boolean alwaysOn;
    /** If true the bind calls onBind() (only while enabled); otherwise the bind toggles the module. */
    public boolean action;
    /** Whether the bind may fire while the player's own inventory screen is open. */
    public boolean allowInInventory;
    /** Call onTick() even while disabled . */
    public boolean tickWhenDisabled;
    /** Consecutive crashes in onTick / onWorldRender; the module is switched off only after a few in a row. */
    public int crashes;
    public Bind bind = Bind.NONE;
    public Bind defaultBind = Bind.NONE;
    public final List<Setting> settings = new ArrayList<>();
    public final float[] anim = new float[8];
    private boolean enabled;
    private long lastTrigger;

    public Module(String name, String description, Category category, boolean defaultEnabled) {
        this.name = name;
        this.description = description;
        this.category = category;
        this.id = name.toLowerCase().replace(' ', '_');
        this.defaultEnabled = defaultEnabled;
        this.enabled = defaultEnabled;
    }

    protected <T extends Setting> T add(T s) { s.owner = this; settings.add(s); return s; }

    public Module defaultBind(Bind b) { this.defaultBind = b; this.bind = b; return this; }

    public boolean isEnabled() { return alwaysOn || enabled; }

    public void setEnabled(boolean v) {
        if (alwaysOn || v == enabled) return;
        enabled = v;
        if (v) onEnable(); else onDisable();
        ConfigManager.markDirty();
    }
    public void toggle() { setEnabled(!enabled); }

    public void onEnable() { }
    public void onDisable() { }
    public void onTick(MinecraftClient mc) { }
    public void onBind(MinecraftClient mc) { }
    /** Called every frame inside the world render pass while enabled. Draw only through {@link dev.deltavisuals.render.Gfx}. */
    public void onWorldRender() { }

    /** Called by BindManager on a fresh press. Debounced against accidental repeats. */
    public void triggerBind(MinecraftClient mc) {
        long now = System.currentTimeMillis();
        if (now - lastTrigger < 120) return;
        lastTrigger = now;
        if (action) { if (isEnabled()) onBind(mc); }
        else toggle();
    }

    public void setBind(Bind b) { this.bind = b; ConfigManager.markDirty(); }

    /** Silent reset: no callbacks, caller decides about saving. */
    public void resetToDefaults() {
        enabled = defaultEnabled;
        bind = defaultBind;
        for (Setting s : settings) s.reset();
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        if (!alwaysOn) o.addProperty("enabled", enabled);
        o.add("bind", bind.toJson());
        JsonObject st = new JsonObject();
        for (Setting s : settings) {
            if (!s.persistent()) continue;
            var e = s.save();
            if (e != null) st.add(s.id, e);
        }
        o.add("settings", st);
        return o;
    }

    public void fromJson(JsonObject o) {
        try {
            if (!alwaysOn && o.has("enabled")) {
                try { enabled = o.get("enabled").getAsBoolean(); } catch (Exception ignored) { }
            }
            if (o.has("bind")) bind = Bind.fromJson(o.get("bind"), defaultBind);
            if (o.has("settings") && o.get("settings").isJsonObject()) {
                JsonObject st = o.getAsJsonObject("settings");
                for (Setting s : settings) {
                    if (s.persistent() && st.has(s.id)) s.load(st.get(s.id));
                }
            }
        } catch (Exception ignored) { /* a broken module block keeps defaults for the rest */ }
    }
}
