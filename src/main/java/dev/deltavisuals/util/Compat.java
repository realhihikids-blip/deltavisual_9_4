package dev.deltavisuals.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Version-sensitive Minecraft calls that are not plain Screen/DrawContext usage.
 * If a Minecraft/Yarn update breaks compilation, the fix is most likely in this file,
 * in render/Pipelines.java or in render/WorldHook.java.
 */
public final class Compat {
    private Compat() {}

    /** Camera position in world space. (Older Yarn: camera.getPos().) */
    public static Vec3d cameraPos(Camera camera) { return camera.getCameraPos(); }

    /** Render partial tick (0..1). */
    public static float partialTick(MinecraftClient mc) { return mc.getRenderTickCounter().getTickProgress(false); }

    /** True if a vanilla Minecraft control is currently bound to the same physical key / button. */
    public static boolean conflictsWithVanilla(MinecraftClient mc, boolean mouse, int code) {
        try {
            InputUtil.Type t = mouse ? InputUtil.Type.MOUSE : InputUtil.Type.KEYSYM;
            String key = t.createFromCode(code).getTranslationKey();
            for (KeyBinding kb : mc.options.allKeys) {
                if (key.equals(kb.getBoundKeyTranslationKey())) return true;
            }
        } catch (Throwable ignored) { }
        return false;
    }

    // ---------------------------------------------------------------- custom sky helpers

    /** Render distance in chunks (an option value, not the effective distance). */
    public static int viewDistanceChunks(MinecraftClient mc) { return mc.options.getViewDistance().getValue(); }

    private static CloudRenderMode savedClouds;
    /** Modules that currently want the vanilla clouds hidden (Custom Sky, World Time...). */
    private static final java.util.Set<Object> CLOUD_OWNERS = new java.util.HashSet<>();
    private static final Object DEFAULT_OWNER = new Object();

    /**
     * Hides / restores the vanilla clouds through the normal video option, so they do not float over a custom sky.
     * The previous value is restored when the sky is switched off and when the client stops.
     */
    public static void hideClouds(MinecraftClient mc, boolean hide) { hideClouds(mc, DEFAULT_OWNER, hide); }

    /**
     * Same, but with an owner: the clouds come back only when NO owner wants them hidden any more,
     * so two sky modules cannot fight over the video option.
     */
    public static void hideClouds(MinecraftClient mc, Object owner, boolean hide) {
        if (hide) CLOUD_OWNERS.add(owner); else CLOUD_OWNERS.remove(owner);
        try {
            var opt = mc.options.getCloudRenderMode();
            if (!CLOUD_OWNERS.isEmpty()) {
                if (savedClouds == null && opt.getValue() != CloudRenderMode.OFF) {
                    savedClouds = opt.getValue();
                    opt.setValue(CloudRenderMode.OFF);
                }
            } else if (savedClouds != null) {
                opt.setValue(savedClouds);
                savedClouds = null;
            }
        } catch (Throwable ignored) { }
    }

    /** Gives the vanilla cloud option back no matter who asked to hide it (client stopping). */
    public static void restoreClouds(MinecraftClient mc) {
        CLOUD_OWNERS.clear();
        hideClouds(mc, DEFAULT_OWNER, false);
    }

    // ---------------------------------------------------------------- game profile helpers

    private static boolean profileResolved;
    private static Method profileId, profileName;

    private static Method find(Class<?> c, String... names) {
        for (String n : names) {
            try { return c.getMethod(n); } catch (ReflectiveOperationException ignored) { }
        }
        return null;
    }

    private static void resolveProfile(Object profile) {
        profileResolved = true;
        profileId = find(profile.getClass(), "id", "getId");
        profileName = find(profile.getClass(), "name", "getName");
    }

    /**
     * UUID of a GameProfile. GameProfile became a record in newer Minecraft / authlib versions (id() instead of getId()),
     * so the accessor is looked up once by name. Takes Object so no authlib import is needed.
     */
    public static UUID profileId(Object profile) {
        if (profile == null) return null;
        try {
            if (!profileResolved) resolveProfile(profile);
            return profileId == null ? null : (UUID) profileId.invoke(profile);
        } catch (Throwable t) { return null; }
    }

    public static String profileName(Object profile) {
        if (profile == null) return null;
        try {
            if (!profileResolved) resolveProfile(profile);
            return profileName == null ? null : (String) profileName.invoke(profile);
        } catch (Throwable t) { return null; }
    }

    /**
     * All entries of the tab list. The accessor name differs between versions (getPlayerList / getListedPlayerListEntries),
     * so it is looked up by name; an empty list if neither exists.
     */
    public static Collection<?> playerList(Object networkHandler) {
        if (networkHandler == null) return List.of();
        for (String n : new String[]{"getPlayerList", "getListedPlayerListEntries"}) {
            try {
                Object r = networkHandler.getClass().getMethod(n).invoke(networkHandler);
                if (r instanceof Collection<?> c) return c;
            } catch (ReflectiveOperationException ignored) { }
        }
        return List.of();
    }
}
