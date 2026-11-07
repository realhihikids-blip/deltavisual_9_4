package dev.deltavisuals.util;

/** Deterministic pseudo-random numbers from an int seed: no Random objects, no per-frame allocation. */
public final class Hash {
    private Hash() {}

    public static int mix(int x) {
        x ^= x >>> 16; x *= 0x7feb352d;
        x ^= x >>> 15; x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }
    /** 0..1 */
    public static float f(int seed) { return (mix(seed) >>> 8) / 16777216f; }
    public static float f(int seed, int k) { return f(seed * 0x9E3779B1 + k * 0x85EBCA6B + 0x27D4EB2F); }
    /** -1..1 */
    public static float s(int seed, int k) { return f(seed, k) * 2f - 1f; }

    public static float smooth(float t) { t = Math.max(0f, Math.min(1f, t)); return t * t * (3f - 2f * t); }
    public static float clamp01(float t) { return Math.max(0f, Math.min(1f, t)); }
}
