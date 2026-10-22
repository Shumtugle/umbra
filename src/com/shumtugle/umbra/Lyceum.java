package com.shumtugle.umbra;

/**
 * The physics of the ancients, played as if it were true. A struck ball is given
 * an impetus, a push of its own: it flies in a straight line, as violent motion
 * does, for as long as the impetus lasts, and then, spent, it falls straight down
 * to its natural place. The harder the swing, the faster and flatter the line.
 *
 * Everything is in plain sight: from the moment of the strike a line runs from the
 * ball to where its impetus will end, a plumb line drops from there, and a ring
 * marks the place on the table. A ring round the ball shrinks as the impetus is
 * spent. Under the table lies a mosaic of the cosmos as it was then understood:
 * the earth at the centre, the elements round it, the spheres of the planets, the
 * fixed stars.
 */
final class Lyceum extends Realm {
    static final float DECK = -0.76f;
    static final int BLACK = 0xFF1A1210, CREAM = 0xFFF0E2C4;

    @Override
    String key() {
        return "lyceum";
    }

    @Override
    String name() {
        return "THE LYCEUM";
    }

    @Override
    String note() {
        return "335 BC  A BALL FLIES STRAIGHT, THEN FALLS";
    }

    @Override
    String hint() {
        return "IT FLIES STRAIGHT, THEN FALLS TO ITS PLACE";
    }

    @Override
    float height() {
        return 0.6f;
    }

    @Override
    float posX(float t) {
        return 0f;
    }

    @Override
    float posZ(float t) {
        return Game.NZ;
    }

    @Override
    float closeness(Ball b) {
        return 0f;
    }

    /**
     * The straight line: from the ball to a point above the aim, high enough that
     * the line crosses the net at the height the hitter meant. The place is kept
     * where the other paddle can reach it.
     */
    @Override
    boolean launch(Ball b, int side, float tx, float tz, float yn, float power) {
        float dir = side == 0 ? 1f : -1f;
        float depth = (tz - Game.NZ) * dir;
        float lo = Game.NZ - Game.FRONT + 0.15f, hi = Game.L - Game.NZ - 0.1f;
        depth = Math.max(lo, Math.min(hi, depth));
        tz = Game.NZ + dir * depth;
        float s = (Game.NZ - b.z) / (tz - b.z);
        float top = s > 0.05f ? b.y + (yn - b.y) / s : yn;
        top = Math.max(0.14f, Math.min(0.9f, top));
        float time = 0.62f - 0.3f * power;
        b.vx = (tx - b.x) / time;
        b.vy = (top - b.y) / time;
        b.vz = (tz - b.z) / time;
        b.impetus = b.impetus0 = time;
        b.aimX = tx;
        b.aimY = top;
        b.aimZ = tz;
        return true;
    }

    /** While the impetus lasts, nothing pulls the ball down; when it is spent, it stops and falls. */
    @Override
    boolean act(Ball b, float t, float h) {
        if (b.impetus > 0f) {
            b.vy += Ball.G * h;
            b.impetus -= h;
            if (b.impetus <= 0f) {
                b.impetus = 0f;
                b.vx = 0f;
                b.vz = 0f;
                b.vy = 0f;
            }
        } else if (b.impetus0 > 0f) {
            // spent: whatever sideways motion is left dies away at once
            b.vx *= 1f - Math.min(1f, h * 12f);
            b.vz *= 1f - Math.min(1f, h * 12f);
        }
        return false;
    }

    @Override
    void drawGuide(Scene s, Px f, Game g) {
        Ball b = g.ball;
        if (b.gone || b.impetus0 <= 0f || g.state == Game.SERVE || g.state == Game.OVER) return;
        Cam cam = s.cam;
        // the place: a ring on the table where the ball will fall
        if (cam.project(b.aimX, 0f, b.aimZ)) {
            float cx = cam.sx, cy = cam.sy, sc = cam.scale();
            f.ringAA(cx, cy, 0.07f * sc, 0.07f * sc * 0.62f, 1.2f, CREAM, 0.7f);
            f.discAA(cx, cy, 1.2f, CREAM, 0.8f);
        }
        float px = b.impetus > 0f ? b.aimX : b.x, py = b.impetus > 0f ? b.aimY : b.y, pz = b.impetus > 0f ? b.aimZ : b.z;
        // the straight line still to fly
        if (b.impetus > 0f && cam.project(b.x, b.y, b.z)) {
            float ax = cam.sx, ay = cam.sy;
            if (cam.project(b.aimX, b.aimY, b.aimZ)) dashed(f, ax, ay, cam.sx, cam.sy, 0xFFFFD27A, 0.55f);
        }
        // the plumb line down to the place
        if (cam.project(px, py, pz)) {
            float ax = cam.sx, ay = cam.sy;
            if (cam.project(px, 0f, pz)) dashed(f, ax, ay, cam.sx, cam.sy, CREAM, 0.45f);
        }
        // the impetus itself: a ring round the ball that shrinks as it is spent
        if (b.impetus > 0f && cam.project(b.x, b.y, b.z)) {
            float k = b.impetus / b.impetus0, r = Ball.R * cam.scale();
            f.ringAA(cam.sx, cam.sy, r * (1.4f + 1.6f * k), r * (1.4f + 1.6f * k), 1.3f, 0xFFFFD27A, 0.35f + 0.5f * k);
        }
    }

    private static void dashed(Px f, float x0, float y0, float x1, float y1, int c, float op) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        int n = Math.max(1, (int) (len / 4f));
        for (int i = 0; i < n; i += 2) {
            float a = i / (float) n, e = Math.min(1f, (i + 1) / (float) n);
            f.lineAA(x0 + dx * a, y0 + dy * a, x0 + dx * e, y0 + dy * e, 1.1f, c, op);
        }
    }

    // ---- the courtyard

    @Override
    void backdrop(Scene s, int[] out) {
        Cam cam = s.cam;
        int w = s.w, h = s.h;
        float inv = 1f / cam.f;
        for (int sy = 0; sy < h; sy++) {
            float dcy = -(sy + 0.5f - cam.cy) * inv;
            float dirY = dcy * cam.cs - cam.sn, dirZ = dcy * cam.sn + cam.cs;
            float t = (DECK - cam.y) / dirY;
            float z = cam.z + t * dirZ;
            float foot = t * inv;
            for (int sx = 0; sx < w; sx++) {
                float x = t * (sx + 0.5f - cam.cx) * inv;
                out[sy * w + sx] = mosaic(x, z - Game.NZ, foot);
            }
        }
    }

    /** The cosmos in stone: earth, water, air, fire, seven planetary spheres, the fixed stars, then marble. */
    private static int mosaic(float u, float v, float foot) {
        float r = (float) Math.sqrt(u * u + v * v);
        float ang = (float) Math.atan2(v, u);
        // tesserae: small stones, each a little different
        final float tile = 0.055f;
        int ti = (int) Math.floor(u / tile), tj = (int) Math.floor(v / tile);
        float jit = Col.hash(ti, tj) - 0.5f;
        int c;
        if (r < 0.55f) {
            float land = Sky.fbm(u * 3f + 4f, v * 3f + 1f);
            c = land > 0.52f ? Col.mix(0xFF5A6A30, 0xFF8A6A3A, Col.ramp(0.52f, 0.7f, land)) : 0xFF2A5A8A;
        } else if (r < 0.75f) {
            c = 0xFF2C6A9A;                                 // water
        } else if (r < 0.95f) {
            c = 0xFFA8C8DA;                                 // air
        } else if (r < 1.15f) {
            c = Col.mix(0xFFD8562A, 0xFFF0A040, 0.5f + 0.5f * (float) Math.sin(ang * 14f));   // fire
        } else if (r < 3.25f) {
            int k = (int) ((r - 1.15f) / 0.3f);
            c = k % 2 == 0 ? 0xFF1C2E5A : 0xFF233668;
            float edge = Math.abs((r - 1.15f) - (k + 1) * 0.3f);
            edge = Math.min(edge, Math.abs((r - 1.15f) - k * 0.3f));
            c = Col.mix(c, 0xFFD8B050, Col.clamp01((0.015f - edge) / foot + 0.5f));
            // the planet riding its sphere
            int[] planet = {0xFFD8D8D0, 0xFFA8A8A0, 0xFFF0E2B0, 0xFFFFD040, 0xFFC8482A, 0xFFD8A870, 0xFFE8D090};
            float pa = 0.7f + k * 2.3f, pr = 1.3f + k * 0.3f;
            float ddx = u - pr * (float) Math.cos(pa), ddy = v - pr * (float) Math.sin(pa);
            float size = k == 3 ? 0.11f : 0.065f;
            float d = (float) Math.sqrt(ddx * ddx + ddy * ddy);
            if (k < 7) c = Col.mix(c, planet[k], Col.clamp01((size - d) / foot + 0.5f));
        } else if (r < 3.6f) {
            c = 0xFF101A38;                                 // the fixed stars
            if (Col.hash(ti + 7, tj + 3) > 0.86f) c = 0xFFE8C860;
        } else {
            // white marble with a key pattern running round
            c = Col.mix(0xFFE8E2D6, 0xFFC8C0B0, Col.ramp(0.45f, 0.8f, Sky.fbm(u * 1.5f + 9f, v * 1.5f + 3f)));
            float q = r - 3.72f;
            if (q > 0f && q < 0.22f) {
                float sArc = r * ang;
                float cu = (sArc / 0.22f) % 1f;
                if (cu < 0f) cu += 1f;
                float cv = q / 0.22f;
                boolean key = cv < 0.14f || cv > 0.86f
                        || (cu < 0.14f && cv > 0.14f)
                        || (cu > 0.14f && cu < 0.72f && cv > 0.58f && cv < 0.72f)
                        || (cu > 0.58f && cu < 0.72f && cv > 0.3f && cv < 0.72f)
                        || (cu > 0.3f && cu < 0.72f && cv > 0.3f && cv < 0.42f);
                if (key) c = BLACK;
            }
        }
        c = Col.mul(c, 0.94f + 0.12f * jit);
        // grout between the stones, faint so it never shimmers
        float gu = Math.abs(u / tile - Math.round(u / tile)) * tile, gv = Math.abs(v / tile - Math.round(v / tile)) * tile;
        float grout = Math.max(Col.clamp01((0.004f - gu) / foot + 0.5f), Col.clamp01((0.004f - gv) / foot + 0.5f));
        if (r < 3.6f) c = Col.mix(c, 0xFF2A2420, grout * 0.35f);
        // the sun, and the table's shade under it
        float under = Col.ramp(0.3f, -0.05f, Math.max(Math.abs(u) - Game.HW, Math.abs(v) - Game.NZ));
        float light = 1f / (1f + (u * u + v * v) / 9f);
        return Col.mul(c, (0.55f + 0.55f * light) * (1f - 0.5f * under));
    }

    // ---- terracotta and black glaze

    @Override
    int surface(float x, float z) {
        float lit = (float) Math.exp(-(x * x / 1.6f + (z - 1.37f) * (z - 1.37f) / 3.4f));
        return Col.mix(0xFF9A4418, 0xFFD87A3C, 0.35f + 0.65f * lit);
    }

    @Override
    int lines() {
        return BLACK;
    }

    @Override
    int grid() {
        return 0xFF3A2410;
    }

    @Override
    float gridGain() {
        return 0.35f;
    }

    @Override
    float bendA() {
        return 0f;
    }

    @Override
    float bendC() {
        return 1f;
    }

    @Override
    int light() {
        return 0xFFFFE0A0;
    }

    @Override
    float lightK() {
        return 0f;
    }

    @Override
    float gloss() {
        return 0f;
    }

    @Override
    int faceTop() {
        return 0xFF2A1A12;
    }

    @Override
    int faceBottom() {
        return 0xFF0E0806;
    }

    @Override
    int bevel() {
        return 0xFFD87A3C;
    }

    @Override
    int tail() {
        return 0xFFFFD27A;
    }

    @Override
    float tailAlpha() {
        return 0.22f;
    }

    @Override
    int netThread() {
        return 0xFFE8DCC0;
    }

    @Override
    int netTape() {
        return CREAM;
    }

    @Override
    int ripple() {
        return CREAM;
    }

    @Override
    int rimLight() {
        return 0xFFFFE0A0;
    }

    @Override
    int hum() {
        return Synth.HUM_CICADAS;
    }

    // boards of olive wood: the player's painted red figure, the opponent's black figure
    private static final Racket MINE = new Racket(0xFFC8482A, 0xFFF09A6A, 0xFF5A1A0C, 0xFF6A5030, 0xFFC8A870,
            0xFF8A6A40, 0xFFD0B080, 0xFF5A4028, 0, 0.12f);
    private static final Racket THEIRS = new Racket(0xFF201410, 0xFF6A5040, 0xFF080404, 0xFFB85A28, 0xFFF09A50,
            0xFF8A6A40, 0xFFD0B080, 0xFF5A4028, 0xFFD87A3C, 0.12f);

    @Override
    Racket racket(boolean player) {
        return player ? MINE : THEIRS;
    }

    @Override
    void drawCenter(Scene s, Px f, Game g, float t) {
        // nothing floats here: the law is in the ball
    }
}
