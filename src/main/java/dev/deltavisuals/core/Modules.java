package dev.deltavisuals.core;

import dev.deltavisuals.feature.*;
import dev.deltavisuals.hud.HudModule;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Modules {
    private static final Logger LOG = LoggerFactory.getLogger("DeltaVisuals/Modules");
    private static final List<Module> ALL = new ArrayList<>();
    private static final List<HudModule> HUD = new ArrayList<>();

    public static ThemeModule THEME;
    public static GuiModule GUI;
    public static HudEditorModule HUD_EDITOR;
    public static ProfilesModule PROFILES;
    public static InfoHud INFO;
    public static ArmorHud ARMOR;
    public static Watermark WATERMARK;
    public static TargetEsp TARGET_ESP;
    public static DamageIndicators DAMAGE;
    public static KillEffect KILL;
    public static BlockOverlay BLOCK_OVERLAY;
    public static Trails TRAILS;
    public static Cosmetics COSMETICS;
    public static Trails BLINK;
    public static HitEffects HIT_FX;
    public static HitSounds HIT_SOUNDS;
    public static CustomSky SKY;
    public static SkyCycle SKY_CYCLE;
    public static ItemPosition ITEM_POS;
    public static Markers MARKERS;
    public static DeltaUsers USERS;
    public static TotemSoul TOTEM_SOUL;
    public static NightVision NIGHT_VISION;
    public static PotionHud POTION;
    public static CooldownHud COOLDOWN;
    public static TotemCount TOTEM_COUNT;
    public static PopCounter POP_COUNTER;
    public static Zoom ZOOM;
    public static ScreenAnim SCREEN_ANIM;
    public static ItemMotion ITEM_MOTION;
    public static WorldTime WORLD_TIME;
    public static SkyColor SKY_COLOR;
    public static ProjectileFx PROJECTILE_FX;
    public static AirParticles AIR_PARTICLES;
    public static TargetHud TARGET_HUD;
    public static HitboxFill HITBOX_FILL;

    private Modules() {}

    private static <T extends Module> T reg(T m) {
        ALL.add(m);
        if (m instanceof HudModule h) HUD.add(h);
        return m;
    }

    public static void init() {
        if (!ALL.isEmpty()) return;
        // order = order inside each category
        INFO = reg(new InfoHud());
        ARMOR = reg(new ArmorHud());
        POTION = reg(new PotionHud());
        COOLDOWN = reg(new CooldownHud());
        TARGET_HUD = reg(new TargetHud());
        TOTEM_COUNT = reg(new TotemCount());
        POP_COUNTER = reg(new PopCounter());
        WATERMARK = reg(new Watermark());
        SKY = reg(new CustomSky());          // first, so the sky is drawn before every other world effect
        SKY_CYCLE = reg(new SkyCycle());
        WORLD_TIME = reg(new WorldTime());    // after Custom Sky: its dome is only drawn when no Custom Sky preset is on
        SKY_COLOR = reg(new SkyColor());      // last of the sky modules: a see-through tint over whatever sky is shown
        NIGHT_VISION = reg(new NightVision());
        TARGET_ESP = reg(new TargetEsp());
        KILL = reg(new KillEffect());
        HIT_FX = reg(new HitEffects());
        TOTEM_SOUL = reg(new TotemSoul());
        PROJECTILE_FX = reg(new ProjectileFx());
        HITBOX_FILL = reg(new HitboxFill());
        DAMAGE = reg(new DamageIndicators());
        ITEM_POS = reg(new ItemPosition());
        HIT_SOUNDS = reg(new HitSounds());
        BLOCK_OVERLAY = reg(new BlockOverlay());
        TRAILS = reg(new Trails("Trails", false));
        COSMETICS = reg(new Cosmetics());
        BLINK = reg(new Trails("Blink", true));
        MARKERS = reg(new Markers());
        ITEM_MOTION = reg(new ItemMotion());
        AIR_PARTICLES = reg(new AirParticles());
        ZOOM = reg(new Zoom());
        SCREEN_ANIM = reg(new ScreenAnim());
        USERS = reg(new DeltaUsers());
        GUI = reg(new GuiModule());
        HUD_EDITOR = reg(new HudEditorModule());
        THEME = reg(new ThemeModule());
        PROFILES = reg(new ProfilesModule());

        // client-side attack notification (Fabric API callback, no mixin needed)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient()) {
                TARGET_ESP.onAttack(entity);
                DAMAGE.onAttack(entity);
                HIT_FX.onAttack(entity, hit != null ? hit.getPos() : null);
                HIT_SOUNDS.onAttack(entity);
                TOTEM_SOUL.onAttack(entity);
                AIR_PARTICLES.onAttack();
            }
            return ActionResult.PASS;
        });
    }

    public static List<Module> all() { return Collections.unmodifiableList(ALL); }
    public static List<HudModule> hud() { return Collections.unmodifiableList(HUD); }

    public static List<Module> byCategory(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : ALL) if (m.category == c) out.add(m);
        return out;
    }

    /** Ticks enabled modules; a crashing module is disabled instead of crashing the game. */
    public static void tick(MinecraftClient mc) {
        for (Module m : ALL) {
            if (!(m.isEnabled() || m.tickWhenDisabled)) continue;
            try {
                m.onTick(mc);
                m.crashes = 0;
            } catch (Throwable t) {
                // one bad tick must not silently (and permanently) switch a module off: only repeated crashes do
                if (++m.crashes >= 3) {
                    LOG.error("Module {} crashed in onTick {} times in a row and was disabled", m.name, m.crashes, t);
                    m.setEnabled(false);
                    m.tickWhenDisabled = false;
                    m.crashes = 0;
                } else {
                    LOG.warn("Module {} threw in onTick (ignored, {}/3)", m.name, m.crashes, t);
                }
            }
        }
    }
}
