package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.gui.UiFont;
import dev.deltavisuals.hud.HudModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Small counter: how many Totems of Undying you still carry (inventory + off hand). Reads only your own inventory. */
public final class TotemCount extends HudModule {
    private final Setting.Bool hideZero, colorByCount;
    private ItemStack icon;
    private String text = "x0";
    private int count, w;

    public TotemCount() {
        super("Totem Count", "How many totems you have left (inventory and off hand)", Category.HUD, true, 0.005, 0.7);
        colorByCount = add(new Setting.Bool("Color By Amount", true));
        hideZero = add(new Setting.Bool("Hide When Zero", false));
    }

    @Override
    protected boolean prepare(MinecraftClient mc, boolean editing) {
        PlayerEntity p = mc.player;
        if (p == null) return false;
        if (icon == null) icon = new ItemStack(Items.TOTEM_OF_UNDYING);
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Items.TOTEM_OF_UNDYING)) n += s.getCount();
        }
        ItemStack off = p.getOffHandStack();
        if (off.isOf(Items.TOTEM_OF_UNDYING)) n += off.getCount();
        count = n;
        if (n == 0 && hideZero.get() && !editing) return false;
        text = "x" + n;
        w = 16 + 3 + Math.max(mc.textRenderer.getWidth("x00"), mc.textRenderer.getWidth(text));
        return true;
    }

    @Override protected int contentW() { return w; }
    @Override protected int contentH() { return 16; }

    @Override
    protected void draw(DrawContext g, MinecraftClient mc, int x, int y) {
        g.drawItem(icon, x, y);
        int col = !colorByCount.get() ? color.get() : count == 0 ? 0xFFF87171 : count == 1 ? 0xFFFBBF24 : 0xFF4ADE80;
        g.drawText(mc.textRenderer, UiFont.text(text), x + 19, y + 4, col, true);
    }
}
