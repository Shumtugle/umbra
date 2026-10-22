package com.shumtugle.umbra;

/**
 * A station turning in orbit, with a glass deck. Nothing floats over the net:
 * the room itself is the force. The station spins about the middle of the table,
 * so every ball curves as it flies, the same way whichever end it is hit from,
 * and the faster it goes the farther it is carried for the same length of flight.
 * The law has its own sign underfoot: through the glass the stars and the planet
 * wheel round the table at exactly the rate of the spin, and the ball turns the
 * way the sky turns. On the table the grid is twisted the same way, and lanes of
 * light trace the curve a ball takes in each direction.
 */
final class Station extends Realm {
    static final float SPIN = 0.24f;        // turns per second at the first level, in radians
    static final float DECK = -0.76f;
    private static final int TEX = 640;
    private static final float SPAN = 4.6f; // the space texture covers -SPAN..SPAN metres about the axis

    private int[] tex;
    // the glass deck, pixel by pixel: where it looks out, and what of the frame covers it
    private int gn;
    private int[] gIdx, gMetal;
    private float[] gU, gV, gCov, gRefl;
    private int[] base;
    private Scene scene;

    @Override
    String key() {
        return "station";
    }

    @Override
    String name() {
        return "THE STATION";
    }

    @Override
    String note() {
        return "IT TURNS, AND EVERY BALL TURNS WITH IT";
    }

    @Override
    String hint() {
        return "THE BALL CURVES THE WAY THE STARS TURN";
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

    float spin() {
        return SPIN * power;
    }

    /** The turning frame's pull on a moving ball, at right angles to its motion. */
    @Override
    boolean act(Ball b, float t, float h) {
        float k = 2f * spin() * h;
        float vx = b.vx, vz = b.vz;
        b.vx = vx - k * vz;
        b.vz = vz + k * vx;
        return false;
    }

    @Override
    float closeness(Ball b) {
        return 0f;
    }

    // ---- the deck and the sky under it

    @Override
    void backdrop(Scene s, int[] out) {
        scene = s;
        if (tex == null) tex = space();
        Cam cam = s.cam;
        int w = s.w, h = s.h;
        float inv = 1f / cam.f;
        int cap = w * h;
        int[] idx = new int[cap], metal = new int[cap];
        float[] us = new float[cap], vs = new float[cap], cov = new float[cap], refl = new float[cap];
        int n = 0;
        for (int sy = 0; sy < h; sy++) {
            float dcy = -(sy + 0.5f - cam.cy) * inv;
            float dirY = dcy * cam.cs - cam.sn, dirZ = dcy * cam.sn + cam.cs;
            float t = (DECK - cam.y) / dirY;
            float z = cam.z + t * dirZ;
            float foot = t * inv * 1.4f;
            for (int sx = 0; sx < w; sx++) {
                float x = t * (sx + 0.5f - cam.cx) * inv;
                float u = x, v = z - Game.NZ;
                float r = (float) Math.sqrt(u * u + v * v);
                float lamp = 1f / (1f + (u * u + v * v) / 5f);
                // the frame: twelve spokes from the axis, three rings, and a walkway behind each end
                float spokeStep = 0.5236f, ang = (float) Math.atan2(v, u);
                float dAng = ang - Math.round(ang / spokeStep) * spokeStep;
                float spoke = Col.clamp01((0.05f - Math.abs(r * (float) Math.sin(dAng))) / foot + 0.5f) * Col.clamp01((r - 0.9f) / foot);
                float ring = 0f;
                for (int k = 0; k < 3; k++) ring = Math.max(ring, Col.clamp01((0.055f - Math.abs(r - 1.9f - k * 1.05f)) / foot + 0.5f));
                float walk = Math.max(walkway(x, z, -0.95f, -0.25f, foot), walkway(x, z, Game.L + 0.25f, Game.L + 0.95f, foot));
                float m = Math.max(Math.max(spoke, ring), walk);
                int metalC;
                if (walk >= Math.max(spoke, ring)) {
                    // grating: square holes between bars, the bars soft-edged so they never shimmer
                    final float cell = 0.09f, bar = 0.022f;
                    float gx = (x / cell + 100f) % 1f * cell, gz = (z / cell + 100f) % 1f * cell;
                    float bars = Math.max(Scene.band(gx, 0f, bar, foot) + Scene.band(gx, cell - 0.001f, cell + bar, foot),
                            Scene.band(gz, 0f, bar, foot) + Scene.band(gz, cell - 0.001f, cell + bar, foot));
                    metalC = Col.mix(0xFF14181E, 0xFF434B57, Col.clamp01(bars + 0.25f));
                } else {
                    metalC = Col.mix(0xFF2A3038, 0xFF4A525E, Col.clamp01(0.5f + 0.5f * (float) Math.sin(r * 40f)) * 0.3f);
                }
                metalC = Col.mul(metalC, 0.45f + 0.8f * lamp);
                int i = sy * w + sx;
                float rf = 0.10f * lamp;
                out[i] = Col.mix(glass(sampleSpace(u, v), rf), metalC, m);
                if (m < 1f) {
                    idx[n] = i;
                    metal[n] = metalC;
                    us[n] = u;
                    vs[n] = v;
                    cov[n] = m;
                    refl[n] = rf;
                    n++;
                }
            }
        }
        gn = n;
        gIdx = java.util.Arrays.copyOf(idx, n);
        gMetal = java.util.Arrays.copyOf(metal, n);
        gU = java.util.Arrays.copyOf(us, n);
        gV = java.util.Arrays.copyOf(vs, n);
        gCov = java.util.Arrays.copyOf(cov, n);
        gRefl = java.util.Arrays.copyOf(refl, n);
        base = out.clone();
    }

    private static float walkway(float x, float z, float z0, float z1, float foot) {
        return Scene.band(x, -1.1f, 1.1f, foot) * Scene.band(z, z0, z1, foot);
    }

    /** Space seen through glass: a little darker and bluer, with the deck lights on it. */
    private static int glass(int c, float refl) {
        c = Col.mix(c, 0xFF0A1424, 0.18f);
        return Col.addScaled(c, 0xFFCFE0FF, refl);
    }

    @Override
    void keepVisible(int[] still) {
        int n = 0;
        for (int k = 0; k < gn; k++) {
            int i = gIdx[k];
            if (still[i] != base[i]) continue;          // the table covers it
            gIdx[n] = i;
            gMetal[n] = gMetal[k];
            gU[n] = gU[k];
            gV[n] = gV[k];
            gCov[n] = gCov[k];
            gRefl[n] = gRefl[k];
            n++;
        }
        gn = n;
    }

    /** The sky turning under the deck: each glass pixel looks out at the turned texture. */
    @Override
    void twinkle(int[] frame, float t) {
        float th = spin() * t;
        float c = (float) Math.cos(th), s = (float) Math.sin(th);
        float k = TEX / (2f * SPAN);
        for (int n = 0; n < gn; n++) {
            float u = gU[n], v = gV[n];
            float iu = u * c + v * s, iv = -u * s + v * c;
            int tx = (int) ((iu + SPAN) * k), ty = (int) ((iv + SPAN) * k);
            int col = tx >= 0 && ty >= 0 && tx < TEX && ty < TEX ? tex[ty * TEX + tx] : 0xFF020308;
            col = glass(col, gRefl[n]);
            float m = gCov[n];
            frame[gIdx[n]] = m > 0f ? Col.mix(col, gMetal[n], m) : col;
        }
        // lights on the rings, blinking in turn
        if (scene != null) {
            Px f = new Px(scene.w, scene.h, frame);
            for (int i = 0; i < 12; i++) {
                float a = i * 0.5236f, r = 1.9f + (i % 3) * 1.05f;
                float on = (float) Math.sin(t * 2.2f - i * 0.9f);
                if (on < 0.3f) continue;
                if (!scene.cam.project((float) Math.cos(a) * r, DECK, Game.NZ + (float) Math.sin(a) * r)) continue;
                int col = i % 2 == 0 ? 0xFFFF5A3C : 0xFF58FF9A;
                f.glow(scene.cam.sx, scene.cam.sy, 4f, col, 0.5f * on);
                f.discAA(scene.cam.sx, scene.cam.sy, 0.8f, col, on);
            }
        }
    }

    private int sampleSpace(float u, float v) {
        float k = TEX / (2f * SPAN);
        int tx = (int) ((u + SPAN) * k), ty = (int) ((v + SPAN) * k);
        return tx >= 0 && ty >= 0 && tx < TEX && ty < TEX ? tex[ty * TEX + tx] : 0xFF020308;
    }

    /** What lies below the deck, still in space: stars, a haze of the galaxy, and the planet. */
    private static int[] space() {
        int[] o = new int[TEX * TEX];
        float px = 2f * SPAN / TEX;
        final float ex = 2.7f, ez = -0.6f, er = 1.9f;      // the planet
        for (int j = 0; j < TEX; j++)
            for (int i = 0; i < TEX; i++) {
                float u = -SPAN + (i + 0.5f) * px, v = -SPAN + (j + 0.5f) * px;
                float band = (float) Math.exp(-sq((v + 0.4f * u - 0.8f) * 0.9f));
                int c = Col.mix(0xFF020309, 0xFF0A0A1C, band * 0.8f);
                c = Col.addScaled(c, 0xFF2C1A5A, Col.ramp(0.5f, 0.85f, Sky.fbm(u * 0.9f + 3f, v * 0.9f)) * 0.5f * (0.4f + band));
                float hv = Col.hash(i, j);
                if (hv > 0.988f - 0.006f * band) {
                    float b = (hv - 0.982f) / 0.018f;
                    c = Col.addScaled(c, Col.hash(i + 3, j) < 0.2f ? 0xFFAFC8FF : 0xFFF2F4FF, 0.3f + 0.7f * b * b);
                }
                float du = u - ex, dv = v - ez;
                float r = (float) Math.sqrt(du * du + dv * dv);
                if (r < er) {
                    // oceans and land under clouds, lit from one side
                    float land = Sky.fbm(u * 1.4f + 11f, v * 1.4f + 4f);
                    int ground = land > 0.56f ? Col.mix(0xFF3E6A2C, 0xFF8A7A4A, Col.ramp(0.56f, 0.75f, land)) : Col.mix(0xFF0A2C64, 0xFF16508E, land);
                    float cloud = Col.ramp(0.5f, 0.75f, Sky.fbm(u * 2.2f + 2f, v * 2.2f + 7f));
                    ground = Col.mix(ground, 0xFFF0F4F8, cloud * 0.85f);
                    float nz = (float) Math.sqrt(Math.max(0f, 1f - (r / er) * (r / er)));
                    float lit = Col.clamp01(0.15f + 0.85f * (-0.6f * du / er + 0.3f * dv / er + 0.75f * nz));
                    c = Col.mul(ground, 0.12f + 0.95f * lit);
                    c = Col.addScaled(c, 0xFF5AA8FF, (float) Math.pow(1f - nz, 3) * 0.6f * lit);
                } else if (r < er + 0.16f) {
                    float a = 1f - (r - er) / 0.16f;
                    float lit = Col.clamp01(0.3f - 0.7f * du / r + 0.3f * dv / r);
                    c = Col.addScaled(c, 0xFF6AB8FF, a * a * 0.8f * lit);
                }
                o[j * TEX + i] = c;
            }
        return o;
    }

    private static float sq(float v) {
        return v * v;
    }

    // ---- the table: dark composite, white lines, safety orange on the edge

    @Override
    int surface(float x, float z) {
        float lit = (float) Math.exp(-(x * x / 1.4f + (z - 1.37f) * (z - 1.37f) / 3f));
        return Col.mix(0xFF14181F, 0xFF2C3440, 0.3f + 0.7f * lit);
    }

    @Override
    int lines() {
        return 0xFFE8ECF2;
    }

    @Override
    int grid() {
        return 0xFF2E4A6A;
    }

    @Override
    float gridGain() {
        return 0.85f;
    }

    @Override
    float bendA() {
        return 0f;
    }

    @Override
    float bendC() {
        return 1f;
    }

    // twisted the way the ball curves, more at every level
    @Override
    float swirl() {
        return -0.55f * power;
    }

    @Override
    int light() {
        return 0xFFDDE8FF;
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
        return 0xFF3A404A;
    }

    @Override
    int faceBottom() {
        return 0xFF101216;
    }

    @Override
    int bevel() {
        return 0xFFFF8A2A;
    }

    @Override
    int tail() {
        return 0xFFFFB45A;
    }

    @Override
    int netThread() {
        return 0xFFC8D0DC;
    }

    @Override
    int netTape() {
        return 0xFFF4F7FB;
    }

    @Override
    int ripple() {
        return 0xFFFF9A48;
    }

    @Override
    int rimLight() {
        return 0xFFDDE8FF;
    }

    @Override
    int hum() {
        return Synth.HUM_STATION;
    }

    /**
     * Lanes of light on the table, each the path of a typical ball: one hit away
     * from you, one coming back. Dashes run along them in the direction of flight.
     */
    @Override
    void drawOnTable(Scene s, Px f, Game g, float t) {
        float w = spin(), speed = 4.5f;
        for (int lane = 0; lane < 2; lane++) {
            boolean away = lane == 0;
            float x0 = away ? 0.32f : -0.32f, z0 = away ? 0.25f : Game.L - 0.25f;
            int col = away ? 0xFFFFB45A : 0xFF9FD8FF;
            float lx = 0f, ly = 0f;
            boolean have = false;
            for (int i = 0; i <= 40; i++) {
                float d = i / 40f * (Game.L - 0.5f);
                float side = w * d * d / speed;
                float x = away ? x0 - side : x0 + side, z = away ? z0 + d : z0 - d;
                if (!s.cam.project(x, 0f, z)) continue;
                float phase = (d / 0.12f - t * 3f) % 2f;
                if (phase < 0f) phase += 2f;
                if (have && phase < 1.2f) f.lineAA(lx, ly, s.cam.sx, s.cam.sy, 1.8f, col, 0.45f);
                lx = s.cam.sx;
                ly = s.cam.sy;
                have = true;
            }
        }
    }

    @Override
    void drawCenter(Scene s, Px f, Game g, float t) {
        // nothing floats here: the force is the room
    }
}
