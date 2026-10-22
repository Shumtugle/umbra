package com.shumtugle.umbra;

/**
 * The space around the table. The camera never moves, so the sky is painted once
 * per screen size: a dark field, slow clouds of gas, a faint band of the galaxy,
 * and stars of different heat and brightness. Each frame copies it and lets the
 * brightest stars breathe.
 */
final class Sky {
    final int w, h;
    final int[] px;
    private final int[] twIdx, twCol;
    private final float[] twPhase;
    private int twN;

    Sky(int w, int h) {
        this.w = w;
        this.h = h;
        px = new int[w * h];
        float s = 1f / w;                 // noise is laid out in screen widths
        int bright = 0;
        int[] idx = new int[4096], col = new int[4096];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                float u = x * s, v = y * s;
                // the galaxy: a soft band across the screen
                float band = (float) Math.exp(-sq((v - 0.55f * u - 0.55f) * 3.2f));
                float n1 = fbm(u * 2.2f + 3.1f, v * 2.2f + 7.7f);
                float n2 = fbm(u * 3.6f + 11.3f, v * 3.6f + 1.9f);
                float gas = Col.ramp(0.45f, 0.85f, n1) * (0.45f + 0.55f * band);
                float teal = Col.ramp(0.5f, 0.9f, n2) * (0.35f + 0.65f * band);
                int c = Col.mix(0xFF03040B, 0xFF0A0B1E, 0.5f + 0.5f * band);
                c = Col.addScaled(c, 0xFF3A1670, gas * 0.55f);
                c = Col.addScaled(c, 0xFF0C4060, teal * 0.45f);
                c = Col.addScaled(c, 0xFF2A2440, band * 0.25f * fbm(u * 9f, v * 9f));
                // stars, denser in the band
                float hv = Col.hash(x * 7 + 3, y * 13 + 5);
                float limit = 0.986f - 0.010f * band;
                if (hv > limit) {
                    float k = (hv - limit) / (1f - limit);
                    float lum = 0.18f + 0.82f * k * k * k;
                    int tint = starTint(Col.hash(x + 911, y + 37));
                    c = Col.addScaled(c, tint, lum);
                    if (lum > 0.75f && bright < idx.length) {
                        idx[bright] = y * w + x;
                        col[bright] = tint;
                        bright++;
                    }
                }
                px[y * w + x] = c;
            }
        // the brightest stars get small spikes of light
        for (int i = 0; i < bright; i++) {
            int x = idx[i] % w, y = idx[i] / w;
            if (Col.hash(x + 5, y + 3) > 0.45f) continue;
            for (int d = 1; d <= 2; d++) {
                float k = d == 1 ? 0.45f : 0.16f;
                addAt(x - d, y, col[i], k);
                addAt(x + d, y, col[i], k);
                addAt(x, y - d, col[i], k);
                addAt(x, y + d, col[i], k);
            }
        }
        twIdx = new int[bright];
        twCol = new int[bright];
        twPhase = new float[bright];
        for (int i = 0; i < bright; i++) {
            twIdx[i] = idx[i];
            twCol[i] = px[idx[i]];
            twPhase[i] = Col.hash(i, 77) * 40f;
        }
        twN = bright;
    }

    private static float sq(float v) {
        return v * v;
    }

    private void addAt(int x, int y, int c, float k) {
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        px[y * w + x] = Col.addScaled(px[y * w + x], c, k);
    }

    private static int starTint(float r) {
        if (r < 0.18f) return 0xFFAFC8FF;      // hot, blue
        if (r < 0.70f) return 0xFFF2F4FF;      // white
        if (r < 0.90f) return 0xFFFFE6B8;      // yellow
        return 0xFFFFB98A;                     // cool, orange
    }

    /** Copies the sky into a frame. */
    void paint(int[] dst, float t) {
        System.arraycopy(px, 0, dst, 0, px.length);
    }

    /** Keeps only the bright stars that are not covered in the still picture. */
    void keepVisible(int[] still) {
        int n = 0;
        for (int i = 0; i < twIdx.length; i++)
            if (still[twIdx[i]] == px[twIdx[i]]) {
                twIdx[n] = twIdx[i];
                twCol[n] = twCol[i];
                twPhase[n] = twPhase[i];
                n++;
            }
        twN = n;
    }

    /** Lets the bright stars breathe. */
    void twinkle(int[] dst, float t) {
        for (int i = 0; i < twN; i++) {
            float k = 0.72f + 0.28f * (float) Math.sin(t * 1.7f + twPhase[i]);
            dst[twIdx[i]] = Col.mul(twCol[i], k);
        }
    }

    // ---- value noise

    private static float noise(float x, float y) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float fx = x - ix, fy = y - iy;
        fx = fx * fx * (3f - 2f * fx);
        fy = fy * fy * (3f - 2f * fy);
        float a = Col.hash(ix, iy), b = Col.hash(ix + 1, iy);
        float c = Col.hash(ix, iy + 1), d = Col.hash(ix + 1, iy + 1);
        return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy;
    }

    static float fbm(float x, float y) {
        float sum = 0f, amp = 0.5f, norm = 0f;
        for (int i = 0; i < 5; i++) {
            sum += noise(x, y) * amp;
            norm += amp;
            x = x * 2.03f + 17.1f;
            y = y * 2.03f + 5.3f;
            amp *= 0.5f;
        }
        return sum / norm;
    }
}
