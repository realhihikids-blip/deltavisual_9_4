package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.gui.UiFont;
import dev.deltavisuals.hud.HudModule;
import dev.deltavisuals.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Your own active potion effects: name, level, time left and a thin duration bar.
 * Reads only the effects the client already knows about (the same list the vanilla inventory shows).
 */
public final class PotionHud extends HudModule {
    private record Row(String name, String time, int color, float frac) { }

    private static final int ROW = 13;
    private static final int STRIPE = 6;

    private final List<Row> rows = new ArrayList<>();
    /** Longest duration seen per effect, used to draw the bar (the game does not tell the original length). */
    private final Map<String, Integer> maxSeen = new HashMap<>();
    private final Setting.Bool bars, harmful, typeColor;
    private int w, h;

    public PotionHud() {
        super("Potion Effects", "Your active potion effects with level and time left", Category.HUD, true, 0.005, 0.3);
        bars = add(new Setting.Bool("Duration Bars", true));
        harmful = add(new Setting.Bool("Show Harmful Effects", true));
        typeColor = add(new Setting.Bool("Color By Effect", true));
    }

    @Override
    protected boolean prepare(MinecraftClient mc, boolean editing) {
        rows.clear();
        if (mc.player == null) return false;
        Set<String> alive = new HashSet<>();
        for (StatusEffectInstance inst : new ArrayList<>(mc.player.getStatusEffects())) {
            if (!inst.shouldShowIcon()) continue; // hidden effects stay hidden, like in the vanilla inventory
            StatusEffect type = inst.getEffectType().value();
            if (!harmful.get() && !type.isBeneficial()) continue;
            String name = type.getName().getString();
            if (inst.getAmplifier() > 0) name += " " + roman(inst.getAmplifier() + 1);
            alive.add(name);
            boolean inf = inst.isInfinite();
            int dur = inst.getDuration();
            float frac = 1f;
            if (!inf) {
                int max = maxSeen.merge(name, dur, Math::max);
                frac = max <= 0 ? 0f : Math.min(1f, dur / (float) max);
            }
            int col = typeColor.get() ? ColorUtil.withAlpha(type.getColor(), 255)
                : (type.isBeneficial() ? 0xFF4ADE80 : 0xFFF87171);
            rows.add(new Row(name, inf ? "--" : time(dur), col, frac));
        }
        maxSeen.keySet().retainAll(alive);
        if (rows.isEmpty()) {
            if (!editing) return false;
            rows.add(new Row("Speed II", "1:30", 0xFF7CAFC6, 0.7f));
            rows.add(new Row("Strength", "0:42", 0xFF932423, 0.3f));
        }
        int mw = 70;
        for (Row r : rows) {
            int rw = mc.textRenderer.getWidth(UiFont.text(r.name)) + 10 + mc.textRenderer.getWidth(UiFont.text(r.time));
            mw = Math.max(mw, rw);
        }
        w = STRIPE + mw;
        h = rows.size() * ROW - 2;
        return true;
    }

    @Override protected int contentW() { return w; }
    @Override protected int contentH() { return h; }

    @Override
    protected void draw(DrawContext g, MinecraftClient mc, int x, int y) {
        int yy = y;
        for (Row r : rows) {
            g.fill(x, yy + 1, x + 2, yy + 10, r.color);
            g.drawText(mc.textRenderer, UiFont.text(r.name), x + STRIPE, yy + 1, color.get(), true);
            int tw = mc.textRenderer.getWidth(UiFont.text(r.time));
            g.drawText(mc.textRenderer, UiFont.text(r.time), x + w - tw, yy + 1, color.get(), true);
            if (bars.get()) {
                int bw = w - STRIPE;
                g.fill(x + STRIPE, yy + 10, x + STRIPE + bw, yy + 11, 0x40FFFFFF);
                int fw = Math.round(bw * r.frac);
                if (fw > 0) g.fill(x + STRIPE, yy + 10, x + STRIPE + fw, yy + 11, r.color);
            }
            yy += ROW;
        }
    }

    private static String time(int ticks) {
        int s = Math.max(0, ticks) / 20;
        if (s >= 3600) return (s / 3600) + ":" + two((s / 60) % 60) + ":" + two(s % 60);
        return (s / 60) + ":" + two(s % 60);
    }

    private static String two(int v) { return v < 10 ? "0" + v : String.valueOf(v); }

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 1 && n < r.length ? r[n] : String.valueOf(n);
    }
}
