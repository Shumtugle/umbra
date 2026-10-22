package com.shumtugle.umbra;

/** Small helpers for ARGB colors held in ints. */
final class Col {
    private Col() {}

    static int rgb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    static int clamp(int v) {
        return v < 0 ? 0 : v > 255 ? 255 : v;
    }

    static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    static int r(int c) {
        return (c >> 16) & 255;
    }

    static int g(int c) {
        return (c >> 8) & 255;
    }

    static int b(int c) {
        return c & 255;
    }

    /** Linear blend: t = 0 gives a, t = 1 gives b. */
    static int mix(int a, int b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        int it = (int) (t * 256f), ia = 256 - it;
        int r = (r(a) * ia + r(b) * it) >> 8;
        int g = (g(a) * ia + g(b) * it) >> 8;
        int bl = (b(a) * ia + b(b) * it) >> 8;
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** Scales brightness. */
    static int mul(int c, float k) {
        return rgb((int) (r(c) * k), (int) (g(c) * k), (int) (b(c) * k));
    }

    /** Adds light, per channel. */
    static int add(int c, int r, int g, int b) {
        return rgb(r(c) + r, g(c) + g, b(c) + b);
    }

    /** Adds another color scaled by k (for glow). */
    static int addScaled(int c, int glow, float k) {
        return rgb(r(c) + (int) (r(glow) * k), g(c) + (int) (g(glow) * k), b(c) + (int) (b(glow) * k));
    }

    /** Smooth 0..1 ramp between e0 and e1, in either direction. */
    static float ramp(float e0, float e1, float x) {
        float t = (x - e0) / (e1 - e0);
        t = t < 0f ? 0f : t > 1f ? 1f : t;
        return t * t * (3f - 2f * t);
    }

    /** Cheap stable noise in 0..1 from two integers. */
    static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }
}
