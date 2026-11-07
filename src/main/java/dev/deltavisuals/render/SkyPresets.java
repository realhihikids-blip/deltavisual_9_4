package dev.deltavisuals.render;

import dev.deltavisuals.render.SkyRenderer.Look;

/** The sky presets. Index 0 is the vanilla sky (nothing is drawn). Palettes are RGB. */
public final class SkyPresets {
    public static final String[] NAMES = {
        "Default Sky", "Space", "Stormy Sunset", "Pink Sunset", "Clear Day", "Aurora", "Black Hole",
        "Sunset Sky", "Night Sky", "Fantasy Sky", "Custom Sky", "Gradient Sky", "Custom Color Sky"
    };
    public static final Look[] LOOKS = new Look[NAMES.length];

    private static Look mk(int i, int top, int mid, int hor, int bot, int glow, int cl, int cd) {
        Look l = new Look(NAMES[i]);
        l.top = top; l.mid = mid; l.horizon = hor; l.bottom = bot; l.glow = glow; l.cloudLight = cl; l.cloudDark = cd;
        LOOKS[i] = l;
        return l;
    }

    static {
        // 1 Space: purple / pink nebula, bright twinkling stars, soft galactic band
        Look l = mk(1, 0x05020F, 0x14083A, 0x2E0F52, 0x06030F, 0xFF5DB0, 0xFF5FC4, 0x5B34D6);
        l.nebula = true; l.stars = 1f;

        // 2 Stormy Sunset: dark swirling thunderclouds, red band at the horizon
        l = mk(2, 0x151A20, 0x3A434C, 0x6B4A4C, 0x1E2126, 0xFF5A32, 0x8996A3, 0x161B21);
        l.cover = 0.60f; l.zenithMass = 0.2f; l.cloudAlpha = 1f; l.streakAz = 3.4f; l.streakEl = 1.7f; l.swirl = 1.4f; l.thickDark = 0.8f;
        l.sunStrength = 0.9f; l.sunWide = 1.1f; l.sunTall = 0.10f;

        // 3 Pink Sunset: pink / lilac sky with a dark mauve cloud mass overhead
        l = mk(3, 0xA45F8E, 0xE08AB0, 0xF6C0CE, 0xE4B8BC, 0xFFB4C8, 0xFFC9D8, 0x4A3A64);
        l.cover = 0.36f; l.zenithMass = 0.62f; l.cloudAlpha = 1f; l.streakAz = 3.0f; l.streakEl = 1.7f; l.swirl = 0.8f; l.thickDark = 0.9f;
        l.sunStrength = 0.35f; l.sunWide = 0.9f; l.sunTall = 0.25f;

        // 4 Clear Day: saturated blue with a warm glow and streaks around the sun
        l = mk(4, 0x0F47B0, 0x2F7FD8, 0x8FC8F0, 0x86BCE4, 0xFFD25A, 0xFFFFFF, 0x7FAFE6);
        l.cover = 0.45f; l.cloudAlpha = 0.8f; l.streakAz = 1.4f; l.streakEl = 7.5f; l.swirl = 0.3f; l.thickDark = 0f;
        l.sunStrength = 1.0f; l.sunWide = 0.42f; l.sunTall = 0.11f; l.sunCore = true;

        // 5 Aurora: night sky, green / teal / violet curtains, stars
        l = mk(5, 0x01030A, 0x05122A, 0x0C3640, 0x03070C, 0x2BFFA0, 0x3C4C80, 0x080C1C);
        l.aurora = true; l.stars = 1f;

        // 6 Black Hole: dark sky, glowing ring and accretion disk, faint stars
        l = mk(6, 0x040507, 0x101216, 0x383B42, 0x121317, 0xFFC48C, 0x606670, 0x101216);
        l.hole = true; l.stars = 0.55f;

        // 7 Sunset Sky: classic indigo to orange sunset with warm clouds
        l = mk(7, 0x242A6B, 0xA1568F, 0xFF8F45, 0xFFB777, 0xFFD27A, 0xFFC0A0, 0x5C3A70);
        l.cover = 0.45f; l.streakAz = 2.2f; l.streakEl = 6f; l.swirl = 0.4f; l.thickDark = 0.5f;
        l.sunStrength = 1.0f; l.sunWide = 0.75f; l.sunTall = 0.2f; l.sunCore = true;

        // 8 Night Sky: deep blue night, moon, stars, thin clouds
        l = mk(8, 0x02040D, 0x09112D, 0x1B2B58, 0x070A16, 0xA9C3FF, 0x3C4C80, 0x080C1C);
        l.cover = 0.3f; l.cloudAlpha = 0.5f; l.streakAz = 2.4f; l.streakEl = 5f; l.thickDark = 0.3f;
        l.stars = 1f; l.moon = true; l.sunStrength = 0.5f; l.sunWide = 0.5f; l.sunTall = 0.5f;

        // 9 Fantasy Sky: violet to pink with pastel clouds, sparkles and a soft rainbow shimmer
        l = mk(9, 0x3B1D93, 0xAC4FD8, 0xFF9BD2, 0x7B3FA2, 0xFFF0A8, 0xB0F2FF, 0xFF86D2);
        l.cover = 0.5f; l.streakAz = 2.6f; l.streakEl = 4.5f; l.swirl = 0.9f; l.thickDark = 0.3f;
        l.sunStrength = 0.7f; l.sunWide = 0.7f; l.sunTall = 0.22f; l.sunCore = true; l.stars = 0.5f; l.fantasy = true;

        // 10 Custom Sky: your colours, clouds, glow, a few faint stars
        l = mk(10, 0x2646A8, 0x7E87C8, 0xFFC9A0, 0x8FA8C8, 0xFFD68A, 0xFFFFFF, 0x7A86B8);
        l.custom = true; l.customKind = 0;
        l.cover = 0.45f; l.cloudAlpha = 0.85f; l.streakAz = 2.0f; l.streakEl = 6f; l.swirl = 0.5f; l.thickDark = 0.4f;
        l.sunStrength = 0.8f; l.sunWide = 0.7f; l.sunTall = 0.2f; l.sunCore = true; l.stars = 0.25f;

        // 11 Gradient Sky: plain three colour gradient
        l = mk(11, 0x2646A8, 0x7E87C8, 0xFFC9A0, 0x8FA8C8, 0xFFD68A, 0xFFFFFF, 0x7A86B8);
        l.custom = true; l.customKind = 1;

        // 12 Custom Color Sky: one colour with a soft horizon
        l = mk(12, 0x2646A8, 0x2646A8, 0x2646A8, 0x2646A8, 0xFFD68A, 0xFFFFFF, 0x7A86B8);
        l.custom = true; l.customKind = 2;
    }

    private SkyPresets() {}

    public static Look get(int index) { return index <= 0 || index >= LOOKS.length ? null : LOOKS[index]; }
}
