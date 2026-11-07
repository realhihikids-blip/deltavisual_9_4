package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Modules;
import dev.deltavisuals.core.Setting;

/**
 * Calmer dropped items: slows down the vanilla spin and bobbing of item entities (or freezes the bobbing).
 * Visual only. The hook is EntityRendererMotionMixin, which scales the animation clock of the render state.
 */
public final class ItemMotion extends Module {
    private final Setting.Num speed;

    public ItemMotion() {
        super("Item Motion", "Dropped items spin and bob more slowly and smoothly", Category.WORLD, false);
        speed = add(new Setting.Num("Speed", 0, 2, 0.05, 0.35, "x"));
    }

    public static float scale(float age) {
        ItemMotion m = Modules.ITEM_MOTION;
        if (m == null || !m.isEnabled()) return age;
        return age * m.speed.floatValue();
    }
}
