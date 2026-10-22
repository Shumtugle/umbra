package com.shumtugle.umbra;

/**
 * A tiny integer pixel canvas, ARGB. The whole frame and all game art are painted
 * here, pixel by pixel; nothing is loaded from disk. Plain Java, no platform classes,
 * so the desktop stand can run the same code.
 */
final class Px {
    final int w, h;
    final int[] a;
    boolean wrap; // horizontal wrap-around for seamless background strips

    Px(int w, int h) {
        this.w = w;
        this.h = h;
        this.a = new int[w * h];
    }

    void set(int x, int y, int c) {
        if (wrap) x = ((x % w) + w) % w;
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        a[y * w + x] = c;
    }

    int get(int x, int y) {
        if (wrap) x = ((x % w) + w) % w;
        if (x < 0 || y < 0 || x >= w || y >= h) return 0;
        return a[y * w + x];
    }

    /** Fills [x0, x1) x [y0, y1). */
    void rect(int x0, int y0, int x1, int y1, int c) {
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) set(x, y, c);
    }

    void disc(float cx, float cy, float r, int c) {
        int r0 = (int) Math.ceil(r);
        float rr = r * r + r * 0.6f;
        for (int y = -r0; y <= r0; y++)
            for (int x = -r0; x <= r0; x++)
                if (x * x + y * y <= rr) set(Math.round(cx) + x, Math.round(cy) + y, c);
    }

    void ellipse(float cx, float cy, float rx, float ry, int c) {
        int x0 = (int) Math.floor(cx - rx), x1 = (int) Math.ceil(cx + rx);
        int y0 = (int) Math.floor(cy - ry), y1 = (int) Math.ceil(cy + ry);
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                float dx = (x - cx) / rx, dy = (y - cy) / ry;
                if (dx * dx + dy * dy <= 1.05f) set(x, y, c);
            }
    }

    /** Recolors existing pixels of one color with a sparse random pattern. */
    void speckle(java.util.Random rnd, int from, int to, float density) {
        for (int i = 0; i < a.length; i++)
            if (a[i] == from && rnd.nextFloat() < density) a[i] = to;
    }

    /** One-pixel dark outline around every fully opaque shape. */
    void outline(int c) {
        int[] src = a.clone();
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                if ((src[y * w + x] >>> 24) != 0) continue;
                if (opaque(src, x - 1, y) || opaque(src, x + 1, y)
                        || opaque(src, x, y - 1) || opaque(src, x, y + 1)) a[y * w + x] = c;
            }
    }

    private boolean opaque(int[] src, int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) return false;
        return (src[y * w + x] >>> 24) == 0xFF;
    }

    void clear(int c) {
        java.util.Arrays.fill(a, c);
    }

    /** Copies a sprite in, skipping fully transparent pixels. */
    void blit(Px s, int x, int y) {
        for (int j = 0; j < s.h; j++)
            for (int i = 0; i < s.w; i++) {
                int c = s.a[j * s.w + i];
                if ((c >>> 24) != 0) set(x + i, y + j, c);
            }
    }

    /** Mixes colour c into the pixel by amount a (0..1). */
    void blend(int x, int y, int c, float a) {
        if (x < 0 || y < 0 || x >= w || y >= h || a <= 0f) return;
        int i = y * w + x;
        this.a[i] = Col.mix(this.a[i], c, a);
    }

    /** A translucent filled disc. */
    void discBlend(float cx, float cy, float r, int c, float a) {
        ellipseBlend(cx, cy, r, r, c, a);
    }

    /** A translucent filled ellipse. */
    void ellipseBlend(float cx, float cy, float rx, float ry, int c, float a) {
        int x0 = (int) Math.floor(cx - rx), x1 = (int) Math.ceil(cx + rx);
        int y0 = (int) Math.floor(cy - ry), y1 = (int) Math.ceil(cy + ry);
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                float dx = (x + 0.5f - cx) / rx, dy = (y + 0.5f - cy) / ry;
                if (dx * dx + dy * dy <= 1f) blend(x, y, c, a);
            }
    }

    /** A translucent ellipse outline about one pixel thick. */
    void ringBlend(float cx, float cy, float rx, float ry, int c, float a) {
        int x0 = (int) Math.floor(cx - rx) - 1, x1 = (int) Math.ceil(cx + rx) + 1;
        int y0 = (int) Math.floor(cy - ry) - 1, y1 = (int) Math.ceil(cy + ry) + 1;
        float edge = 1.2f / Math.max(2f, Math.min(rx, ry));
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                float dx = (x + 0.5f - cx) / rx, dy = (y + 0.5f - cy) / ry;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (Math.abs(d - 1f) < edge) blend(x, y, c, a);
            }
    }

    /** A line of the given thickness in pixels. */
    void line(float x0, float y0, float x1, float y1, float thick, int c) {
        float dx = x1 - x0, dy = y1 - y0;
        int n = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy))) + 1;
        float r = thick / 2f;
        for (int i = 0; i <= n; i++) {
            float k = i / (float) n;
            float cx = x0 + dx * k, cy = y0 + dy * k;
            if (thick <= 1.5f) set(Math.round(cx - 0.5f), Math.round(cy - 0.5f), c);
            else disc(cx, cy, r - 0.5f, c);
        }
    }

    // ---- smooth shapes: edges are blended by how much of the pixel they cover

    /** Adds light to the pixel: colour c scaled by k. */
    void add(int x, int y, int c, float k) {
        if (x < 0 || y < 0 || x >= w || y >= h || k <= 0f) return;
        int i = y * w + x;
        a[i] = Col.addScaled(a[i], c, k);
    }

    /** A filled ellipse with a soft one-pixel edge, blended at opacity op. */
    void ellipseAA(float cx, float cy, float rx, float ry, int c, float op) {
        if (rx <= 0f || ry <= 0f) return;
        int x0 = (int) Math.floor(cx - rx - 1), x1 = (int) Math.ceil(cx + rx + 1);
        int y0 = (int) Math.floor(cy - ry - 1), y1 = (int) Math.ceil(cy + ry + 1);
        float edge = 1f / Math.min(rx, ry);
        for (int y = Math.max(0, y0); y <= Math.min(h - 1, y1); y++)
            for (int x = Math.max(0, x0); x <= Math.min(w - 1, x1); x++) {
                float dx = (x + 0.5f - cx) / rx, dy = (y + 0.5f - cy) / ry;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float cov = (1f - d) / edge + 0.5f;
                if (cov <= 0f) continue;
                blend(x, y, c, op * Math.min(1f, cov));
            }
    }

    /** A filled disc with a soft edge. */
    void discAA(float cx, float cy, float r, int c, float op) {
        ellipseAA(cx, cy, r, r, c, op);
    }

    /** An elliptical ring of the given width in pixels, soft on both sides. */
    void ringAA(float cx, float cy, float rx, float ry, float width, int c, float op) {
        if (rx <= 0f || ry <= 0f) return;
        int x0 = (int) Math.floor(cx - rx - width - 1), x1 = (int) Math.ceil(cx + rx + width + 1);
        int y0 = (int) Math.floor(cy - ry - width - 1), y1 = (int) Math.ceil(cy + ry + width + 1);
        float half = width / 2f;
        for (int y = Math.max(0, y0); y <= Math.min(h - 1, y1); y++)
            for (int x = Math.max(0, x0); x <= Math.min(w - 1, x1); x++) {
                float dx = (x + 0.5f - cx) / rx, dy = (y + 0.5f - cy) / ry;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                // distance to the ellipse in pixels, along the gradient
                float gx = dx / rx, gy = dy / ry;
                float g = (float) Math.sqrt(gx * gx + gy * gy) + 1e-6f;
                float dist = Math.abs(d - 1f) * d / g;
                float cov = half - dist + 0.5f;
                if (cov <= 0f) continue;
                blend(x, y, c, op * Math.min(1f, cov));
            }
    }

    /** A line with round ends, width in pixels, soft edged. */
    void lineAA(float x0, float y0, float x1, float y1, float width, int c, float op) {
        float half = width / 2f;
        int bx0 = (int) Math.floor(Math.min(x0, x1) - half - 1), bx1 = (int) Math.ceil(Math.max(x0, x1) + half + 1);
        int by0 = (int) Math.floor(Math.min(y0, y1) - half - 1), by1 = (int) Math.ceil(Math.max(y0, y1) + half + 1);
        float dx = x1 - x0, dy = y1 - y0, len2 = dx * dx + dy * dy;
        for (int y = Math.max(0, by0); y <= Math.min(h - 1, by1); y++)
            for (int x = Math.max(0, bx0); x <= Math.min(w - 1, bx1); x++) {
                float px = x + 0.5f - x0, py = y + 0.5f - y0;
                float t = len2 > 1e-6f ? Col.clamp01((px * dx + py * dy) / len2) : 0f;
                float ex = px - dx * t, ey = py - dy * t;
                float dist = (float) Math.sqrt(ex * ex + ey * ey);
                float cov = half - dist + 0.5f;
                if (cov <= 0f) continue;
                blend(x, y, c, op * Math.min(1f, cov));
            }
    }

    /** Soft light around a point: falls off smoothly to nothing at radius r. */
    void glow(float cx, float cy, float r, int c, float k) {
        int x0 = (int) Math.floor(cx - r), x1 = (int) Math.ceil(cx + r);
        int y0 = (int) Math.floor(cy - r), y1 = (int) Math.ceil(cy + r);
        float inv = 1f / (r * r);
        for (int y = Math.max(0, y0); y <= Math.min(h - 1, y1); y++)
            for (int x = Math.max(0, x0); x <= Math.min(w - 1, x1); x++) {
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                float q = 1f - (dx * dx + dy * dy) * inv;
                if (q <= 0f) continue;
                add(x, y, c, k * q * q);
            }
    }
}
