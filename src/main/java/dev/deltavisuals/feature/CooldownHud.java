package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.gui.UiFont;
import dev.deltavisuals.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Item cooldowns (ender pearl, golden apple, shield, totem, chorus fruit) next to the crosshair.
 * Shows only what the client already knows: the vanilla item cooldown that the game itself draws on the hotbar.
 *
 * The game exposes only the remaining fraction (1 -> 0), not the length, so the length is learned from how fast the
 * fraction drops (a vanilla default is used until then). Only items that are cooling down are listed.
 */
public final class CooldownHud extends HudModule {
    private static final int ROW = 18, BAR_W = 30;

    private static final class Entry {
        final String label;
        final Item item, alt;
        final float defSecs;
        final Setting.Bool on;
        ItemStack stack, altStack;
        float p0 = -1f, last = 0f, total = -1f;
        long t0;
        Entry(String label, Item item, Item alt, float defSecs, Setting.Bool on) {
            this.label = label; this.item = item; this.alt = alt; this.defSecs = defSecs; this.on = on;
        }
    }

    private record Shown(ItemStack stack, float p, float secs) { }

    private final List<Entry> entries = new ArrayList<>();
    private final List<Shown> shown = new ArrayList<>();
    private final Setting.Bool showTime;
    private int w, h, timeW;

    public CooldownHud() {
        // default position: just under the crosshair
        super("Cooldowns", "Item cooldowns (pearl, golden apple, shield, totem...) next to the crosshair", Category.HUD, true, 0.5, 0.56);
        entries.add(new Entry("Ender Pearl", Items.ENDER_PEARL, null, 1.0f, add(new Setting.Bool("Ender Pearl", true))));
        entries.add(new Entry("Golden Apple", Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, -1f, add(new Setting.Bool("Golden Apple", true))));
        entries.add(new Entry("Shield", Items.SHIELD, null, 5.0f, add(new Setting.Bool("Shield", true))));
        entries.add(new Entry("Totem", Items.TOTEM_OF_UNDYING, null, -1f, add(new Setting.Bool("Totem", true))));
        entries.add(new Entry("Chorus Fruit", Items.CHORUS_FRUIT, null, -1f, add(new Setting.Bool("Chorus Fruit", false))));
        showTime = add(new Setting.Bool("Show Seconds", true));
    }

    @Override
    protected boolean prepare(MinecraftClient mc, boolean editing) {
        shown.clear();
        ClientPlayerEntity p = mc.player;
        if (p == null) return false;
        long now = System.currentTimeMillis();
        for (Entry e : entries) {
            if (e.stack == null) {
                e.stack = new ItemStack(e.item);
                if (e.alt != null) e.altStack = new ItemStack(e.alt);
            }
            if (!e.on.get()) { e.p0 = -1f; continue; }
            float pr = progress(p, e.stack);
            ItemStack shownStack = e.stack;
            if (pr <= 0f && e.altStack != null) {
                pr = progress(p, e.altStack);
                if (pr > 0f) shownStack = e.altStack;
            }
            if (pr <= 0f) { e.p0 = -1f; e.total = -1f; e.last = 0f; continue; }
            // a fresh cooldown (or one that was restarted)
            if (e.p0 < 0f || pr > e.last + 0.02f) {
                e.p0 = pr;
                e.t0 = now;
                e.total = e.defSecs;
            } else if (e.p0 - pr > 0.04f) {
                e.total = (now - e.t0) / 1000f / (e.p0 - pr); // learn the real length from the speed of the drop
            }
            e.last = pr;
            float secs = e.total > 0f ? pr * e.total : -1f;
            shown.add(new Shown(shownStack, pr, secs));
        }
        if (shown.isEmpty()) {
            if (!editing) return false;
            shown.add(new Shown(entries.get(0).stack, 0.6f, 0.6f));
            shown.add(new Shown(entries.get(2).stack, 0.35f, 1.8f));
        }
        timeW = mc.textRenderer.getWidth("00.0s");
        w = 16 + 3 + BAR_W + (showTime.get() ? 3 + timeW : 0);
        h = shown.size() * ROW - 2;
        return true;
    }

    private static float progress(ClientPlayerEntity p, ItemStack s) {
        try {
            return p.getItemCooldownManager().getCooldownProgress(s, 0f);
        } catch (Throwable t) {
            return 0f;
        }
    }

    @Override protected int contentW() { return w; }
    @Override protected int contentH() { return h; }

    @Override
    protected void draw(DrawContext g, MinecraftClient mc, int x, int y) {
        int yy = y;
        for (Shown s : shown) {
            g.drawItem(s.stack, x, yy);
            int bx = x + 19;
            g.fill(bx, yy + 6, bx + BAR_W, yy + 9, 0x66000000);
            int fw = Math.round(BAR_W * (1f - s.p)); // fills up while the item is getting ready
            int col = s.p > 0.66f ? 0xFFF87171 : s.p > 0.33f ? 0xFFFBBF24 : 0xFF4ADE80;
            if (fw > 0) g.fill(bx, yy + 6, bx + fw, yy + 9, col);
            if (showTime.get()) {
                String t = s.secs >= 0f ? String.format(Locale.ROOT, "%.1fs", s.secs) : Math.round(s.p * 100f) + "%";
                g.drawText(mc.textRenderer, UiFont.text(t), bx + BAR_W + 3, yy + 4, color.get(), true);
            }
            yy += ROW;
        }
    }
}
