package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.util.SoundBank;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Plays a sound when YOUR hit really lands. A swing at nothing never makes a sound: the attack event only fires
 * when the crosshair is on an entity, and the sound is played only after the target's health actually dropped
 * (so blocked / cooldown / invulnerable hits stay silent). Purely audio feedback, no gameplay effect.
 */
public final class HitSounds extends Module {
    public static final String[] SOUNDS = {"Minecraft Bell", "Hit Sound 1", "Hit Sound 2", "Hit Sound 3", "Hit Sound 4", "Custom Hit Sound"};
    private static final int CONFIRM_TICKS = 10;

    private static final class Pending { int tick; float hp; }

    private final Setting.Mode sound;
    private final Setting.Num volume, pitch, variation, delay;
    private final Setting.Bool playersOnly;
    private final Map<Integer, Pending> pending = new HashMap<>();
    private SoundBank.Pcm custom;
    private final String[] customError = {null};
    private long lastPlay;
    private int ticks;

    public HitSounds() {
        super("Hit Sounds", "Plays a sound when your hit lands on a player. Silent on misses", Category.COMBAT_VISUALS, false);
        add(new Setting.Header("Hit Sound"));
        sound = add(new Setting.Mode("Sound", SOUNDS, 1));
        volume = add(new Setting.Num("Volume", 0, 100, 1, 70, "%"));
        pitch = add(new Setting.Num("Pitch", 0.5, 2.0, 0.05, 1.0, "x"));
        variation = add(new Setting.Num("Pitch Variation", 0, 30, 1, 0, "%"));
        delay = add(new Setting.Num("Delay Between Sounds", 0, 1000, 10, 120, " ms"));
        playersOnly = add(new Setting.Bool("Players Only", true));
        add(new Setting.Action("Play Sound", this::preview));
        add(new Setting.Header("Custom Hit Sound"));
        add(new Setting.Info("Folder", () -> "config/delta_visuals/sounds"));
        add(new Setting.Info("File", () -> "custom.wav (PCM WAV, up to 5 s)"));
        add(new Setting.Info("Status", this::customStatus));
        add(new Setting.Action("Create Sounds Folder", SoundBank::createSoundsDir));
        add(new Setting.Action("Reload Custom Sound", this::reloadCustom));
        sound.onChange = this::preview;       // picking a sound plays it once, like a preview
    }

    private String customStatus() {
        if (custom != null) return "loaded";
        Path p = SoundBank.customFile();
        if (customError[0] != null) return customError[0];
        return p == null ? "no file yet" : "found, press Reload";
    }

    private void reloadCustom() {
        custom = SoundBank.loadCustom(customError);
        if (custom != null) preview();
    }

    @Override public void onDisable() { pending.clear(); }

    // ------------------------------------------------------------------ events

    /** Called from the AttackEntityCallback in Modules. Remembers the target and its health. */
    public void onAttack(Entity e) {
        if (!isEnabled() || !(e instanceof LivingEntity le)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (le == mc.player || le.isDead() || le.getHealth() <= 0f) return;
        if (playersOnly.get() && !(le instanceof PlayerEntity)) return;
        if (pending.size() > 24) pending.clear();
        Pending p = pending.get(le.getId());
        if (p != null && ticks - p.tick <= CONFIRM_TICKS) return;      // already waiting for this target
        if (p == null) { p = new Pending(); pending.put(le.getId(), p); }
        p.tick = ticks;
        p.hp = le.getHealth() + le.getAbsorptionAmount();
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) { pending.clear(); return; }
        if (mc.isPaused()) return;
        ticks++;
        Iterator<Map.Entry<Integer, Pending>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Pending> en = it.next();
            Pending p = en.getValue();
            Entity e = mc.world.getEntityById(en.getKey());
            if (!(e instanceof LivingEntity le) || ticks - p.tick > CONFIRM_TICKS) { it.remove(); continue; }
            float now = le.getHealth() + le.getAbsorptionAmount();
            if (now < p.hp - 0.01f) {                       // the hit really did damage
                it.remove();
                long t = System.currentTimeMillis();
                if (t - lastPlay >= delay.get()) { lastPlay = t; playNow(); }
            }
        }
    }

    // ------------------------------------------------------------------ playback

    private void preview() { playNow(); }

    private void playNow() {
        float vol = volume.floatValue() / 100f;
        if (vol <= 0f) return;
        float pt = pitch.floatValue();
        float var = variation.floatValue() / 100f;
        if (var > 0f) pt *= 1f + (float) ((Math.random() * 2 - 1) * var);
        String s = sound.get();
        if (s.equals("Minecraft Bell")) {
            // bell generated by code (inharmonic partials); no Minecraft sound API involved, so it compiles on any version
            SoundBank.play(SoundBank.synth(0), vol, pt);
        } else if (s.equals("Custom Hit Sound")) {
            if (custom == null) custom = SoundBank.loadCustom(customError);
            SoundBank.play(custom != null ? custom : SoundBank.synth(1), vol, pt);
        } else {
            SoundBank.play(SoundBank.synth(sound.index()), vol, pt);   // index 1..4 = Hit Sound 1..4
        }
    }
}
