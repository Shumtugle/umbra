package com.shumtugle.umbra;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Paints the launcher icon: the black hole over the far end of a bent table, and
 * the ball swinging round it on a curved tail. Painted large, pixel by pixel, then
 * shrunk by averaging to each launcher size, so the edges come out smooth.
 *
 *   java ... IconArt res out/icon     writes res/mipmap-* /ic_launcher.png and a large copy
 */
public final class IconArt {
    private static final int BIG = 1024;

    public static void main(String[] args) throws IOException {
        File res = new File(args.length > 0 ? args[0] : "res");
        float[][] img = paint(BIG);
        String[] dirs = {"mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi"};
        int[] sizes = {48, 72, 96, 144, 192};
        for (int i = 0; i < dirs.length; i++) {
            File d = new File(res, dirs[i]);
            d.mkdirs();
            ImageIO.write(shrink(img, BIG, sizes[i]), "png", new File(d, "ic_launcher.png"));
        }
        File big = new File(args.length > 1 ? args[1] : "out/icon");
        big.mkdirs();
        ImageIO.write(shrink(img, BIG, 512), "png", new File(big, "icon-512.png"));
    }

    /** Premultiplied RGBA, 0..1, row by row. */
    private static float[][] paint(int n) {
        float[][] o = new float[n * n][];
        // the hole and the disk, in icon units (0..1 across)
        final float hx = 0.5f, hy = 0.40f, rs = 0.095f;
        final float din = 0.13f, dout = 0.30f, flat = 0.22f, turn = -0.16f;
        float ct = (float) Math.cos(turn), st = (float) Math.sin(turn);
        float px = 1f / n;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                float x = (i + 0.5f) * px, y = (j + 0.5f) * px;
                float shape = roundRect(x, y, 0.5f, 0.5f, 0.5f, 0.5f, 0.2f, px);
                if (shape <= 0f) {
                    o[j * n + i] = new float[]{0, 0, 0, 0};
                    continue;
                }
                // space
                float vx = x - hx, vy = y - hy;
                float r = (float) Math.sqrt(vx * vx + vy * vy);
                float[] c = mix(rgb(0x0A0C22), rgb(0x020309), Col.clamp01(r * 1.3f));
                float neb = Sky.fbm(x * 3.2f + 4f, y * 3.2f + 9f);
                c = add(c, rgb(0x3A1670), Col.ramp(0.5f, 0.85f, neb) * 0.45f);
                float sh = Col.hash(i / 6, j / 6);
                if (sh > 0.985f) c = add(c, rgb(0xEEF2FF), (sh - 0.985f) / 0.015f * 0.9f * (1f - Col.ramp(0.6f, 0.4f, y) * 0f));
                // the table, in perspective, its grid bent toward the hole
                float t = (y - 0.50f) / 0.52f;                // 0 at the far edge, 1 at the bottom
                if (t > 0f) {
                    float half = 0.30f + 0.32f * t;
                    float u = (x - 0.5f) / half;              // -1..1 across
                    float edgeW = px * 1.5f / half;
                    float inside = Col.clamp01((1f - Math.abs(u)) / edgeW + 0.5f) * Col.clamp01((y - 0.50f) / px + 0.5f);
                    if (inside > 0f) {
                        float depth = 1f / (0.35f + t);       // farther rows are closer together
                        float gx = u * 3f, gz = depth * 3f;
                        float dx = (x - hx), dz = (y - hy) * 1.6f;
                        float bend = 0.035f / (dx * dx + dz * dz + 0.02f);
                        gx -= dx * bend * 3f / half;
                        gz -= dz * bend * 2f;
                        float fx = Math.abs(gx - Math.round(gx)), fz = Math.abs(gz - Math.round(gz));
                        float line = Math.max(Col.clamp01((0.06f - fx) / 0.03f), Col.clamp01((0.05f - fz * (0.6f + t)) / 0.03f));
                        float[] top = mix(rgb(0x081634), rgb(0x12306A), 1f - Math.abs(u) * 0.6f);
                        top = add(top, rgb(0x2A64C0), line * 0.75f);
                        float rim = Col.clamp01((Math.abs(u) - (1f - 0.05f)) / 0.03f);
                        float far = Col.clamp01((0.515f - y) / 0.012f + 1f);
                        top = mix(top, rgb(0x63E0FF), Math.max(rim, far));
                        // warm light from the disk on the table
                        float lt = 0.006f / (vx * vx + vy * vy * 0.6f + 0.004f);
                        top = add(top, rgb(0xFF9A48), Math.min(0.9f, lt * 0.35f));
                        c = mix(c, top, inside);
                    }
                }
                // bent light near the hole: a faint ring
                float einstein = (r - rs * 1.9f) / 0.012f;
                c = add(c, rgb(0x9FB8FF), (float) Math.exp(-einstein * einstein) * 0.12f);
                // the far side of the disk, bent over the top of the shadow
                if (r > rs) {
                    float q = (r - rs * 1.07f) / ((dout - din) * 0.3f);
                    if (q > 0f && q < 1f) {
                        float topk = Col.clamp01(-vy / r);
                        c = add(c, heat(q * 0.7f), (float) Math.sin(q * Math.PI) * (0.1f + 0.9f * topk * topk) * 1.2f);
                    }
                }
                c = add(c, rgb(0xFF7A30), 0.10f * (float) Math.exp(-r * r / 0.03f));
                // the shadow
                float black = Col.clamp01((rs - r) / px + 0.5f);
                c = mix(c, new float[]{0, 0, 0}, black);
                // the disk itself; its near side crosses in front of the shadow
                float du = vx * ct + vy * st, dv = -vx * st + vy * ct;
                float vv = dv / flat;
                float dd = (float) Math.sqrt(du * du + vv * vv);
                if (dd > din * 0.9f && dd < dout) {
                    float k = (dd - din) / (dout - din);
                    float edge = Col.ramp(din * 0.9f, din * 1.03f, dd) * Col.ramp(dout, dout * 0.65f, dd);
                    float doppler = 1f - 0.5f * du / dd;
                    float ang = (float) Math.atan2(vv, du);
                    float lanes = 0.8f + 0.2f * (float) Math.sin(k * 30f + 1.5f * Math.sin(ang * 2f));
                    float e = edge * (1.6f - 1.2f * Col.clamp01(k)) * doppler * lanes;
                    if (dv < 0f) e *= 1f - black;
                    c = add(c, heat(k), e);
                }
                // the photon ring
                float pr = (r - rs * 1.03f) / (px * 2.2f);
                if (r > rs * 0.9f) c = add(c, rgb(0xFFEED2), (float) Math.exp(-pr * pr) * 0.9f);
                // the ball's tail: an arc curving round the hole
                float tail = arc(x, y, px);
                c = add(c, rgb(0x7FE8FF), tail);
                // the ball
                float bx = 0.5f + 0.37f * (float) Math.cos(0.8f) * 1.05f, by = 0.40f + 0.37f * (float) Math.sin(0.8f) * 0.95f, br = 0.058f;
                float gx = x - bx, gy = y - by;
                float bd = (float) Math.sqrt(gx * gx + gy * gy);
                c = add(c, rgb(0x9FE8FF), 0.35f * (float) Math.exp(-bd * bd / (br * br * 3f)));
                float bc = Col.clamp01((br - bd) / px + 0.5f);
                if (bc > 0f) {
                    float u = gx / br, v = gy / br;
                    float nz = (float) Math.sqrt(Math.max(0f, 1f - u * u - v * v));
                    float diff = Col.clamp01(-0.45f * u - 0.55f * v + 0.7f * nz);
                    float[] ball = mix(rgb(0x55607E), rgb(0xF8F5EE), 0.25f + 0.75f * diff);
                    float rim = Col.clamp01(-(u * 0.6f + v * -0.8f)) * (1f - nz);
                    ball = add(ball, rgb(0xFF9A48), Col.clamp01(-u * 0.7f - v * 0.7f) * (1f - nz) * 0.9f + rim * 0f);
                    float spec = Col.clamp01(1f - ((u + 0.35f) * (u + 0.35f) + (v + 0.4f) * (v + 0.4f)) * 9f);
                    ball = add(ball, rgb(0xFFFFFF), spec * 0.8f);
                    c = mix(c, ball, bc);
                }
                // a hairline of light round the edge of the icon
                float inner = roundRect(x, y, 0.5f, 0.5f, 0.5f - px * 3f, 0.5f - px * 3f, 0.2f - px * 3f, px);
                c = add(c, rgb(0x3A4E80), (shape - inner) * 0.6f);
                o[j * n + i] = new float[]{c[0] * shape, c[1] * shape, c[2] * shape, shape};
            }
        return o;
    }

    /** The tail: a spiral arm from the lower left, round under the hole, to the ball. */
    private static float arc(float x, float y, float px) {
        float best = 9f, at = 0f;
        float lx = 0f, ly = 0f;
        for (int s = 0; s <= 60; s++) {
            float k = s / 60f;
            float ang = 2.75f - 1.95f * k;                 // sweeping round under the hole
            float rad = 0.40f - 0.03f * k;
            float ax = 0.5f + rad * (float) Math.cos(ang) * 1.05f;
            float ay = 0.40f + rad * (float) Math.sin(ang) * 0.95f;
            if (s > 0) {
                float d = segDist(x, y, lx, ly, ax, ay);
                if (d < best) {
                    best = d;
                    at = k;
                }
            }
            lx = ax;
            ly = ay;
        }
        float width = (0.006f + 0.026f * at * at);
        float cov = Col.clamp01((width - best) / px + 0.5f);
        return cov * (0.15f + 0.6f * at * at);
    }

    private static float segDist(float x, float y, float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0;
        float t = Col.clamp01(((x - x0) * dx + (y - y0) * dy) / (dx * dx + dy * dy));
        float ex = x - x0 - dx * t, ey = y - y0 - dy * t;
        return (float) Math.sqrt(ex * ex + ey * ey);
    }

    /** Coverage of a rounded rectangle with soft edges. */
    private static float roundRect(float x, float y, float cx, float cy, float hw, float hh, float rad, float px) {
        float qx = Math.abs(x - cx) - (hw - rad), qy = Math.abs(y - cy) - (hh - rad);
        float ox = Math.max(qx, 0f), oy = Math.max(qy, 0f);
        float d = (float) Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0f) - rad;
        return Col.clamp01(-d / px + 0.5f);
    }

    private static float[] heat(float k) {
        k = Col.clamp01(k);
        if (k < 0.35f) return mix(rgb(0xFFF6E0), rgb(0xFFC060), k / 0.35f);
        return mix(rgb(0xFFC060), rgb(0xD8461E), (k - 0.35f) / 0.65f);
    }

    private static float[] rgb(int c) {
        return new float[]{((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    private static float[] add(float[] a, float[] b, float k) {
        return new float[]{Math.min(1f, a[0] + b[0] * k), Math.min(1f, a[1] + b[1] * k), Math.min(1f, a[2] + b[2] * k)};
    }

    /** Averages blocks of the big picture down to the target size. */
    private static BufferedImage shrink(float[][] img, int n, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        float k = n / (float) size;
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                int x0 = (int) (x * k), x1 = (int) ((x + 1) * k), y0 = (int) (y * k), y1 = (int) ((y + 1) * k);
                float r = 0, g = 0, b = 0, a = 0;
                int cnt = 0;
                for (int j = y0; j < y1; j++)
                    for (int i = x0; i < x1; i++) {
                        float[] p = img[j * n + i];
                        r += p[0];
                        g += p[1];
                        b += p[2];
                        a += p[3];
                        cnt++;
                    }
                r /= cnt;
                g /= cnt;
                b /= cnt;
                a /= cnt;
                int ia = Math.round(a * 255);
                int ir = a > 0 ? Math.round(Math.min(1f, r / a) * 255) : 0;
                int ig = a > 0 ? Math.round(Math.min(1f, g / a) * 255) : 0;
                int ib = a > 0 ? Math.round(Math.min(1f, b / a) * 255) : 0;
                out.setRGB(x, y, (ia << 24) | (ir << 16) | (ig << 8) | ib);
            }
        return out;
    }
}
