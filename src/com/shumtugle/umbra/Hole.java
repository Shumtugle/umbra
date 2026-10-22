package com.shumtugle.umbra;

/**
 * The black hole that drifts over the net. It pulls the ball, harder the closer
 * the ball passes, and a ball that crosses the horizon is gone. None of it is
 * hidden: the light bends around the hole, the grid on the table falls toward
 * it like a funnel, the ball's tail curves, and the hum grows as the ball comes
 * near.
 */
final class Hole extends Realm {
    static final float STRENGTH = 0.42f;
    static final float REACH2 = 1.2f;       // no pull beyond this squared distance
    static final float SOFT = 0.06f;        // softens the pull near the centre
    static final float Y = 0.42f;           // height above the table top
    static final float HORIZON = 0.055f;    // a ball whose centre gets this close is swallowed

    // as it is drawn, in metres at its depth
    static final float SHADOW = 0.08f, DISK_IN = 0.105f, DISK_OUT = 0.25f, LENS = 0.40f, EINSTEIN = 0.075f;
    static final float DISK_FLAT = 0.2f;     // how flat the disk looks: its tilt toward the camera
    static final float DISK_TURN = -0.17f;   // its slant on the screen, radians

    private Sky sky;

    @Override
    String key() {
        return "hole";
    }

    @Override
    String name() {
        return "THE HOLE";
    }

    @Override
    String note() {
        return "IT PULLS THE BALL AND KEEPS WHAT FALLS IN";
    }

    @Override
    String hint() {
        return "THE HOLE PULLS THE BALL";
    }

    @Override
    float height() {
        return Y;
    }

    @Override
    float posX(float t) {
        return 0.55f * (float) Math.sin(t * 0.45f);
    }

    @Override
    float posZ(float t) {
        return Game.NZ + 0.30f * (float) Math.sin(t * 0.31f + 1f);
    }

    @Override
    boolean act(Ball b, float t, float h) {
        float dx = posX(t) - b.x, dy = Y - b.y, dz = posZ(t) - b.z;
        float r2 = dx * dx + dy * dy + dz * dz;
        if (r2 < HORIZON * HORIZON) return true;
        if (r2 > REACH2) return false;
        float a = STRENGTH * power / (r2 + SOFT) * h / (float) Math.sqrt(r2 + 1e-4f);
        b.vx += dx * a;
        b.vy += dy * a;
        b.vz += dz * a;
        return false;
    }

    // ---- the look

    @Override
    void backdrop(Scene s, int[] out) {
        sky = new Sky(s.w, s.h);
        sky.paint(out, 0f);
    }

    @Override
    void keepVisible(int[] still) {
        if (sky != null) sky.keepVisible(still);
    }

    @Override
    void twinkle(int[] frame, float t) {
        if (sky != null) sky.twinkle(frame, t);
    }

    @Override
    int surface(float x, float z) {
        float lit = (float) Math.exp(-(x * x / 1.1f + (z - 1.37f) * (z - 1.37f) / 2.6f));
        return Col.mix(0xFF060D22, 0xFF10285A, 0.35f + 0.65f * lit);
    }

    @Override
    int lines() {
        return 0xFF5FD8F8;
    }

    @Override
    int grid() {
        return 0xFF1D4F96;
    }

    // a deeper, wider funnel at every level, never folding (bendA < bendC)
    @Override
    float bendA() {
        return 0.955f * bendC();
    }

    @Override
    float bendC() {
        return 0.6f + 0.3f * power;
    }

    @Override
    int light() {
        return 0xFFFF9A48;
    }

    @Override
    float lightK() {
        return 0.022f;
    }

    @Override
    float gloss() {
        return 0.32f;
    }

    @Override
    int faceTop() {
        return 0xFF2A6FA8;
    }

    @Override
    int faceBottom() {
        return 0xFF081226;
    }

    @Override
    int bevel() {
        return 0xFF8FE8FF;
    }

    @Override
    int tail() {
        return 0xFF7FE8FF;
    }

    @Override
    int netThread() {
        return 0xFFB8C8E0;
    }

    @Override
    int netTape() {
        return 0xFFF4F7FB;
    }

    @Override
    int ripple() {
        return 0xFF5FD8F8;
    }

    @Override
    int rimLight() {
        return 0xFFFF9A48;
    }

    @Override
    int hum() {
        return Synth.HUM_DRONE;
    }

    // black edges that catch the disk's light, as if cut from the dark itself
    private static final Racket MINE = new Racket(0xFFD8352B, 0xFFFF8E7A, 0xFF7A120E, 0xFF0A0A12, 0xFF5A4A6A,
            0xFF1A1622, 0xFF6A5A7A, 0xFFFF9A48, 0, 0.05f);
    private static final Racket THEIRS = new Racket(0xFF3C2E7A, 0xFF8C7CE0, 0xFF140E30, 0xFF0A0A12, 0xFF5A4A6A,
            0xFF1A1622, 0xFF6A5A7A, 0xFFFF9A48, 0xFF7FE8FF, 0.05f);

    @Override
    Racket racket(boolean player) {
        return player ? MINE : THEIRS;
    }

    /**
     * The hole bends the light of everything behind it into a ring, then shows its
     * own: the black shadow, a thin bright ring of light that orbits just outside it,
     * and the burning disk, tilted toward the camera. The far side of the disk is
     * seen bent over the top of the shadow, as a black hole shows it.
     */
    @Override
    void drawCenter(Scene s, Px f, Game g, float t) {
        Cam cam = s.cam;
        if (!cam.project(x, Y, z)) return;
        float cx = cam.sx, cy = cam.sy, sc = cam.scale();
        float lens = LENS * sc, eins = EINSTEIN * sc, rs = SHADOW * sc;
        float din = DISK_IN * sc, dout = DISK_OUT * sc;
        float flare = 1f + 0.7f * (g.ball.gone ? 0f : closeness(g.ball)) + 2f * Math.max(0f, 1f - g.goneAge * 2.2f);
        int w = f.w, h = f.h;
        int x0 = Math.max(0, (int) (cx - lens)), x1 = Math.min(w - 1, (int) (cx + lens) + 1);
        int y0 = Math.max(0, (int) (cy - lens)), y1 = Math.min(h - 1, (int) (cy + lens) + 1);
        int bw = x1 - x0 + 1, bh = y1 - y0 + 1;
        if (bw <= 1 || bh <= 1) return;
        int[] src = s.scratch(bw * bh);
        for (int y = 0; y < bh; y++) System.arraycopy(f.a, (y0 + y) * w + x0, src, y * bw, bw);
        float cosT = (float) Math.cos(DISK_TURN), sinT = (float) Math.sin(DISK_TURN);
        float spin = t * 2.2f;
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                float vx = x + 0.5f - cx, vy = y + 0.5f - cy;
                float r = (float) Math.sqrt(vx * vx + vy * vy);
                if (r >= lens) continue;
                int i = y * w + x;
                int c = f.a[i];
                // bent light from behind
                if (r > 0.5f) {
                    float k = (r - eins * eins / r) / r;
                    int bent = Scene.sample(src, bw, bh, cx + vx * k - x0 - 0.5f, cy + vy * k - y0 - 0.5f);
                    c = Col.mix(c, bent, Col.ramp(lens, lens * 0.62f, r));
                }
                // the shadow
                float black = Col.clamp01(rs - r + 0.5f);
                c = Col.mix(c, 0xFF000000, black);
                // the disk in its own frame: u along it, v across, stretched back to a circle
                float u = vx * cosT + vy * sinT, v = -vx * sinT + vy * cosT;
                float vv = v / DISK_FLAT;
                float dd = (float) Math.sqrt(u * u + vv * vv);
                float behind = v < 0f ? 1f - black : 1f;
                float e = disk(dd, din, dout, u, vv, spin) * flare;
                if (e > 0f) c = Col.addScaled(c, heat((dd - din) / (dout - din)), e * behind);
                if (r > rs) {
                    // the far side of the disk, bent up over the shadow, and a thin arc below
                    float q = (r - rs * 1.08f) / ((dout - din) * 0.32f);
                    if (q > 0f && q < 1f) {
                        float top = Col.clamp01(-v / r);
                        float arc = (float) Math.sin(q * Math.PI) * (0.12f + 0.88f * top * top);
                        c = Col.addScaled(c, heat(q * 0.7f), arc * 1.1f * flare);
                    }
                    float under = (r - rs * 1.02f) / (rs * 0.18f);
                    if (under > 0f && under < 1f && v > 0f) c = Col.addScaled(c, 0xFFFFC890, (float) Math.sin(under * Math.PI) * 0.35f * (v / r) * flare);
                    // the photon ring
                    float pr = (r - rs * 1.04f) / 0.7f;
                    c = Col.addScaled(c, 0xFFFFEED2, (float) Math.exp(-pr * pr) * 0.75f);
                }
                f.a[i] = c;
            }
        f.glow(cx, cy, dout * 1.7f, 0xFFFF7A30, 0.10f * flare);
        // a flash at the horizon when it takes the ball
        if (g.goneAge < 0.6f) {
            float a = g.goneAge / 0.6f;
            f.ringAA(cx, cy, rs * (1f + 2.5f * a), rs * (1f + 2.5f * a), 1.5f, 0xFFFFEED2, 0.8f * (1f - a));
        }
    }

    /** The glow of the disk at distance dd from the centre (in the disk's own frame). */
    private static float disk(float dd, float din, float dout, float u, float vv, float spin) {
        if (dd <= din * 0.9f || dd >= dout) return 0f;
        float n = (dd - din) / (dout - din);
        float edge = Col.ramp(din * 0.9f, din * 1.02f, dd) * Col.ramp(dout, dout * 0.7f, dd);
        float body = edge * (1.6f - 1.2f * Col.clamp01(n));
        // the near side of the spin comes toward the camera and burns brighter
        float doppler = 1f - 0.5f * u / (dd + 1e-3f);
        float ang = (float) Math.atan2(vv, u);
        // gas in fine lanes along the orbit, the lanes wavering as they turn
        float lanes = 0.8f + 0.2f * (float) Math.sin(n * 26f + 1.6f * (float) Math.sin(ang * 2f - spin * 0.9f));
        float clumps = 0.85f + 0.15f * (float) Math.sin(ang * 3f - spin * (1.2f / (0.3f + n)) + n * 4f);
        return body * doppler * lanes * clumps;
    }

    /** Colour of hot gas: white at the inner edge, through gold, to deep orange outside. */
    static int heat(float n) {
        n = Col.clamp01(n);
        if (n < 0.35f) return Col.mix(0xFFFFF6E0, 0xFFFFC060, n / 0.35f);
        return Col.mix(0xFFFFC060, 0xFFD8461E, (n - 0.35f) / 0.65f);
    }
}
