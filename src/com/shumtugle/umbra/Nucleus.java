package com.shumtugle.umbra;

/**
 * A laboratory of the early twentieth century: board floor, a lamp overhead, a
 * blackboard at the far wall, and over the net an atomic nucleus that pushes the
 * ball away, harder the closer it passes; a ball that comes straight at it may be
 * thrown back the way it came. The same table as the hole, the opposite law.
 *
 * Its signs: the grid on the table is pressed into a dense knot under the nucleus
 * instead of falling into a funnel, chalk arrows drift outward around it, the
 * ball leaves a trail of mist that shows its bent path, and the blackboard holds
 * the old drawing of particles scattered by a nucleus.
 */
final class Nucleus extends Realm {
    static final float STRENGTH = 0.55f;
    static final float REACH2 = 1.0f;
    static final float SOFT = 0.02f;
    static final float Y = 0.32f;
    static final float FLOOR = -0.76f, WALL = 3.9f;
    static final int CHALK = 0xFFEDEBDD;

    @Override
    String key() {
        return "nucleus";
    }

    @Override
    String name() {
        return "THE NUCLEUS";
    }

    @Override
    String note() {
        return "1911  IT PUSHES THE BALL AWAY";
    }

    @Override
    String hint() {
        return "THE NUCLEUS PUSHES THE BALL AWAY";
    }

    @Override
    float height() {
        return Y;
    }

    @Override
    float posX(float t) {
        return 0.5f * (float) Math.sin(t * 0.38f + 2.2f);
    }

    @Override
    float posZ(float t) {
        return Game.NZ + 0.28f * (float) Math.sin(t * 0.26f + 0.4f);
    }

    @Override
    boolean act(Ball b, float t, float h) {
        float dx = b.x - posX(t), dy = b.y - Y, dz = b.z - posZ(t);
        float r2 = dx * dx + dy * dy + dz * dz;
        if (r2 > REACH2) return false;
        float a = STRENGTH * power / (r2 + SOFT) * h / (float) Math.sqrt(r2 + 1e-4f);
        b.vx += dx * a;
        b.vy += dy * a;
        b.vz += dz * a;
        return false;
    }

    // ---- the room

    /**
     * The floor, the wall and the blackboard, each found by casting the ray of the
     * pixel: the floor where the ray comes down before the wall, the wall otherwise.
     */
    @Override
    void backdrop(Scene s, int[] out) {
        Cam cam = s.cam;
        int w = s.w, h = s.h;
        float inv = 1f / cam.f;
        for (int sy = 0; sy < h; sy++) {
            float dcy = -(sy + 0.5f - cam.cy) * inv;
            float dirY = dcy * cam.cs - cam.sn, dirZ = dcy * cam.sn + cam.cs;
            float tF = dirY < -1e-4f ? (FLOOR - cam.y) / dirY : 1e9f;
            float zF = cam.z + tF * dirZ;
            float dcy2 = -(sy + 1.5f - cam.cy) * inv;
            float zF2 = cam.z + (FLOOR - cam.y) / (dcy2 * cam.cs - cam.sn) * (dcy2 * cam.sn + cam.cs);
            float footZ = Math.abs(zF - zF2) + 1e-5f, footX = tF * inv;
            float tW = dirZ > 1e-4f ? (WALL - cam.z) / dirZ : 1e9f;
            float yW = cam.y + tW * dirY;
            float footW = tW * inv;
            for (int sx = 0; sx < w; sx++) {
                float dx = (sx + 0.5f - cam.cx) * inv;
                int c;
                if (zF < WALL) c = floor(tF * dx, zF, footX, footZ);
                else c = wall(tW * dx, yW, footW);
                out[sy * w + sx] = c;
            }
        }
        chalkDrawings(s, out);
    }

    /** Oak boards running down the room, each its own shade, under a warm lamp. */
    private static int floor(float x, float z, float footX, float footZ) {
        final float pw = 0.16f, seg = 1.4f;
        float u = x / pw + 40f;
        int ix = (int) u;
        float ex = Math.min(u - ix, ix + 1 - u) * pw;              // distance to the board's long edge
        float zz = z + Col.hash(ix, 7) * seg + 20f;
        int iz = (int) (zz / seg);
        float ez = Math.min(zz - iz * seg, (iz + 1) * seg - zz);   // distance to the board's end
        float tone = Col.hash(ix, iz);
        int c = Col.mix(0xFF3A2414, 0xFF60401F, tone);
        float grain = (float) Math.sin(x * 140f + 3f * (float) Math.sin(z * 2.3f + ix) + tone * 9f);
        c = Col.mul(c, 0.92f + 0.08f * grain);
        float gap = Math.max(Col.clamp01((0.005f - ex) / footX + 0.5f), Col.clamp01((0.004f - ez) / footZ + 0.5f));
        c = Col.mix(c, 0xFF120A05, gap * 0.85f);
        // the lamp over the table, and the table's own shadow on the floor under it
        float lx = x, lz = z - Game.NZ;
        float lamp = 1f / (1f + (lx * lx + lz * lz) / 2.2f);
        float under = Col.ramp(0.35f, -0.05f, Math.max(Math.abs(x) - Game.HW, Math.max(-z, z - Game.L)));
        c = Col.mul(c, (0.25f + 0.85f * lamp) * (1f - 0.55f * under));
        return c;
    }

    /** The far wall: plaster above dark panelling, and the blackboard on its stand. */
    private static int wall(float x, float y, float foot) {
        float lamp = 1f / (1f + (x * x) / 3f);
        int c;
        if (y < -0.48f) {
            float u = x / 0.45f + 40f;
            float e = Math.min(u - (int) u, (int) u + 1 - u) * 0.45f;
            c = Col.mix(0xFF2A1A0E, 0xFF3C2614, 0.5f + 0.5f * Col.hash((int) u, 3));
            c = Col.mix(c, 0xFF140C06, Col.clamp01((0.008f - e) / foot + 0.5f));
        } else {
            c = Col.mix(0xFF2C3228, 0xFF353C30, Sky.fbm(x * 3f + 5f, y * 3f));
        }
        // the blackboard: a wooden frame round dark slate, with a chalk ledge at the bottom
        float bx = Math.abs(x), half = 1.25f, low = -0.40f;
        if (bx < half + 0.05f && y > low - 0.05f) {
            boolean frame = bx > half || y < low;
            if (frame) c = Col.mix(0xFF5A3A1C, 0xFF8A6034, 0.5f + 0.5f * (float) Math.sin(x * 60f + y * 9f));
            else {
                c = Col.mix(0xFF1A2620, 0xFF22302A, Sky.fbm(x * 4f + 1f, y * 4f + 2f));
                // the ghosts of old lessons, wiped but not quite
                c = Col.mix(c, 0xFF56645C, 0.10f * Col.ramp(0.55f, 0.8f, Sky.fbm(x * 2.5f + 9f, y * 5f)));
            }
            if (y > low - 0.05f && y < low - 0.02f) c = 0xFF2A1A0E;
        }
        return Col.mul(c, 0.55f + 0.5f * lamp);
    }

    /** On the board: particles scattered by a nucleus, and an atom with its orbits, in chalk. */
    private void chalkDrawings(Scene s, int[] out) {
        Px f = new Px(s.w, s.h, out);
        Cam cam = s.cam;
        // the scattering: parallel paths from the left, each bent away from the dot
        float nx = 0.55f, ny = -0.05f;
        for (int k = -3; k <= 3; k++) {
            float px = -0.25f, py = ny + k * 0.085f, vx = 1f, vy = 0f;
            float lx = 0f, ly = 0f;
            boolean have = false;
            for (int i = 0; i < 260; i++) {
                float dx = px - nx, dy = py - ny;
                float r2 = dx * dx + dy * dy + 1e-4f;
                float a = 0.0045f / (r2 * (float) Math.sqrt(r2));
                vx += dx * a * 0.01f;
                vy += dy * a * 0.01f;
                float sp = (float) Math.sqrt(vx * vx + vy * vy);
                px += vx / sp * 0.008f;
                py += vy / sp * 0.008f;
                if (px > 1.2f || py > 0.45f || py < -0.38f || px < -0.3f) break;
                if (!cam.project(px, py, WALL)) break;
                if (have) f.lineAA(lx, ly, cam.sx, cam.sy, 0.9f, CHALK, 0.45f + 0.25f * Col.hash(i / 4, k));
                lx = cam.sx;
                ly = cam.sy;
                have = true;
            }
        }
        if (cam.project(nx, ny, WALL)) f.discAA(cam.sx, cam.sy, 1.6f, CHALK, 0.9f);
        // the atom: a nucleus and three tilted orbits
        float ax = -0.75f, ay = 0.0f;
        for (int o = 0; o < 3; o++) {
            float ang = o * 1.047f;
            float lx = 0f, ly = 0f;
            for (int i = 0; i <= 48; i++) {
                float th = i / 48f * 6.2832f;
                float u = 0.30f * (float) Math.cos(th), v = 0.11f * (float) Math.sin(th);
                float wx = ax + u * (float) Math.cos(ang) - v * (float) Math.sin(ang);
                float wy = ay + u * (float) Math.sin(ang) + v * (float) Math.cos(ang);
                if (!cam.project(wx, wy, WALL)) continue;
                if (i > 0) f.lineAA(lx, ly, cam.sx, cam.sy, 0.9f, CHALK, 0.6f);
                lx = cam.sx;
                ly = cam.sy;
            }
        }
        if (cam.project(ax, ay, WALL)) f.discAA(cam.sx, cam.sy, 2f, CHALK, 0.85f);
        // an underline and a few strokes of a formula nobody can read from here
        for (int k = 0; k < 9; k++) {
            float x0 = -0.2f + k * 0.11f, y0 = 0.32f + 0.02f * Col.hash(k, 5);
            if (!cam.project(x0, y0, WALL)) continue;
            float ax0 = cam.sx, ay0 = cam.sy;
            if (!cam.project(x0 + 0.07f, y0 - 0.03f + 0.06f * Col.hash(k, 9), WALL)) continue;
            f.lineAA(ax0, ay0, cam.sx, cam.sy, 0.9f, CHALK, 0.5f);
        }
    }

    // ---- the table and the rest

    @Override
    int surface(float x, float z) {
        float lit = (float) Math.exp(-(x * x / 1.4f + (z - 1.37f) * (z - 1.37f) / 3.2f));
        return Col.mix(0xFF07241A, 0xFF135A3C, 0.3f + 0.7f * lit);
    }

    @Override
    int lines() {
        return CHALK;
    }

    @Override
    int grid() {
        return 0xFF3C7458;
    }

    @Override
    float gridGain() {
        return 0.6f;
    }

    // pushed outward: lines gather in a knot under the nucleus, tighter at every level
    @Override
    float bendA() {
        return -0.9f * bendC();
    }

    @Override
    float bendC() {
        return 0.3f + 0.2f * power;
    }

    @Override
    int light() {
        return 0xFFFFD27A;
    }

    @Override
    float lightK() {
        return 0.006f;
    }

    @Override
    float gloss() {
        return 0.10f;
    }

    @Override
    int faceTop() {
        return 0xFF6A4424;
    }

    @Override
    int faceBottom() {
        return 0xFF24150A;
    }

    @Override
    int bevel() {
        return 0xFFE2B45A;
    }

    @Override
    int tail() {
        return 0xFFE6EEF0;
    }

    @Override
    float tailWidth() {
        return 2.2f;
    }

    @Override
    float tailAlpha() {
        return 0.30f;
    }

    @Override
    int netThread() {
        return 0xFFE8E0C8;
    }

    @Override
    int netTape() {
        return 0xFFF4EEDC;
    }

    @Override
    int ripple() {
        return CHALK;
    }

    @Override
    int rimLight() {
        return 0xFFFFD27A;
    }

    @Override
    int hum() {
        return Synth.HUM_MAINS;
    }

    // the early bats: a face of cork or pimpled rubber on a plain board, a turned handle
    private static final Racket MINE = new Racket(0xFFB8402E, 0xFFE88A6A, 0xFF5A1A10, 0xFF5A3A1C, 0xFFC89858,
            0xFF7A4E28, 0xFFC8955A, 0xFFE2B45A, 0, 0.22f);
    private static final Racket THEIRS = new Racket(0xFF3A5A40, 0xFF8AB890, 0xFF102018, 0xFF5A3A1C, 0xFFC89858,
            0xFF7A4E28, 0xFFC8955A, 0xFFE2B45A, 0xFFEDEBDD, 0.22f);

    @Override
    Racket racket(boolean player) {
        return player ? MINE : THEIRS;
    }

    /** Chalk arrows round the nucleus on the table, drifting outward. */
    @Override
    void drawOnTable(Scene s, Px f, Game g, float t) {
        Cam cam = s.cam;
        float drift = (t * 0.5f) % 1f;
        for (int k = 0; k < 10; k++) {
            float ang = k * 0.6283f + 0.3f;
            float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
            float r0 = 0.20f + 0.16f * drift, r1 = r0 + 0.07f;
            float x0 = x + ca * r0, z0 = z + sa * r0, x1 = x + ca * r1, z1 = z + sa * r1;
            if (!Ball.onTable(x0, z0) || !Ball.onTable(x1, z1)) continue;
            float op = 0.55f * (float) Math.sin(drift * Math.PI);
            if (!cam.project(x0, 0f, z0)) continue;
            float ax = cam.sx, ay = cam.sy;
            if (!cam.project(x1, 0f, z1)) continue;
            float bx = cam.sx, by = cam.sy;
            f.lineAA(ax, ay, bx, by, 1f, CHALK, op);
            // the head: two short strokes back from the tip
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                float hx = x1 - (ca * 0.03f) + (-sa * 0.02f * sgn), hz = z1 - (sa * 0.03f) + (ca * 0.02f * sgn);
                if (cam.project(hx, 0f, hz)) f.lineAA(bx, by, cam.sx, cam.sy, 1f, CHALK, op);
            }
        }
    }

    /** The nucleus, a knot of protons and neutrons, with three electrons on their orbits. */
    @Override
    void drawCenter(Scene s, Px f, Game g, float t) {
        Cam cam = s.cam;
        if (!cam.project(x, Y, z)) return;
        float cx = cam.sx, cy = cam.sy, sc = cam.scale();
        float near = g.ball.gone ? 0f : closeness(g.ball);
        f.glow(cx, cy, 0.22f * sc, 0xFFFFC870, 0.16f + 0.35f * near * near);
        // the orbits: three circles in tilted planes, seen in perspective
        float[] ex = new float[3], ey = new float[3];
        for (int o = 0; o < 3; o++) {
            float tilt = 1.15f, turn = o * 2.094f;
            float ct = (float) Math.cos(tilt), st = (float) Math.sin(tilt), cr = (float) Math.cos(turn), sr = (float) Math.sin(turn);
            float lx = 0f, ly = 0f;
            for (int i = 0; i <= 40; i++) {
                float th = i / 40f * 6.2832f;
                float[] p = orbit(th, ct, st, cr, sr);
                if (!cam.project(x + p[0], Y + p[1], z + p[2])) continue;
                if (i > 0) f.lineAA(lx, ly, cam.sx, cam.sy, 0.9f, 0xFF9FD8FF, 0.35f);
                lx = cam.sx;
                ly = cam.sy;
            }
            float[] e = orbit(t * (2.4f + 0.5f * o) + o * 2f, ct, st, cr, sr);
            cam.project(x + e[0], Y + e[1], z + e[2]);
            ex[o] = cam.sx;
            ey[o] = cam.sy;
        }
        // the knot: seven particles, red protons and pale neutrons, lit from above
        float pr = Math.max(1.8f, 0.03f * sc), ring = pr * 1.15f;
        float jit = near * near * 0.6f;
        for (int k = 0; k < 7; k++) {
            float px = cx, py = cy;
            if (k > 0) {
                float a = (k - 1) * 1.0472f + 0.4f;
                px += (float) Math.cos(a) * ring;
                py += (float) Math.sin(a) * ring * 0.9f;
            }
            px += (Col.hash(k, (int) (t * 30f)) - 0.5f) * jit;
            py += (Col.hash(k + 9, (int) (t * 30f)) - 0.5f) * jit;
            particle(f, px, py, pr, k % 2 == 0 ? 0xFFD8503A : 0xFFE8E2D0);
        }
        for (int o = 0; o < 3; o++) {
            f.glow(ex[o], ey[o], 3.5f, 0xFF9FD8FF, 0.6f);
            f.discAA(ex[o], ey[o], 1.1f, 0xFFF0FAFF, 1f);
        }
        // sparks when the ball is close
        if (near > 0.55f) {
            float k = (near - 0.55f) / 0.45f;
            for (int i = 0; i < 4; i++) {
                float a = Col.hash(i, (int) (t * 24f)) * 6.2832f, len = (0.10f + 0.12f * Col.hash(i + 4, (int) (t * 24f))) * sc;
                f.lineAA(cx, cy, cx + (float) Math.cos(a) * len, cy + (float) Math.sin(a) * len * 0.8f, 1f, 0xFFFFF0C0, 0.6f * k);
            }
        }
    }

    private static float[] orbit(float th, float ct, float st, float cr, float sr) {
        float r = 0.15f;
        float u = r * (float) Math.cos(th), v = r * (float) Math.sin(th);
        // a circle in the x-z plane, tipped about x, then turned about y
        float px = u, py = v * st, pz = v * ct;
        return new float[]{px * cr + pz * sr, py, -px * sr + pz * cr};
    }

    /** A small lit sphere with a soft edge. */
    private static void particle(Px f, float cx, float cy, float r, int base) {
        int x0 = (int) Math.floor(cx - r - 1), x1 = (int) Math.ceil(cx + r + 1);
        int y0 = (int) Math.floor(cy - r - 1), y1 = (int) Math.ceil(cy + r + 1);
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                float u = (x + 0.5f - cx) / r, v = (y + 0.5f - cy) / r;
                float d2 = u * u + v * v;
                float cov = (1f - (float) Math.sqrt(d2)) * r + 0.5f;
                if (cov <= 0f) continue;
                float nz = (float) Math.sqrt(Math.max(0f, 1f - d2));
                float lit = Col.clamp01(-0.4f * u - 0.6f * v + 0.7f * nz);
                int c = Col.mix(Col.mul(base, 0.35f), base, 0.3f + 0.7f * lit);
                float spec = Col.clamp01(1f - ((u + 0.35f) * (u + 0.35f) + (v + 0.4f) * (v + 0.4f)) * 7f);
                c = Col.addScaled(c, 0xFFFFFFFF, spec * 0.6f);
                f.blend(x, y, c, Math.min(1f, cov));
            }
    }
}
