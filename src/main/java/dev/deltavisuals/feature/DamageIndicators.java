package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.hud.TextHud;
import dev.deltavisuals.util.Chroma;
import dev.deltavisuals.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shows the damage you dealt as a short-lived list on the HUD (newest on top, fading out), e.g. "-14.38 HP".
 * Information only - it reads health values the game already sends to the client.
 */
public final class DamageIndicators extends TextHud {
    private record Hit(String text, long time, double phase) { }

    private final Setting.Num decimals, duration, maxEntries, minDamage;
    private final Setting.Bool onlyMyHits, playersOnly;
    private final Setting.Mode colorMode;

    private final ArrayDeque<Hit> hits = new ArrayDeque<>();
    private final Map<Integer, Float> lastHealth = new HashMap<>();
    private final Map<Integer, Long> myHits = new HashMap<>();
    private long ticks;

    public DamageIndicators() {
        super("Damage Indicators", "Floating list of the damage you deal (-14.38 HP)", Category.VISUALS, false, 0.56, 0.42);
        decimals = add(new Setting.Num("Decimals", 0, 2, 1, 2));
        duration = add(new Setting.Num("Duration", 0.5, 5, 0.1, 1.6, " s"));
        maxEntries = add(new Setting.Num("Max Entries", 1, 8, 1, 4));
        minDamage = add(new Setting.Num("Min Damage", 0.05, 2, 0.05, 0.05));
        colorMode = add(new Setting.Mode("Color Mode", new String[]{"Static", "Rainbow"}, 0));
        onlyMyHits = add(new Setting.Bool("Only My Hits", true));
        playersOnly = add(new Setting.Bool("Players Only", true));
    }

    public void onAttack(Entity e) {
        if (isEnabled()) myHits.put(e.getId(), System.currentTimeMillis());
    }

    @Override
    public void onDisable() { hits.clear(); lastHealth.clear(); myHits.clear(); }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) return;
        ticks++;
        long now = System.currentTimeMillis();
        Set<Integer> seen = new HashSet<>();

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || le == mc.player) continue;
            if (playersOnly.get() && !(le instanceof PlayerEntity)) continue;
            int id = le.getId();
            seen.add(id);
            float hp = le.getHealth() + le.getAbsorptionAmount();
            Float prev = lastHealth.put(id, hp);
            if (prev == null) continue;
            float diff = prev - hp;
            if (diff < minDamage.floatValue()) continue;
            if (onlyMyHits.get() && now - myHits.getOrDefault(id, 0L) > 800) continue;
            hits.addFirst(new Hit(String.format(Locale.ROOT, "-%." + decimals.intValue() + "f HP", diff), now, Math.random()));
        }
        lastHealth.keySet().retainAll(seen);
        myHits.values().removeIf(t -> now - t > 5000);
        while (hits.size() > maxEntries.intValue()) hits.pollLast();
    }

    @Override
    protected void collect(MinecraftClient mc, List<Line> out, boolean editing) {
        long now = System.currentTimeMillis();
        long life = (long) (duration.get() * 1000);
        hits.removeIf(h -> now - h.time() > life);
        for (Hit h : hits) {
            float age = (now - h.time()) / (float) life;
            float alpha = age < 0.6f ? 1f : 1f - (age - 0.6f) / 0.4f;
            int base = colorMode.is("Rainbow")
                    ? 0xFF000000 | Chroma.pick("Rainbow", 0, 0, 1.0, h.phase(), ticks)
                    : color.get();
            out.add(new Line(h.text(), ColorUtil.mulAlpha(base, alpha)));
        }
        if (out.isEmpty() && editing) out.add(new Line("-14.38 HP", color.get()));
    }
}
