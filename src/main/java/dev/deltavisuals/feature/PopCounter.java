package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.hud.TextHud;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Counts how many totems each player has popped. Fed by LivingEntityStatusMixin (entity status 35), which is
 * information the server already sends to every client. An entry disappears after a timeout or when that player dies.
 */
public final class PopCounter extends TextHud {
    private static final class Entry {
        String name = "";
        int count;
        long last;
    }

    private final Map<UUID, Entry> map = new LinkedHashMap<>();
    private final Setting.Num timeout, range, maxRows;
    private final Setting.Bool own, chat, title;

    public PopCounter() {
        super("Totem Pops", "How many totems each player has popped", Category.HUD, true, 0.005, 0.5);
        timeout = add(new Setting.Num("Forget After", 10, 300, 5, 60, " s"));
        range = add(new Setting.Num("Max Distance", 8, 128, 1, 64, " m"));
        maxRows = add(new Setting.Num("Max Players", 1, 10, 1, 5));
        title = add(new Setting.Bool("Show Title", true));
        own = add(new Setting.Bool("Count My Own Pops", false));
        chat = add(new Setting.Bool("Chat Message", false));
    }

    /** Called from LivingEntityStatusMixin when an entity plays the totem-pop status. */
    public void onPop(LivingEntity e) {
        if (!isEnabled() || !(e instanceof PlayerEntity p)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        boolean me = p == mc.player;
        if (me && !own.get()) return;
        double r = range.get();
        if (!me && p.squaredDistanceTo(mc.player) > r * r) return;
        Entry en = map.computeIfAbsent(p.getUuid(), k -> new Entry());
        en.name = me ? "You" : p.getName().getString();
        en.count++;
        en.last = System.currentTimeMillis();
        if (chat.get()) mc.player.sendMessage(Text.literal(en.name + " popped a totem (" + en.count + ")"), false);
    }

    @Override public void onDisable() { map.clear(); }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) { map.clear(); return; }
        long now = System.currentTimeMillis();
        long limit = (long) (timeout.get() * 1000);
        Iterator<Map.Entry<UUID, Entry>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Entry> me = it.next();
            PlayerEntity pl = mc.world.getPlayerByUuid(me.getKey());
            if (now - me.getValue().last > limit || (pl != null && !pl.isAlive())) it.remove();
        }
    }

    @Override
    protected void collect(MinecraftClient mc, List<Line> out, boolean editing) {
        if (map.isEmpty()) return;
        List<Entry> list = new ArrayList<>(map.values());
        list.sort(Comparator.comparingInt((Entry en) -> en.count).reversed());
        if (title.get()) out.add(new Line("Totem Pops", 0xFF9CA3AF));
        int shown = 0;
        for (Entry en : list) {
            if (shown++ >= maxRows.intValue()) break;
            out.add(new Line(en.name + "  x" + en.count, en.count >= 2 ? 0xFFF87171 : color.get()));
        }
    }
}
