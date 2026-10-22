package com.shumtugle.umbra;

/**
 * A winter evening by a frozen river, early in the nineteenth century: snow,
 * firs, a sledge, a lantern, and a duel fought with paddles, where nobody falls.
 *
 * The law is the wind. It blows across the table and carries the ball sideways,
 * now one way, now the other, and every few seconds a gust crosses the table as a
 * travelling wave. The snow shows all of it in advance: flakes drift with the
 * wind and streak in a gust, and the gust's front sweeps across the table as a
 * band of driven snow before it reaches the ball.
 *
 * The ritual is the barrier: a paddle that comes forward to its barrier line
 * strikes harder, and has less time to answer. And each side has one shot a
 * match: the hardest, deepest ball there is, fired with a crack and a cloud of
 * smoke that the wind then carries over the table.
 */
final class River extends Realm {
    static final float ACC = 0.9f;                     // sideways push of a unit of wind, m/s^2
    static final float GUST_T = 5.5f, GUST_V = 2.6f, GUST_W = 0.35f;
    static final float DECK = -0.76f;
    static final float BARRIER = 0.55f;                // from each end of the table
    static final float START = -0.25f;                 // where a paddle stands at rest

    private float now;

    // snow, in the air over the table
    private static final int FLAKES = 190;
    private final float[] fx = new float[FLAKES], fy = new float[FLAKES], fz = new float[FLAKES], fs = new float[FLAKES];
    private boolean snowing;
    private final java.util.Random rnd = new java.util.Random(1837);

    // gunsmoke
    private static final int PUFFS = 18;
    private final float[] sx = new float[PUFFS], sy = new float[PUFFS], sz = new float[PUFFS], svx = new float[PUFFS],
            svz = new float[PUFFS], sage = new float[PUFFS];
    private float flashX, flashZ, flashAge = 9f;

    @Override
    String key() {
        return "river";
    }

    @Override
    String name() {
        return "THE BLACK RIVER";
    }

    @Override
    String note() {
        return "1837  WIND, SNOW, AND ONE SHOT EACH";
    }

    @Override
    String hint() {
        return "THE SNOW SHOWS THE WIND";
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

    // ---- the wind, a function of place and time

    static float base(float t) {
        return 0.9f * (float) Math.sin(t * 0.11f + 0.7f);
    }

    private static float gustDir(int n) {
        return base(n * GUST_T) >= 0f ? 1f : -1f;
    }

    /** Where the front of the current gust is, across the table. */
    static float gustFront(float t) {
        int n = (int) Math.floor(t / GUST_T);
        float dir = gustDir(n);
        return -dir * 2.4f + dir * GUST_V * (t - n * GUST_T);
    }

    static float wind(float x, float t) {
        int n = (int) Math.floor(t / GUST_T);
        float dir = gustDir(n);
        float d = (x - gustFront(t)) / GUST_W;
        return base(t) + dir * 1.6f * (float) Math.exp(-d * d);
    }

    @Override
    boolean act(Ball b, float t, float h) {
        b.vx += ACC * power * wind(b.x, t) * h;
        return false;
    }

    @Override
    float closeness(Ball b) {
        return Col.clamp01(Math.abs(wind(b.x, now)) / 2.5f);
    }

    @Override
    int shots() {
        return 1;
    }

    /** Coming forward to the barrier: up to a quarter more power, at the barrier line itself. */
    @Override
    float strikeBoost(int side, float paddleZ) {
        float forward = side == 0 ? paddleZ - START : (Game.L - START) - paddleZ;
        return 0.25f * Col.clamp01(forward / (BARRIER - START));
    }

    @Override
    void onShot(Game g, int side) {
        Game.Paddle p = g.pad[side];
        float dir = side == 0 ? 1f : -1f;
        flashX = p.x;
        flashZ = p.z + dir * 0.15f;
        flashAge = 0f;
        for (int i = 0; i < PUFFS; i++) {
            sx[i] = p.x + (rnd.nextFloat() - 0.5f) * 0.1f;
            sy[i] = Game.PY + 0.05f + rnd.nextFloat() * 0.08f;
            sz[i] = p.z + dir * (0.1f + rnd.nextFloat() * 0.25f);
            svx[i] = (rnd.nextFloat() - 0.5f) * 0.3f;
            svz[i] = dir * (0.3f + rnd.nextFloat() * 0.7f);
            sage[i] = rnd.nextFloat() * 0.2f;
        }
    }

    @Override
    void tick(Game g, float dt) {
        now = g.clock;
        if (!snowing) {
            for (int i = 0; i < FLAKES; i++) respawn(i, true);
            snowing = true;
        }
        for (int i = 0; i < FLAKES; i++) {
            float w = wind(fx[i], now);
            fx[i] += (w * 0.9f + 0.15f * (float) Math.sin(now * 1.3f + i)) * dt;
            fy[i] -= (0.25f + 0.25f * fs[i]) * dt;
            fz[i] += 0.05f * (float) Math.sin(now * 0.7f + i * 1.7f) * dt;
            if (fy[i] < DECK || Math.abs(fx[i]) > 2.2f) respawn(i, false);
        }
        flashAge += dt;
        for (int i = 0; i < PUFFS; i++) {
            if (sage[i] > 4.5f) continue;
            sage[i] += dt;
            float slow = (float) Math.exp(-sage[i] * 1.5f);
            sx[i] += (wind(sx[i], now) * 0.45f + svx[i] * slow) * dt;
            sz[i] += svz[i] * slow * dt;
            sy[i] += 0.06f * dt;
        }
    }

    private void respawn(int i, boolean anywhere) {
        fx[i] = (rnd.nextFloat() - 0.5f) * 4.2f;
        fy[i] = anywhere ? DECK + rnd.nextFloat() * 2.4f : 1.6f;
        fz[i] = -0.9f + rnd.nextFloat() * 5.2f;
        fs[i] = rnd.nextFloat();
        if (!anywhere && Math.abs(fx[i]) > 2.0f) fx[i] *= 0.5f;
    }

    // ---- the snowfield

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
            for (int sx = 0; sx < w; sx++) out[sy * w + sx] = ground(t * (sx + 0.5f - cam.cx) * inv, z, foot);
        }
        // the lantern on its post by the near end
        Px f = new Px(w, h, out);
        if (cam.project(-1.05f, DECK + 1.2f, -0.55f)) {
            float lx = cam.sx, ly = cam.sy;
            if (cam.project(-1.05f, DECK, -0.55f)) f.lineAA(lx, ly, cam.sx, cam.sy, 2f, 0xFF1A1410, 1f);
            f.glow(lx, ly, 40f, 0xFFFFB060, 0.35f);
            f.discAA(lx, ly, 2.4f, 0xFF2A1E14, 1f);
            f.discAA(lx, ly, 1.4f, 0xFFFFE0A0, 1f);
        }
    }

    private static int ground(float x, float z, float foot) {
        int c;
        if (z > 3.75f) {
            // the frozen river: grey ice, cracks, and a hole of black water
            float n = Sky.fbm(x * 2f + 3f, z * 2f + 1f);
            c = Col.mix(0xFF3A4A60, 0xFF6A7A92, n);
            float crack = Math.abs(Sky.fbm(x * 4f + 11f, z * 4f) - 0.5f);
            c = Col.mix(c, 0xFF2A3446, Col.clamp01((0.02f - crack) / 0.02f) * 0.7f);
            float hx = x - 0.7f, hz = (z - 4.6f) * 1.6f;
            if (hx * hx + hz * hz < 0.18f + 0.05f * n) c = Col.mix(0xFF0A1018, 0xFF162232, n);
            float bank = Col.clamp01((z - 3.75f) / 0.12f);
            c = Col.mix(0xFFDCE4F0, c, bank);
        } else {
            // snow with soft drifts, blue in the dusk
            float drift = Sky.fbm(x * 1.2f + 5f, z * 1.2f + 2f);
            c = Col.mix(0xFF5A6A90, 0xFFB8C6DE, 0.3f + 0.7f * drift);
            float sparkle = Col.hash((int) (x * 160f + 4000f), (int) (z * 160f + 4000f));
            if (sparkle > 0.995f) c = 0xFFFFFFFF;
            // footprints, two trails along the sides
            for (int side = -1; side <= 1; side += 2) {
                float px = side * 1.25f, k = (z + 1f) / 0.36f;
                int step = (int) Math.floor(k);
                float cz = (step + 0.5f) * 0.36f - 1f, cx = px + ((step & 1) == 0 ? 0.07f : -0.07f);
                float ex = (x - cx) / 0.05f, ez = (z - cz) / 0.11f;
                float d = ex * ex + ez * ez;
                if (d < 1f && z < Game.L + 1f) c = Col.mix(c, 0xFF6A7898, (1f - d) * 0.6f);
            }
            // the barriers: a greatcoat thrown on the snow beside each one
            c = coat(c, x, z, -Game.HW - 0.32f, BARRIER);
            c = coat(c, x, z, Game.HW + 0.32f, Game.L - BARRIER);
            // a sledge waiting
            float ux = (x + 1.5f) / 0.32f, uz = (z - 3.0f) / 0.6f;
            if (Math.abs(ux) < 1f && Math.abs(uz) < 1f) {
                boolean runner = Math.abs(Math.abs(ux) - 0.92f) < 0.08f;
                c = runner ? 0xFF2A2018 : Col.mix(0xFF5A2A1A, 0xFF7A3A22, 0.5f + 0.5f * (float) Math.sin(uz * 9f));
            }
        }
        // firs seen from above: dark stars of branches with snow on the tips
        float[][] firs = {{1.55f, 0.2f, 0.55f}, {1.75f, 3.1f, 0.65f}, {-1.7f, 1.1f, 0.6f}, {-1.55f, 4.4f, 0.7f}, {1.35f, 4.9f, 0.6f}, {-1.75f, -0.7f, 0.5f}};
        for (float[] fr : firs) {
            float dx = x - fr[0], dz = z - fr[1];
            float r = (float) Math.sqrt(dx * dx + dz * dz);
            // its shadow first, cast away from the lantern
            float sdx = dx - 0.18f, sdz = dz - 0.25f;
            if (sdx * sdx + sdz * sdz < fr[2] * fr[2]) c = Col.mul(c, 0.82f);
            if (r > fr[2]) continue;
            float a = (float) Math.atan2(dz, dx);
            float reach = fr[2] * (0.72f + 0.28f * Math.abs((float) Math.sin(a * 4.5f + fr[0])));
            if (r > reach) continue;
            float k = r / reach;
            int needle = Col.mix(0xFF0E2218, 0xFF24402C, k);
            if (k > 0.7f && Col.hash((int) (a * 30f + 99f), (int) (r * 40f)) > 0.5f) needle = Col.mix(needle, 0xFFDCE6F2, 0.7f);
            c = Col.mix(c, needle, Col.clamp01((reach - r) / foot + 0.5f));
        }
        // dusk, with the lantern's pool of warm light near the table
        float lx = x + 1.05f, lz = z + 0.55f;
        float lamp = 1f / (1f + (lx * lx + lz * lz) / 1.2f);
        c = Col.mul(c, 0.62f + 0.3f / (1f + (x * x + (z - Game.NZ) * (z - Game.NZ)) / 6f));
        c = Col.addScaled(c, 0xFFFFA050, lamp * 0.45f);
        float under = Col.ramp(0.3f, -0.05f, Math.max(Math.abs(x) - Game.HW, Math.max(-z, z - Game.L)));
        return Col.mul(c, 1f - 0.45f * under);
    }

    private static int coat(int c, float x, float z, float cx, float cz) {
        float ux = (x - cx) / 0.22f, uz = (z - cz) / 0.34f;
        float d = ux * ux + uz * uz;
        if (d > 1f) return c;
        int cloth = Col.mix(0xFF1E2A24, 0xFF34443A, 0.5f + 0.5f * (float) Math.sin(ux * 7f + uz * 3f));
        if (uz < -0.6f) cloth = 0xFF14100C;           // the collar
        return Col.mix(c, cloth, Col.clamp01((1f - d) * 6f));
    }

    // ---- the table: dark wood under frost

    @Override
    int surface(float x, float z) {
        float lit = (float) Math.exp(-(x * x / 1.6f + (z - 1.37f) * (z - 1.37f) / 3.4f));
        int c = Col.mix(0xFF1E1610, 0xFF3A2A1E, 0.35f + 0.65f * lit);
        // rime only along the edges, where the cold gets in first
        float edge = Math.min(Game.HW - Math.abs(x), Math.min(z, Game.L - z));
        float frost = Col.ramp(0.16f, 0.0f, edge) * Col.ramp(0.35f, 0.7f, Sky.fbm(x * 6f + 7f, z * 6f + 3f));
        return Col.mix(c, 0xFFA8B4C4, frost * 0.6f);
    }

    @Override
    int lines() {
        return 0xFFE8EAE4;
    }

    @Override
    int grid() {
        return 0xFF2A2C30;
    }

    @Override
    float gridGain() {
        return 0.4f;
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
        return 0xFFFFC890;
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
        return 0xFF3A2414;
    }

    @Override
    int faceBottom() {
        return 0xFF120A06;
    }

    @Override
    int bevel() {
        return 0xFFC8D0D8;
    }

    @Override
    int tail() {
        return 0xFFE8F0F8;
    }

    @Override
    float tailWidth() {
        return 1.6f;
    }

    @Override
    float tailAlpha() {
        return 0.30f;
    }

    @Override
    int netThread() {
        return 0xFF505860;
    }

    @Override
    int netTape() {
        return 0xFFE0E4EA;
    }

    @Override
    int ripple() {
        return 0xFFE8F0F8;
    }

    @Override
    int rimLight() {
        return 0xFFFFC890;
    }

    @Override
    int hum() {
        return Synth.HUM_BLIZZARD;
    }

    // walnut and silver, as on a pistol from a lined case
    private static final Racket MINE = new Racket(0xFF8A1A24, 0xFFD8606A, 0xFF3A0810, 0xFF3A2414, 0xFF8A6040,
            0xFF4A2E18, 0xFF9A7048, 0xFFD8DEE6, 0, 0.06f);
    private static final Racket THEIRS = new Racket(0xFF2A3A5A, 0xFF7A90B8, 0xFF0C1220, 0xFF3A2414, 0xFF8A6040,
            0xFF4A2E18, 0xFF9A7048, 0xFFD8DEE6, 0xFFC8D8F0, 0.06f);

    @Override
    Racket racket(boolean player) {
        return player ? MINE : THEIRS;
    }

    /** The barrier lines across the table, and the gust's front sweeping over it as driven snow. */
    @Override
    void drawOnTable(Scene s, Px f, Game g, float t) {
        Cam cam = s.cam;
        for (int side = 0; side < 2; side++) {
            float bz = side == 0 ? BARRIER : Game.L - BARRIER;
            for (int i = 0; i < 12; i++) {
                float x0 = -Game.HW + (i + 0.15f) * (2f * Game.HW / 12f), x1 = x0 + 0.07f;
                if (!cam.project(x0, 0f, bz)) continue;
                float ax = cam.sx, ay = cam.sy;
                if (cam.project(x1, 0f, bz)) f.lineAA(ax, ay, cam.sx, cam.sy, 1.2f, 0xFFB83A3A, 0.55f);
            }
        }
        float front = gustFront(t);
        int n = (int) Math.floor(t / GUST_T);
        float dir = gustDir(n);
        for (int i = 0; i < 70; i++) {
            float z = -0.6f + Col.hash(i, n) * (Game.L + 1.2f);
            float x = front - dir * Col.hash(i + 50, n) * 0.6f;
            float len = 0.12f + 0.25f * Col.hash(i + 90, n);
            float op = 0.5f * (1f - Math.abs(x - front) / 0.6f);
            if (op <= 0f) continue;
            if (!cam.project(x, 0.01f, z)) continue;
            float ax = cam.sx, ay = cam.sy;
            if (cam.project(x - dir * len, 0.01f, z)) f.lineAA(ax, ay, cam.sx, cam.sy, 1f, 0xFFF0F6FF, op);
        }
    }

    @Override
    void drawCenter(Scene s, Px f, Game g, float t) {
        // nothing floats here: the force is the weather
    }

    /** Falling snow, streaked by the wind, and the smoke of a shot. */
    @Override
    void drawOver(Scene s, Px f, Game g, float t) {
        Cam cam = s.cam;
        for (int i = 0; i < PUFFS; i++) {
            if (sage[i] > 4.5f) continue;
            if (!cam.project(sx[i], sy[i], sz[i])) continue;
            float a = sage[i] / 4.5f, r = (0.07f + 0.3f * (float) Math.sqrt(a)) * cam.scale();
            f.puff(cam.sx, cam.sy, r, r * 0.8f, 0xFFC8CCD4, 0.55f * (1f - a) * (1f - a));
        }
        if (flashAge < 0.15f && cam.project(flashX, Game.PY + 0.08f, flashZ)) {
            f.glow(cam.sx, cam.sy, 30f, 0xFFFFC060, 1.2f * (1f - flashAge / 0.15f));
        }
        for (int i = 0; i < FLAKES; i++) {
            if (!cam.project(fx[i], fy[i], fz[i])) continue;
            float sc = cam.scale(), r = Math.max(0.6f, 0.009f * sc * (0.6f + 0.6f * fs[i]));
            float w = wind(fx[i], t);
            float streak = Math.abs(w) > 1f ? (Math.abs(w) - 1f) * 0.06f * sc : 0f;
            float op = 0.45f + 0.4f * fs[i];
            if (streak > 1f) f.lineAA(cam.sx, cam.sy, cam.sx - Math.signum(w) * streak, cam.sy, r * 1.6f, 0xFFF4F8FF, op * 0.8f);
            else f.discAA(cam.sx, cam.sy, r, 0xFFF4F8FF, op);
        }
    }
}
