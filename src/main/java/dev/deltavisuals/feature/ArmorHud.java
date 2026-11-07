package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/** Equipped armor with durability. Uses only the player's own, visible equipment. */
public final class ArmorHud extends HudModule {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private final Setting.Mode layout;
    private final Setting.Bool percent, mainHand;
    private final ItemStack[] stacks = new ItemStack[5];
    private int n, w, h;
    private boolean placeholder;

    public ArmorHud() {
        super("Armor", "Your armor and held item with durability", Category.HUD, true, 0.995, 0.8);
        layout = add(new Setting.Mode("Layout", new String[]{"Vertical", "Horizontal"}, 0));
        percent = add(new Setting.Bool("Durability Percent", true));
        mainHand = add(new Setting.Bool("Show Held Item", false));
    }

    @Override
    protected boolean prepare(MinecraftClient mc, boolean editing) {
        if (mc.player == null) return false;
        n = 0;
        for (EquipmentSlot s : SLOTS) {
            ItemStack st = mc.player.getEquippedStack(s);
            if (!st.isEmpty()) stacks[n++] = st;
        }
        if (mainHand.get() && !mc.player.getMainHandStack().isEmpty()) stacks[n++] = mc.player.getMainHandStack();
        placeholder = n == 0;
        if (placeholder && !editing) return false;
        boolean vertical = layout.index() == 0;
        int tw = percent.get() ? mc.textRenderer.getWidth("100%") + 3 : 0;
        if (placeholder) { w = 50; h = 12; return true; }
        w = vertical ? 16 + tw : n * (16 + tw + 2) - 2;
        h = vertical ? n * 18 - 2 : 16;
        return true;
    }

    @Override protected int contentW() { return w; }
    @Override protected int contentH() { return h; }

    @Override
    protected void draw(DrawContext g, MinecraftClient mc, int x, int y) {
        if (placeholder) {
            g.drawText(mc.textRenderer, Text.literal("Armor"), x, y + 2, color.get(), true);
            return;
        }
        boolean vertical = layout.index() == 0;
        int tw = percent.get() ? mc.textRenderer.getWidth("100%") + 3 : 0;
        for (int i = 0; i < n; i++) {
            int ix = vertical ? x : x + i * (16 + tw + 2);
            int iy = vertical ? y + i * 18 : y;
            g.drawItem(stacks[i], ix, iy);
            if (percent.get() && stacks[i].isDamageable()) {
                int left = stacks[i].getMaxDamage() - stacks[i].getDamage();
                int pct = Math.round(100f * left / stacks[i].getMaxDamage());
                int col = pct > 60 ? 0xFF4ADE80 : pct > 25 ? 0xFFFBBF24 : 0xFFF87171;
                g.drawText(mc.textRenderer, Text.literal(pct + "%"), ix + 18, iy + 4, col, true);
            }
        }
    }
}
