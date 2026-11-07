package dev.deltavisuals.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.deltavisuals.config.ConfigManager;
import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Modules;
import dev.deltavisuals.core.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.RotationAxis;

/**
 * Moves, rotates and scales the item in your first-person hands. Purely visual: the hit box, reach, damage and
 * the swing itself are untouched. Settings are stored per hand and per item type (sword, axe, pickaxe, shovel,
 * hoe, food, shield, other); "All Items" is the fallback for types you did not set up.
 *
 * The transform is applied around the resting position of the item by HeldItemRendererMixin.
 */
public final class ItemPosition extends Module {
    public static final String[] HANDS = {"Right Hand", "Left Hand"};
    public static final String[] CATS = {"All Items", "Sword", "Axe", "Pickaxe", "Shovel", "Hoe", "Food", "Shield", "Other Items"};
    private static final int NV = 7;                                  // x y z rotX rotY rotZ scale
    private static final int CUSTOM = 24;

    /** name, then x, y, z, rotX, rotY, rotZ, scale. X/Y/Z in blocks relative to the vanilla pose, rotations in degrees. */
    private static final Object[][] PRESETS = {
        {"Default",         0f,     0f,     0f,    0f,   0f,   0f,  1f},
        {"Classic",         0.04f, -0.05f,  0.04f, 0f,  -6f,   4f,  0.95f},
        {"Low Sword",       0f,    -0.34f,  0.06f, 8f,   0f,   0f,  1f},
        {"High Sword",     -0.04f,  0.26f, -0.10f, -12f, 0f,   0f,  1f},
        {"Side Sword",      0.34f, -0.04f,  0f,    0f,  -38f, 10f,  1f},
        {"Diagonal Sword",  0.06f,  0.02f, -0.04f, 0f,   0f, -38f,  1f},
        {"Reverse Sword",  -0.05f,  0f,     0f,    0f,  110f, 25f,  1f},
        {"Small Sword",     0.10f,  0.10f,  0.10f, 0f,   0f,   0f,  0.55f},
        {"Big Sword",      -0.10f, -0.06f, -0.22f, 0f,   0f,   0f,  1.5f},
        {"Compact",        -0.16f,  0.06f,  0.18f, 6f,  10f,   0f,  0.72f},
        {"PvP Classic",     0.08f, -0.12f, -0.02f, 10f, -14f,  6f,  0.9f},
        {"PvP Low",         0.02f, -0.42f,  0.04f, 22f, -8f,   4f,  0.88f},
        {"PvP High",       -0.06f,  0.30f, -0.12f, -22f, 6f,  -6f,  0.9f},
        {"PvP Side",        0.42f, -0.10f,  0f,    4f,  -52f, 16f,  0.9f},
        {"PvP Diagonal",    0.14f, -0.04f, -0.06f, 0f,  -12f, -48f, 0.9f},
        {"Tilted Blade",    0f,     0f,     0f,   -38f,  0f,   0f,  1f},
        {"Flat Blade",      0.02f, -0.10f,  0f,    70f,  0f,   0f,  1f},
        {"Close Guard",    -0.36f, -0.04f,  0.26f, 10f,  24f,  6f,  0.95f},
        {"Forward Pose",    0f,    -0.02f, -0.34f, -4f,  -4f,  0f,  1.05f},
        {"Overhead",        0f,     0.34f, -0.06f, -52f, 0f,   0f,  1f},
        {"Samurai",         0.22f, -0.14f,  0f,    0f,  -18f, -62f, 1.05f},
        {"Showcase",       -0.06f,  0.12f, -0.36f, 0f,   28f, -8f,  1.2f},
        {"Low Guard",       0.04f, -0.36f,  0.10f, 30f,  0f,  -14f, 0.95f},
        {"Dagger Stab",     0.12f, -0.12f, -0.18f, 80f,  0f,   0f,  0.8f},
        {"Custom",          0f,     0f,     0f,    0f,   0f,   0f,  1f},
    };
    private static final String[] PRESET_NAMES = new String[PRESETS.length];
    static { for (int i = 0; i < PRESETS.length; i++) PRESET_NAMES[i] = (String) PRESETS[i][0]; }

    // ------------------------------------------------------------------ storage of all slots (hidden setting)
    private static final class Store extends Setting {
        final float[][] v = new float[HANDS.length * CATS.length][NV];
        final boolean[] active = new boolean[HANDS.length * CATS.length];
        final int[] preset = new int[HANDS.length * CATS.length];
        Runnable afterLoad;

        Store() { super("Slots"); hidden = true; reset(); }

        @Override public JsonElement save() {
            JsonObject o = new JsonObject();
            JsonArray vals = new JsonArray(), act = new JsonArray(), pre = new JsonArray();
            for (int s = 0; s < v.length; s++) {
                JsonArray row = new JsonArray();
                for (float f : v[s]) row.add(new JsonPrimitive(f));
                vals.add(row); act.add(new JsonPrimitive(active[s])); pre.add(new JsonPrimitive(preset[s]));
            }
            o.add("v", vals); o.add("a", act); o.add("p", pre);
            return o;
        }

        @Override public void load(JsonElement e) {
            reset();
            try {
                JsonObject o = e.getAsJsonObject();
                JsonArray vals = o.getAsJsonArray("v"), act = o.getAsJsonArray("a"), pre = o.getAsJsonArray("p");
                for (int s = 0; s < v.length && s < vals.size(); s++) {
                    JsonArray row = vals.get(s).getAsJsonArray();
                    for (int i = 0; i < NV && i < row.size(); i++) v[s][i] = clampValue(i, row.get(i).getAsFloat());
                    active[s] = s < act.size() && act.get(s).getAsBoolean();
                    preset[s] = s < pre.size() ? Math.max(0, Math.min(PRESETS.length - 1, pre.get(s).getAsInt())) : CUSTOM;
                }
            } catch (Exception ex) { reset(); }
            if (afterLoad != null) afterLoad.run();
        }

        @Override public void reset() {
            for (int s = 0; s < v.length; s++) { setDefault(s); }
        }

        void setDefault(int s) {
            v[s][0] = 0; v[s][1] = 0; v[s][2] = 0; v[s][3] = 0; v[s][4] = 0; v[s][5] = 0; v[s][6] = 1f;
            active[s] = false; preset[s] = 0;
        }
    }

    // Safe limits: with these values the item always stays inside the visible area of the screen.
    public static final float POS_X = 0.7f, POS_Y_MIN = -0.6f, POS_Y_MAX = 0.6f, POS_Z_MIN = -0.8f, POS_Z_MAX = 0.5f;
    public static final float ROT_X = 90f, ROT_Y = 110f, ROT_Z = 90f, SCALE_MIN = 0.3f, SCALE_MAX = 1.6f;
    /** Largest allowed sideways + vertical offset (blocks), also applied to the combination of X and Y. */
    private static final float MAX_OFFSET_XY = 0.85f;

    private static float clampValue(int i, float f) {
        if (Float.isNaN(f)) return i == 6 ? 1f : 0f;
        switch (i) {
            case 0: return Math.max(-POS_X, Math.min(POS_X, f));
            case 1: return Math.max(POS_Y_MIN, Math.min(POS_Y_MAX, f));
            case 2: return Math.max(POS_Z_MIN, Math.min(POS_Z_MAX, f));
            case 3: return Math.max(-ROT_X, Math.min(ROT_X, f));
            case 4: return Math.max(-ROT_Y, Math.min(ROT_Y, f));
            case 5: return Math.max(-ROT_Z, Math.min(ROT_Z, f));
            default: return Math.max(SCALE_MIN, Math.min(SCALE_MAX, f));
        }
    }

    // ------------------------------------------------------------------ module
    private final Setting.Mode hand, category, preset;
    private final Setting.Num px, py, pz, rx, ry, rz, scale;
    private final Store store;
    private boolean syncing;

    public ItemPosition() {
        super("Item Position", "Move, rotate and scale the item in your hands. Separate for each hand and item type", Category.VISUALS, false);
        add(new Setting.Header("Hand and Item"));
        hand = add(new Setting.Mode("Hand", HANDS, 0));
        category = add(new Setting.Mode("Item Type", CATS, 0));
        add(new Setting.Info("Editing", () -> HANDS[hand.index()] + " / " + CATS[category.index()]));

        add(new Setting.Header("Position Presets"));
        preset = add(new Setting.Mode("Preset", PRESET_NAMES, 0));

        add(new Setting.Header("Position"));
        px = add(new Setting.Num("Position X", -POS_X, POS_X, 0.01, 0));
        py = add(new Setting.Num("Position Y", POS_Y_MIN, POS_Y_MAX, 0.01, 0));
        pz = add(new Setting.Num("Position Z", POS_Z_MIN, POS_Z_MAX, 0.01, 0));
        add(new Setting.Header("Rotation"));
        rx = add(new Setting.Num("Rotation X", -ROT_X, ROT_X, 1, 0, " deg"));
        ry = add(new Setting.Num("Rotation Y", -ROT_Y, ROT_Y, 1, 0, " deg"));
        rz = add(new Setting.Num("Rotation Z", -ROT_Z, ROT_Z, 1, 0, " deg"));
        add(new Setting.Header("Size"));
        scale = add(new Setting.Num("Item Scale", SCALE_MIN, SCALE_MAX, 0.01, 1.0, "x"));

        add(new Setting.Header("Actions"));
        add(new Setting.Action("Preview (closes menu)", () -> MinecraftClient.getInstance().setScreen(null)));
        add(new Setting.Action("Reset Position", this::resetCurrent));
        add(new Setting.Action("Reset All", this::resetAll));
        add(new Setting.Info("Tip", () -> "All Items = fallback for types you did not set"));
        store = add(new Store());
        store.afterLoad = this::sync;

        hand.onChange = this::sync;
        category.onChange = this::sync;
        preset.onChange = this::applyPreset;
        Runnable edit = this::onSlider;
        for (Setting.Num n : new Setting.Num[]{px, py, pz, rx, ry, rz, scale}) n.onChange = edit;
    }

    private int slot() { return hand.index() * CATS.length + category.index(); }

    /** Pushes the stored values of the edited slot into the sliders and the preset chips. */
    private void sync() {
        syncing = true;
        try {
            int s = slot();
            float[] a = store.v[s];
            px.set(a[0]); py.set(a[1]); pz.set(a[2]); rx.set(a[3]); ry.set(a[4]); rz.set(a[5]); scale.set(a[6]);
            preset.set(store.preset[s]);
        } finally { syncing = false; }
    }

    private void onSlider() {
        if (syncing) return;
        int s = slot();
        float[] a = store.v[s];
        a[0] = px.floatValue(); a[1] = py.floatValue(); a[2] = pz.floatValue();
        a[3] = rx.floatValue(); a[4] = ry.floatValue(); a[5] = rz.floatValue(); a[6] = scale.floatValue();
        store.active[s] = true;
        store.preset[s] = CUSTOM;
        syncing = true;
        try { preset.set(CUSTOM); } finally { syncing = false; }
        ConfigManager.markDirty();
    }

    private void applyPreset() {
        if (syncing) return;
        int idx = preset.index();
        if (idx == CUSTOM) return;
        int s = slot();
        float side = hand.index() == 1 ? -1f : 1f;              // presets are written for the right hand; mirror for the left
        float[] a = store.v[s];
        a[0] = (float) (Float) PRESETS[idx][1] * side; a[1] = (Float) PRESETS[idx][2]; a[2] = (Float) PRESETS[idx][3];
        a[3] = (Float) PRESETS[idx][4]; a[4] = (Float) PRESETS[idx][5] * side; a[5] = (Float) PRESETS[idx][6] * side;
        a[6] = (Float) PRESETS[idx][7];
        store.active[s] = idx != 0 || category.index() != 0;   // "Default" on All Items simply means: nothing changed
        store.preset[s] = idx;
        sync();
        ConfigManager.markDirty();
    }

    private void resetCurrent() {
        store.setDefault(slot());
        sync();
        ConfigManager.markDirty();
    }

    private void resetAll() {
        store.reset();
        sync();
        ConfigManager.markDirty();
    }

    // ------------------------------------------------------------------ rendering side

    private static int categoryOf(ItemStack s) {
        if (s.isIn(ItemTags.SWORDS)) return 1;
        if (s.isIn(ItemTags.AXES)) return 2;
        if (s.isIn(ItemTags.PICKAXES)) return 3;
        if (s.isIn(ItemTags.SHOVELS)) return 4;
        if (s.isIn(ItemTags.HOES)) return 5;
        if (s.isOf(Items.SHIELD)) return 7;
        if (s.contains(DataComponentTypes.FOOD)) return 6;
        return 8;
    }

    /** Values for this item and hand, or null when nothing is configured (vanilla pose). */
    private static float[] resolve(ItemStack stack, boolean rightArm) {
        ItemPosition m = Modules.ITEM_POS;
        if (m == null || !m.isEnabled()) return null;
        int h = rightArm ? 0 : 1, c = categoryOf(stack);
        int s = h * CATS.length + c;
        if (c != 0 && m.store.active[s]) return m.store.v[s];
        s = h * CATS.length;
        return m.store.active[s] ? m.store.v[s] : null;
    }

    /**
     * Called by the mixin at the start of the first-person item render, inside a matrix push.
     *
     * The transform is applied INSIDE the item's own frame, i.e. after the equip and swing animation of vanilla.
     * To do that from the start of the method, the vanilla frame is built first (equip offset + swing rotations),
     * then the custom position / rotation / scale is applied around the hand point, then the vanilla frame is undone.
     * Vanilla then applies the very same frame again, so the net result is: vanilla frame, then the custom transform.
     * Because of this the swing never flies away, and a bigger scale or a rotation does not stretch the swing motion.
     */
    public static void apply(MatrixStack m, ItemStack stack, Hand hand, AbstractClientPlayerEntity player,
                             float swingProgress, float equipProgress) {
        if (stack == null || stack.isEmpty()) return;
        boolean rightArm = (hand == Hand.MAIN_HAND) == (player.getMainArm() == Arm.RIGHT);
        float[] v = resolve(stack, rightArm);
        if (v == null) return;

        // safe values (also protects against old or hand-edited configs)
        double x = clampValue(0, v[0]), y = clampValue(1, v[1]), z = clampValue(2, v[2]);
        double rxd = clampValue(3, v[3]), ryd = clampValue(4, v[4]), rzd = clampValue(5, v[5]);
        float sc = clampValue(6, v[6]);
        double r = Math.sqrt(x * x + y * y);
        if (r > MAX_OFFSET_XY) { x *= MAX_OFFSET_XY / r; y *= MAX_OFFSET_XY / r; }

        double sw = Math.max(0.0, Math.min(1.0, swingProgress));
        double side = rightArm ? 1.0 : -1.0;
        double sq = Math.sqrt(sw);
        double tx = side * (-0.4 * Math.sin(sq * Math.PI)), ty = 0.2 * Math.sin(sq * Math.PI * 2.0), tz = -0.2 * Math.sin(sw * Math.PI);
        double ex = side * 0.56, ey = -0.52 + equipProgress * -0.6, ez = -0.72;
        double f = Math.sin(sw * sw * Math.PI), g = Math.sin(sq * Math.PI);
        float yaw1 = (float) (side * (45.0 + f * -20.0)), roll = (float) (side * g * -20.0), pitch = (float) (g * -80.0), yaw2 = (float) (side * -45.0);

        // 1) vanilla frame of the item
        m.translate(tx, ty, tz);
        m.translate(ex, ey, ez);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw1));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw2));

        // 2) custom transform around the hand point
        m.translate(x, y, z);
        if (rzd != 0.0) m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) rzd));
        if (ryd != 0.0) m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) ryd));
        if (rxd != 0.0) m.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) rxd));
        if (sc != 1f) m.scale(sc, sc, sc);

        // 3) undo the vanilla frame (vanilla applies it again right after this)
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw2));
        m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-pitch));
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-roll));
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw1));
        m.translate(-ex, -ey, -ez);
        m.translate(-tx, -ty, -tz);
    }
}
