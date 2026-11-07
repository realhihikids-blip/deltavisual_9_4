package dev.deltavisuals.feature;

import dev.deltavisuals.core.Category;
import dev.deltavisuals.core.Module;
import dev.deltavisuals.core.Modules;
import dev.deltavisuals.core.Setting;
import dev.deltavisuals.social.Presence;
import dev.deltavisuals.util.Compat;
import dev.deltavisuals.util.IconFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * Puts the Delta Visuals crystal next to the nick of every player who also uses the mod: in the tab list and on the
 * name tag above their head. Who uses the mod comes from {@link Presence}. Purely visual, client side.
 */
public final class DeltaUsers extends Module {
    public final Setting.Bool tab, head, self, announce;

    public DeltaUsers() {
        super("Delta Users", "Delta Visuals logo next to the nick of players who also use the mod (tab list and above head)", Category.PLAYER, true);
        tab = add(new Setting.Bool("Logo In Tab", true));
        head = add(new Setting.Bool("Logo Above Head", true));
        self = add(new Setting.Bool("Logo On Myself", true));
        announce = add(new Setting.Bool("Announce Me To Other Users", true));
        add(new Setting.Header("Presence server"));
        add(new Setting.Info("Status", Presence::status));
        add(new Setting.Info("Server", () -> Presence.serverUrl().isEmpty() ? "config/delta_visuals/presence.json" : Presence.serverUrl()));
        add(new Setting.Info("Users online", () -> String.valueOf(Presence.onlineCount())));
        add(new Setting.Action("Reload Presence Config", Presence::loadConfig));
        add(new Setting.Info("Manual list", () -> "/delta user add NICK"));
    }

    @Override public void onDisable() { Presence.clearOnline(); }

    @Override
    public void onTick(MinecraftClient mc) { Presence.tick(mc, announce.get()); }

    private boolean isUser(MinecraftClient mc, UUID id, String name) {
        if (mc.player != null && id != null && id.equals(mc.player.getUuid())) return self.get();
        return Presence.isUser(id, name);
    }

    private static Text withLogo(Text name) {
        if (IconFont.hasLogo(name)) return name;
        return Text.empty().append(name).append(Text.literal(" ")).append(IconFont.logo());
    }

    // ------------------------------------------------------------------ hooks called from the mixins

    /** Tab list entry name. */
    public static Text decorateTab(PlayerListEntry entry, Text name) {
        DeltaUsers m = Modules.USERS;
        if (m == null || !m.isEnabled() || !m.tab.get() || name == null || entry == null) return name;
        try {
            Object profile = entry.getProfile();
            if (!m.isUser(MinecraftClient.getInstance(), Compat.profileId(profile), Compat.profileName(profile))) return name;
            return withLogo(name);
        } catch (Throwable t) {
            return name;
        }
    }

    /** Name above a player's head. Only for players of the client world (never the integrated server's copies). */
    public static Text decorateHead(PlayerEntity p, Text name) {
        DeltaUsers m = Modules.USERS;
        if (m == null || !m.isEnabled() || !m.head.get() || name == null) return name;
        if (!(p instanceof AbstractClientPlayerEntity)) return name;   // not the integrated server's copy of a player
        try {
            if (!m.isUser(MinecraftClient.getInstance(), p.getUuid(), p.getName().getString())) return name;
            return withLogo(name);
        } catch (Throwable t) {
            return name;
        }
    }
}
