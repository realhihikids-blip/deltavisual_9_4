package dev.deltavisuals.feature;

import dev.deltavisuals.config.ConfigManager;
import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/** Profile management. Settings are saved automatically; profiles are separate JSON files. */
public final class ProfilesModule extends Module {
    private long armedDelete, armedReset;
    private String status = "";

    public ProfilesModule() {
        super("Profiles", "Switch, duplicate and reset configuration profiles", Category.CONFIGS, true);
        alwaysOn = true;
        add(new Setting.Info("Active Profile", ConfigManager::profile));
        add(new Setting.Info("Status", () -> status.isEmpty() ? "Autosave on" : status));
        add(new Setting.Action("Next Profile", () -> say(ConfigManager.nextProfile())));
        add(new Setting.Action("Duplicate Current", () -> say(ConfigManager.createProfile(true))));
        add(new Setting.Action("New Empty Profile", () -> say(ConfigManager.createProfile(false))));
        add(new Setting.Action("Copy Share Key", () -> {
            String k = ConfigManager.exportKey();
            if (k.isEmpty()) { say("Could not build key"); return; }
            MinecraftClient.getInstance().keyboard.setClipboard(k);
            say("Share key copied (" + k.length() + " chars)");
        }));
        add(new Setting.Action("Load Key From Clipboard", () -> {
            String err = ConfigManager.importKey(MinecraftClient.getInstance().keyboard.getClipboard());
            say(err == null ? "Settings loaded from key" : err);
        }));
        add(new Setting.Action("Reload Config From Disk", () -> say(ConfigManager.reload())));
        add(new Setting.Action("Save Now", () -> { ConfigManager.save(); say("Saved"); }));
        add(new Setting.Action("Delete Profile (press twice)", () -> {
            long now = System.currentTimeMillis();
            if (now - armedDelete < 3000) { armedDelete = 0; say(ConfigManager.deleteProfile()); }
            else { armedDelete = now; say("Press again within 3 s to delete '" + ConfigManager.profile() + "'"); }
        }));
        add(new Setting.Action("Reset ALL Settings (press twice)", () -> {
            long now = System.currentTimeMillis();
            if (now - armedReset < 3000) { armedReset = 0; ConfigManager.resetAll(); say("All settings reset"); }
            else { armedReset = now; say("Press again within 3 s to reset everything"); }
        }));
    }

    private void say(String s) {
        status = s;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("[Delta] " + s), true);
    }
}
