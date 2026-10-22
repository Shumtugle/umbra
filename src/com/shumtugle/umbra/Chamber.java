package com.shumtugle.umbra;

/**
 * A cloud chamber, as the physicists of the early twentieth century built them:
 * a glass vessel of supersaturated vapour, a strong lamp from the side, black
 * velvet below, and two great coils that fill the middle of it with a magnetic
 * field rising out of the table. Every charged thing that crosses the vapour
 * leaves a trail of droplets, and in the field every trail curls.
 *
 * Each struck ball carries a charge, red for positive and blue for negative,
 * drawn at random with the strike. In the field a red ball turns one way and a
 * blue one the other, the faster the harder; outside the field it flies straight.
 * The law is shown three ways: the ball's colour and sign, the field's dots on
 * the table, densest where it is strongest, and the trail of mist the ball
 * leaves, which stays long enough to show its curve. Stray particles from the
 * sky cross the chamber all the while and curl by the same law: electrons in
 * tight blue spirals one way, now and then a positron the other.
 */
final class Chamber extends Realm {
    static final float FIELD = 1.3f;       // turning rate per unit of speed at the centre of the field
    static final float WIDTH = 0.75f;      // how far along the table the field reaches either side of the net
    static final float DECK = -0.76f;

    private final java.util.Random rnd = new java.util.Random(1911);
    private float now;

    // the ball's own trail of droplets: where, when, and its charge then
    private static final int MIST = 260;
    private final float[] mx = new float[MIST], my = new float[MIST], mz = new float[MIST], mt = new float[MIST], mq = new float[MIST];
    private int mHead, mCount;
    private float mClock;

    // stray particles: a spiral or a line on the table, appearing and fading
    private static final int STRAYS = 6;
    private final float[] px = new float[STRAYS], pz = new float[STRAYS], pa = new float[STRAYS], pr = new float[STRAYS],
            page = new float[STRAYS];
    private final int[] kind = new int[STRAYS];    // 0 electron, 1 positron, 2 alpha, 3 muon
    private float nextStray;

    @Override
    String key() {
        return "chamber";
    }

    @Override
    String name() {
        return "THE CLOUD CHAMBER";
    }

    @Override
    String note() {
        return "1932  EVERY BALL CARRIES A CHARGE";
    }

    @Override
    String hint() {
        return "RED TURNS ONE WAY, BLUE THE OTHER";
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

    static float field(float z) {
        float d = (z - Game.NZ) / WIDTH;
        return (float) Math.exp(-d * d);
    }

    @Override
    boolean launch(Ball b, int side, float tx, float tz, float yn, float power) {
        b.charge = rnd.nextBoolean() ? 1f : -1f;
        return false;
    }

    /** The magnetic force: across the motion, its way set by the charge, its size by the speed and the field. */
    @Override
    boolean act(Ball b, float t, float h) {
        if (b.charge == 0f) return false;
        float k = FIELD * power * field(b.z) * b.charge * h;
        float vx = b.vx, vz = b.vz;
        b.vx = vx - k * vz;
        b.vz = vz + k * vx;
        return false;
    }

    @Override
    float closeness(Ball b) {
        return b.charge == 0f ? 0f : field(b.z) * Col.clamp01(Math.abs(b.vz) / 6f);
    }

    @Override
    int ballGlow(Ball b) {
        return b.charge > 0f ? 0xFFFF5A48 : b.charge < 0f ? 0xFF4A9AFF : 0xFFDDE6F0;
    }

    @Override
    float ballGlowK(Ball b) {
        return b.charge == 0f ? 0.15f : 0.55f;
    }

    @Override
    void tick(Game g, float dt) {
        now = g.clock;
        Ball b = g.ball;
        mClock += dt;
        boolean flying = (g.state == Game.RALLY || g.state == Game.POINT) && !b.gone && b.charge != 0f;
        if (flying && mClock > 0.012f) {
            mClock = 0f;
            mx[mHead] = b.x;
            my[mHead] = b.y;
            mz[mHead] = b.z;
            mt[mHead] = now;
            mq[mHead] = b.charge;
            mHead = (mHead + 1) % MIST;
            mCount = Math.min(MIST, mCount + 1);
        }
        for (int i = 0; i < STRAYS; i++) page[i] += dt;
        nextStray -= dt;
        if (nextStray <= 0f) {
            nextStray = 0.5f + rnd.nextFloat() * 0.9f;
            int slot = 0;
            for (int i = 1; i < STRAYS; i++) if (page[i] > page[slot]) slot = i;
            float r = rnd.nextFloat();
            kind[slot] = r < 0.62f ? 0 : r < 0.7f ? 1 : r < 0.85f ? 2 : 3;
            px[slot] = (rnd.nextFloat() - 0.5f) * 1.3f;
            pz[slot] = 0.3f + rnd.nextFloat() * (Game.L - 0.6f);
            pa[slot] = rnd.nextFloat() * 6.2832f;
            pr[slot] = 0.08f + rnd.nextFloat() * 0.12f;
            page[slot] = 0f;
        }
    }

    // ---- the chamber

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
            for (int sx = 0; sx < w; sx++) out[sy * w + sx] = floor(t * (sx + 0.5f - cam.cx) * inv, z, foot);
        }
    }

    private static int floor(float x, float z, float foot) {
        float u = x, v = z - Game.NZ;
        // black velvet, lit from the left by the lamp, with layers of vapour drifting over it
        float lamp = Col.clamp01(0.55f - 0.25f * x);
        int c = Col.mix(0xFF050608, 0xFF14171C, lamp * (0.6f + 0.4f * Sky.fbm(x * 9f + 3f, z * 9f)));
        float haze = Col.ramp(0.45f, 0.75f, Sky.fbm(x * 0.9f + 7f, z * 2.4f + 1f));
        c = Col.addScaled(c, 0xFF6A7888, haze * 0.18f * (0.4f + lamp));
        // the glass wall's brass foot, a ring round the table
        float r = (float) Math.sqrt(u * u + v * v * 0.55f);
        float ring = Col.clamp01((0.05f - Math.abs(r - 1.85f)) / foot + 0.5f);
        c = Col.mix(c, Col.mix(0xFF6A4A1E, 0xFFE2B45A, lamp), ring);
        // the coils: copper windings either side of the net
        for (int sd = -1; sd <= 1; sd += 2) {
            float cx = sd * (Game.HW + 0.55f);
            float dx = x - cx, dz = (z - Game.NZ) * 0.9f;
            float d = (float) Math.sqrt(dx * dx + dz * dz);
            if (d < 0.5f) {
                float wind = 0.5f + 0.5f * (float) Math.sin(d * 120f);
                int copper = Col.mix(0xFF5A2A10, 0xFFD8783A, 0.35f + 0.4f * wind + 0.25f * Col.clamp01(-dx / 0.5f * sd));
                if (d < 0.16f) copper = Col.mix(0xFF2A2A2E, 0xFF8A8E96, 0.5f + 0.5f * Col.clamp01(-dx * 4f));
                c = Col.mix(c, copper, Col.clamp01((0.5f - d) / foot + 0.5f));
            }
        }
        float under = Col.ramp(0.3f, -0.05f, Math.max(Math.abs(x) - Game.HW, Math.max(-z, z - Game.L)));
        return Col.mul(c, 1f - 0.4f * under);
    }

    // ---- the table: black slate, pale lines, the field's dots

    @Override
    int surface(float x, float z) {
        float lit = Col.clamp01(0.5f - 0.35f * x / Game.HW);
        return Col.mix(0xFF0C0E12, 0xFF1E232A, lit);
    }

    @Override
    int lines() {
        return 0xFFC8D4E0;
    }

    @Override
    int grid() {
        return 0xFF20262E;
    }

    @Override
    float gridGain() {
        return 0.5f;
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
        return 0xFFDDE6F0;
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
        return 0xFF2A2E34;
    }

    @Override
    int faceBottom() {
        return 0xFF08090B;
    }

    @Override
    int bevel() {
        return 0xFFE2B45A;
    }

    @Override
    int tail() {
        return 0xFFE8EEF4;
    }

    @Override
    float tailAlpha() {
        return 0f;                          // the chamber draws its own trail, of droplets
    }

    @Override
    int netThread() {
        return 0xFF8A949E;
    }

    @Override
    int netTape() {
        return 0xFFE8EEF4;
    }

    @Override
    int ripple() {
        return 0xFFE8EEF4;
    }

    @Override
    int rimLight() {
        return 0xFFDDE6F0;
    }

    @Override
    int hum() {
        return Synth.HUM_CHAMBER;
    }

    // electrodes: copper for the player, blued steel for the opponent
    private static final Racket MINE = new Racket(0xFFC8642A, 0xFFF0A070, 0xFF5A2410, 0xFF3A3E44, 0xFFA8B0BA,
            0xFF2A2E34, 0xFF6A727C, 0xFFE2B45A, 0, 0.05f);
    private static final Racket THEIRS = new Racket(0xFF2A4A7A, 0xFF7AA0D8, 0xFF0A1428, 0xFF3A3E44, 0xFFA8B0BA,
            0xFF2A2E34, 0xFF6A727C, 0xFFE2B45A, 0xFF4A9AFF, 0.05f);

    @Override
    Racket racket(boolean player) {
        return player ? MINE : THEIRS;
    }

    /** The field rising out of the table, drawn as dots, and the stray particles' tracks. */
    @Override
    void drawOnTable(Scene s, Px f, Game g, float t) {
        Cam cam = s.cam;
        float k = Math.min(1f, power);
        for (int j = 0; j < 15; j++) {
            float z = Game.NZ + (j - 7) * 0.2f;
            float b = field(z) * k;
            if (b < 0.08f) continue;
            for (int i = 0; i < 8; i++) {
                float x = -Game.HW + 0.095f + i * 0.2f;
                if (!cam.project(x, 0f, z)) continue;
                f.discAA(cam.sx, cam.sy, 1f + b, 0xFF9FB8D8, 0.25f + 0.45f * b);
            }
        }
        for (int i = 0; i < STRAYS; i++) {
            float age = page[i];
            if (age > 2.6f) continue;
            float op = age < 0.08f ? age / 0.08f : 1f - (age - 0.08f) / 2.52f;
            op *= 0.6f;
            int col = kind[i] == 1 ? 0xFFFF8A78 : kind[i] == 0 ? 0xFFB8D8FF : 0xFFE8EEF4;
            if (kind[i] <= 1) {
                // a spiral, tightening as the particle loses speed; the electron turns one way, the positron the other
                float dir = kind[i] == 0 ? -1f : 1f, lx = 0f, ly = 0f;
                for (int n = 0; n <= 60; n++) {
                    float th = n * 0.21f;
                    float r = pr[i] * (float) Math.exp(-th * 0.09f);
                    float ang = pa[i] + dir * th;
                    float x = px[i] + r * (float) Math.cos(ang), z = pz[i] + r * (float) Math.sin(ang);
                    if (!cam.project(x, 0.003f, z)) continue;
                    if (n > 0 && (n & 1) == 0) f.lineAA(lx, ly, cam.sx, cam.sy, 0.9f, col, op);
                    lx = cam.sx;
                    ly = cam.sy;
                }
            } else {
                // heavy and fast ones run nearly straight: a short thick alpha, a long thin muon
                float len = kind[i] == 2 ? 0.18f : 0.9f, wdt = kind[i] == 2 ? 2.4f : 0.9f;
                float x0 = px[i], z0 = pz[i], x1 = x0 + (float) Math.cos(pa[i]) * len, z1 = z0 + (float) Math.sin(pa[i]) * len;
                if (!cam.project(x0, 0.003f, z0)) continue;
                float ax = cam.sx, ay = cam.sy;
                if (cam.project(x1, 0.003f, z1)) f.lineAA(ax, ay, cam.sx, cam.sy, wdt, col, op * 0.8f);
            }
        }
    }

    /** The ball's trail of mist, and its charge written beside it. */
    @Override
    void drawGuide(Scene s, Px f, Game g) {
        Cam cam = s.cam;
        for (int k = 0; k < mCount; k++) {
            int i = (mHead - 1 - k + MIST * 2) % MIST;
            float age = now - mt[i];
            if (age > 2.8f || age < 0f) continue;
            if (!cam.project(mx[i], my[i], mz[i])) continue;
            float spread = (0.6f + age * 1.6f);
            float jx = (Col.hash(i, 3) - 0.5f) * spread, jy = (Col.hash(i, 7) - 0.5f) * spread;
            float op = 0.55f * (1f - age / 2.8f);
            int col = Col.mix(0xFFF2F6FA, mq[i] > 0f ? 0xFFFFB0A0 : 0xFFB0D0FF, 0.35f);
            f.discAA(cam.sx + jx, cam.sy + jy, 0.8f + age * 0.5f, col, op);
        }
        Ball b = g.ball;
        if (b.charge != 0f && !b.gone && (g.state == Game.RALLY || g.state == Game.POINT) && cam.project(b.x, b.y, b.z)) {
            float r = Ball.R * Scene.LOOK * cam.scale();
            int x = (int) (cam.sx + r + 3), y = (int) (cam.sy - r - 9);
            int col = b.charge > 0f ? 0xFFFF7A68 : 0xFF6AB0FF;
            PixelFont.draw(f, b.charge > 0f ? "+" : "-", x + 1, y + 1, 2, 0xFF02030A);
            PixelFont.draw(f, b.charge > 0f ? "+" : "-", x, y, 2, col);
        }
    }

    @Override
    void drawCenter(Scene s, Px f, Game g, float t) {
        // nothing floats here: the field fills the middle of the room
    }
}
