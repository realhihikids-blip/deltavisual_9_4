package dev.deltavisuals.util;

public final class ColorUtil {
    private ColorUtil() {}

    public static int argb(int a, int r, int g, int b) {
        return (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
    }
    public static int alpha(int c) { return c >>> 24; }
    public static int red(int c) { return c >> 16 & 255; }
    public static int green(int c) { return c >> 8 & 255; }
    public static int blue(int c) { return c & 255; }

    public static int withAlpha(int c, int a) {
        return (Math.max(0, Math.min(255, a)) << 24) | (c & 0xFFFFFF);
    }
    public static int mulAlpha(int c, float f) {
        return withAlpha(c, Math.round(alpha(c) * Math.max(0f, Math.min(1f, f))));
    }
    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return argb(
            Math.round(alpha(a) + (alpha(b) - alpha(a)) * t),
            Math.round(red(a) + (red(b) - red(a)) * t),
            Math.round(green(a) + (green(b) - green(a)) * t),
            Math.round(blue(a) + (blue(b) - blue(a)) * t));
    }

    /** h, s, v in 0..1 */
    public static int hsv(float h, float s, float v, int alpha) {
        h = h - (float) Math.floor(h);
        float c = v * s;
        float x = c * (1f - Math.abs((h * 6f) % 2f - 1f));
        float m = v - c;
        float r, g, b;
        switch ((int) (h * 6f) % 6) {
            case 0: r = c; g = x; b = 0; break;
            case 1: r = x; g = c; b = 0; break;
            case 2: r = 0; g = c; b = x; break;
            case 3: r = 0; g = x; b = c; break;
            case 4: r = x; g = 0; b = c; break;
            default: r = c; g = 0; b = x; break;
        }
        return argb(alpha, Math.round((r + m) * 255f), Math.round((g + m) * 255f), Math.round((b + m) * 255f));
    }

    public static float[] toHsv(int c) {
        float r = red(c) / 255f, g = green(c) / 255f, b = blue(c) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min, h;
        if (d == 0) h = 0;
        else if (max == r) h = (((g - b) / d) % 6f) / 6f;
        else if (max == g) h = (((b - r) / d) + 2f) / 6f;
        else h = (((r - g) / d) + 4f) / 6f;
        if (h < 0) h += 1f;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    public static String hex(int c, boolean withAlpha) {
        return withAlpha ? String.format("%08X", c) : String.format("%06X", c & 0xFFFFFF);
    }

    /** Accepts RRGGBB or AARRGGBB (with optional '#'). Returns null if invalid. */
    public static Integer parseHex(String s, int currentAlpha) {
        if (s == null) return null;
        if (s.startsWith("#")) s = s.substring(1);
        try {
            if (s.length() == 6) return withAlpha((int) Long.parseLong(s, 16), currentAlpha);
            if (s.length() == 8) return (int) Long.parseLong(s, 16);
        } catch (NumberFormatException ignored) { }
        return null;
    }
}
