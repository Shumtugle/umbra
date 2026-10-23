package com.shumtugle.umbra;

/**
 * The double slit. In place of the net stands a screen of smoked glass with two
 * slits in it; a ball must go through one of them, and a ball that meets the
 * glass is stopped as by a net. Behind the screen, on the other half, light and
 * dark fringes lie across the table, and the ball cannot come down on a dark
 * one: past the slits it is drawn sideways to the nearest bright fringe, as if it
 * had gone through both slits at once and met itself.
 *
 * A detector lamp on the screen watches the left slit. Every so often it lights
 * up: then it is known which slit each ball went through, the fringes fade, and
 * the ball flies straight, as a plain ball would. The lamp keeps time, so it can
 * be read and waited for.
 *
 * Every landing leaves a dot on the table, and over a match the dots build up the
 * fringes, one ball at a time. In the deeper rounds a shadow ball, from a world
 * next to this one, flies through the other slit beside the real one and meets
 * it again where it lands; it vanishes whenever the lamp is on.
 */
final class Slit extends Realm {
    static final float SC = 0.38f;          // the slits' centres, either side of the middle
    static final float SW = 0.15f;          // half the width of a slit
    static final float WALL_H = 0.5f;       // height of the screen
    static final float D = 0.24f;           // distance between bright fringes
    static final float PULL = 2.0f;         // sideways pull toward the nearest bright fringe, m/s^2 per unit
    static final float PERIOD = 16f, LAMP_ON = 9f, LAMP_FADE = 0.4f;
    static final float DECK = -0.76f;

    private final java.util.Random rnd = new java.util.Random(1801);
    private float now;
    private boolean lampWas;

    // the last ball struck, for its shadow: where it left, which slit, where it lands
    private float launchZ, landZ, slitX;
    private boolean launched;

    // dots where balls came down this match
    private static final int DOTS = 400;
    private final float[] dx = new float[DOTS], dz = new float[DOTS];
    private int dn;
    private Game seen;
    private float lastBounceAge = 9f;

    @Override
    String key() {
        return "slit";
    }

    @Override
    String name() {
        return "THE DOUBLE SLIT";
    }

    @Override
    String note() {
        return "1927  ONE BALL, TWO SLITS, AND FRINGES";
    }

    @Override
    String hint() {
        return "IT LANDS ON THE BRIGHT FRINGES";
    }

    @Override
    String netWord() {
        return "GLASS";
    }


    /** The screen stands tall at the net; from high up it hides less of the far half. */
    @Override
    Cam.Eye eye() {
        return EYE;
    }

    private static final Cam.Eye EYE = new Cam.Eye(5.0f, 58f, 0.2f, 0.35f, 0.4f, -0.45f);
    @Override
    float height() {
        return WALL_H;
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

    /** How much interference there is at time t: 1 with the lamp off, 0 while it watches. */
    static float coherence(float t) {
        float p = t % PERIOD;
        if (p < LAMP_ON - LAMP_FADE) return 1f;
        if (p < LAMP_ON) return (LAMP_ON - p) / LAMP_FADE;
        if (p < PERIOD - LAMP_FADE) return 0f;
        return (p - (PERIOD - LAMP_FADE)) / LAMP_FADE;
    }

    /** The ball goes through the slit nearer its line, give or take how steady the hand was. */
    @Override
    float steer(Ball b, int side, float tx, float tz, float wobble) {
        float s = (Game.NZ - b.z) / (tz - b.z);
        if (s <= 0.05f || s >= 0.95f) return tx;
        float xn = b.x + (tx - b.x) * s;
        float sc = xn >= 0f ? SC : -SC;
        float cross = sc + (float) rnd.nextGaussian() * (0.035f + 0.06f * wobble);
        launchZ = b.z;
        landZ = tz;
        slitX = sc;
        launched = true;
        return b.x + (cross - b.x) / s;
    }

    /** The screen: open in the slits, glass everywhere else up to its top edge. */
    @Override
    int barrier(Ball b, float prevZ) {
        if (Math.abs(b.x) > Game.NET_HW || b.y > WALL_H + Ball.R || b.y < -Ball.R) return Ball.NONE;
        float side = prevZ < Game.NZ ? -1f : 1f;
        float off = (b.z - Game.NZ) * side;
        if (off >= Ball.R || b.vz * side >= 0f) return Ball.NONE;
        if (b.y < WALL_H && Math.abs(Math.abs(b.x) - SC) < SW - Ball.R * 0.5f) return Ball.NONE;
        b.z = Game.NZ + side * Ball.R;
        b.impetus = 0f;
        b.vz = -b.vz * 0.15f;
        b.vx *= 0.5f;
        b.vy = b.vy > 0f ? b.vy * 0.3f : b.vy * 0.6f;
        return Ball.NET;
    }

    /** Past the slits, the ball is drawn to the nearest bright fringe, unless the lamp is watching. */
    @Override
    boolean act(Ball b, float t, float h) {
        float beyond = (b.z - Game.NZ) * Math.signum(b.vz);
        if (beyond < 0.05f && Math.abs(b.vz) > 0.3f) return false;
        if (Math.abs(b.z - Game.NZ) < 0.05f) return false;
        float c = coherence(t);
        if (c <= 0f) return false;
        b.vx -= PULL * power * c * (float) Math.sin(6.2831853 * b.x / D) * h;
        return false;
    }

    @Override
    void tick(Game g, float dt) {
        now = g.clock;
        if (g != seen) {
            seen = g;
            dn = 0;
            launched = false;
        }
        if (g.bounceAge < lastBounceAge && Math.abs(g.bounceZ - Game.NZ) > 0.2f && dn < DOTS) {
            dx[dn] = g.bounceX;
            dz[dn] = g.bounceZ;
            dn++;
        }
        lastBounceAge = g.bounceAge;
        boolean lamp = coherence(now) < 0.5f;
        if (lamp != lampWas && g.human) g.sink.cue(Game.C_TICK, 0.7f, lamp ? 1.4f : 0.9f);
        lampWas = lamp;
    }

    // ---- the optics room

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

    /** Dark tiles, faint red light thrown across them from the far end, the table's shadow. */
    private static int floor(float x, float z, float foot) {
        float tu = x / 0.6f + 50f, tv = z / 0.6f + 50f;
        float eu = Math.min(tu - (int) tu, (int) tu + 1 - tu) * 0.6f, ev = Math.min(tv - (int) tv, (int) tv + 1 - tv) * 0.6f;
        int c = Col.mix(0xFF0C0D10, 0xFF15171C, Col.hash((int) tu, (int) tv));
        c = Col.mix(c, 0xFF050607, Math.max(Col.clamp01((0.006f - eu) / foot + 0.5f), Col.clamp01((0.006f - ev) / foot + 0.5f)));
        // the fan of light out of the slits, spreading over the floor on both sides
        float d = Math.abs(z - Game.NZ);
        if (d > 0.1f) {
            float fan = 0.5f + 0.5f * (float) Math.cos(6.2831853 * x / (D * (1f + d * 0.4f)));
            c = Col.addScaled(c, 0xFF7A1A2A, fan * fan * 0.25f / (1f + d * 0.6f) * Col.ramp(2.6f, 0.8f, Math.abs(x)));
        }
        float under = Col.ramp(0.3f, -0.05f, Math.max(Math.abs(x) - Game.HW, Math.max(-z, z - Game.L)));
        return Col.mul(c, 1f - 0.5f * under);
    }

    @Override
    int surface(float x, float z) {
        float lit = (float) Math.exp(-(x * x / 1.4f + (z - 1.37f) * (z - 1.37f) / 3.2f));
        return Col.mix(0xFF0E1014, 0xFF20242C, 0.3f + 0.7f * lit);
    }

    @Override
    int lines() {
        return 0xFFD8DCE4;
    }

    @Override
    int grid() {
        return 0xFF1A1E26;
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
        return 0xFFFF6A7A;
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
        return 0xFF2A2E36;
    }

    @Override
    int faceBottom() {
        return 0xFF08090C;
    }

    @Override
    int bevel() {
        return 0xFFB8C0CC;
    }

    @Override
    int tail() {
        return 0xFFFF8A9A;
    }

    @Override
    float tailAlpha() {
        return 0.3f;
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
        return 0xFFFF8A9A;
    }

    @Override
    int rimLight() {
        return 0xFFFF8A9A;
    }

    @Override
    int hum() {
        return Synth.HUM_OPTICS;
    }

    // anodized aluminium, as optics are made: red for the player, green for the opponent
    private static final Racket MINE = new Racket(0xFFC8303A, 0xFFF0808A, 0xFF4A0A10, 0xFF3A3E46, 0xFFB8C0CC,
            0xFF2A2E36, 0xFF7A828E, 0xFFFF5A6A, 0, 0.03f);
    private static final Racket THEIRS = new Racket(0xFF2A7A4A, 0xFF7AD89A, 0xFF0A2A16, 0xFF3A3E46, 0xFFB8C0CC,
            0xFF2A2E36, 0xFF7A828E, 0xFF5AFF8A, 0xFF5AFF8A, 0.03f);

    @Override
    Racket racket(boolean player) {
        return player ? MINE : THEIRS;
    }

    /**
     * The fringes on both halves: soft bright bands while the lamp is dark, two
     * plain stripes behind the slits while it watches; and the dots of the balls
     * that have come down so far.
     */
    @Override
    void drawOnTable(Scene s, Px f, Game g, float t) {
        Cam cam = s.cam;
        float c = coherence(t);
        for (int half = -1; half <= 1; half += 2) {
            float z0 = Game.NZ + half * 0.3f, z1 = half > 0 ? Game.L - 0.16f : 0.16f;
            for (int k = -3; k <= 3; k++) {
                float x = k * D;
                float env = (float) Math.exp(-(x * x) / 0.5f);
                band(f, cam, x, z0, z1, D * 0.42f, 0xFFFF6A7A, 0.22f * c * env);
            }
            if (c < 1f) for (int k = -1; k <= 1; k += 2) band(f, cam, k * SC, z0, z1, SW * 1.4f, 0xFFFFE0C0, 0.16f * (1f - c));
        }
        for (int i = 0; i < dn; i++) {
            if (!cam.project(dx[i], 0.002f, dz[i])) continue;
            f.discAA(cam.sx, cam.sy, 1.2f, 0xFFFFD8DE, 0.75f);
        }
    }

    private static void band(Px f, Cam cam, float x, float z0, float z1, float half, int col, float op) {
        if (op <= 0.005f) return;
        if (!cam.project(x, 0f, z0)) return;
        float ax = cam.sx, ay = cam.sy, sa = cam.scale();
        if (!cam.project(x, 0f, z1)) return;
        float wdt = half * 2f * (sa + cam.scale()) * 0.5f;
        f.lineAA(ax, ay, cam.sx, cam.sy, wdt, col, op * 0.6f);
        f.lineAA(ax, ay, cam.sx, cam.sy, wdt * 0.45f, col, op);
    }

    /** The screen of smoked glass, its two open slits glowing at the edges, and the lamp. */
    @Override
    boolean drawBarrier(Scene s, Px f, Game g, float t) {
        Cam cam = s.cam;
        float nz = Game.NZ, hw = Game.NET_HW;
        if (!cam.project(0f, WALL_H, nz)) return true;
        int y0 = (int) Math.floor(cam.sy) - 1;
        if (!cam.project(0f, 0f, nz)) return true;
        int y1 = (int) Math.ceil(cam.sy) + 1;
        float inv = 1f / cam.f;
        float c = coherence(t);
        for (int sy = Math.max(0, y0); sy <= Math.min(s.h - 1, y1); sy++) {
            float dcy = -(sy + 0.5f - cam.cy) * inv;
            float dirY = dcy * cam.cs - cam.sn, dirZ = dcy * cam.sn + cam.cs;
            if (dirZ <= 1e-4f) continue;
            float tt = (nz - cam.z) / dirZ;
            float wy = cam.y + tt * dirY;
            float dcy2 = -(sy + 1.5f - cam.cy) * inv;
            float wy2 = cam.y + (nz - cam.z) / (dcy2 * cam.sn + cam.cs) * (dcy2 * cam.cs - cam.sn);
            float footY = Math.abs(wy - wy2) + 1e-5f, footX = tt * inv;
            float covY = Scene.band(wy, 0f, WALL_H, footY);
            if (covY <= 0f) continue;
            for (int sx = 0; sx < s.w; sx++) {
                float wx = tt * (sx + 0.5f - cam.cx) * inv;
                float cov = covY * Scene.band(wx, -hw, hw, footX);
                if (cov <= 0f) continue;
                float edge = Math.abs(Math.abs(wx) - SC) - SW;          // < 0 inside a slit
                float open = Col.clamp01(-edge / footX + 0.5f);
                int i = sy * s.w + sx;
                int px = f.a[i];
                // glass: darkened, with a soft sheen, and a frame at its top and ends
                int glass = Col.mix(px, 0xFF080C14, 0.72f);
                glass = Col.addScaled(glass, 0xFF8AA0C0, 0.10f * (1f - wy / WALL_H) + 0.05f * (float) Math.sin(wx * 9f + wy * 4f));
                boolean frame = wy > WALL_H - 0.03f || Math.abs(wx) > hw - 0.03f;
                if (frame) glass = Col.mix(glass, 0xFF5A626E, 0.8f);
                float glow = Col.clamp01(1f - Math.abs(edge) / 0.04f);
                glass = Col.addScaled(glass, c > 0.5f ? 0xFFFF6A7A : 0xFFFFE0C0, glow * 0.9f);
                f.a[i] = Col.mix(px, glass, cov * (1f - open));
            }
        }
        // the slits' bright jambs, so the two openings read at a glance
        for (int k = -1; k <= 1; k += 2)
            for (int e = -1; e <= 1; e += 2) {
                float jx = k * SC + e * SW;
                if (!cam.project(jx, 0f, nz)) continue;
                float ax = cam.sx, ay = cam.sy;
                if (cam.project(jx, WALL_H, nz)) f.lineAA(ax, ay, cam.sx, cam.sy, 1.6f, c > 0.5f ? 0xFFFF8A9A : 0xFFFFE8C8, 0.85f);
            }
        // the lamp over the left slit: lit while it watches
        float lampOn = 1f - c;
        if (cam.project(-SC, WALL_H + 0.05f, nz)) {
            float lx = cam.sx, ly = cam.sy, sc = cam.scale();
            f.discAA(lx, ly, 0.045f * sc, 0xFF3A2A14, 1f);
            f.discAA(lx, ly, 0.03f * sc, Col.mix(0xFF4A3A20, 0xFFFFF0B0, lampOn), 1f);
            if (lampOn > 0f) {
                f.glow(lx, ly, 0.3f * sc, 0xFFFFE0A0, 0.6f * lampOn);
                if (cam.project(-SC, 0.05f, nz)) f.lineAA(lx, ly, cam.sx, cam.sy, 0.12f * sc, 0xFFFFE0A0, 0.12f * lampOn);
            }
        }
        return true;
    }

    /** The shadow ball from the world next door, through the other slit; gone while the lamp watches. */
    @Override
    void drawGuide(Scene s, Px f, Game g) {
        if (power < 1.2f || !launched) return;
        Ball b = g.ball;
        float c = coherence(now);
        if (c <= 0.02f || b.gone || g.state != Game.RALLY) return;
        float span = landZ - launchZ;
        if (Math.abs(span) < 0.2f) return;
        float p = Col.clamp01((b.z - launchZ) / span), pn = Col.clamp01((Game.NZ - launchZ) / span);
        float bump = p < pn ? p / Math.max(0.05f, pn) : (1f - p) / Math.max(0.05f, 1f - pn);
        bump = (float) Math.sin(Col.clamp01(bump) * Math.PI / 2);
        if (!s.cam.project(b.x - 2f * slitX * bump, b.y, b.z)) return;
        float r = Ball.R * Scene.LOOK * s.cam.scale();
        f.glow(s.cam.sx, s.cam.sy, r * 2.4f, 0xFFC8A0FF, 0.25f * c);
        f.discAA(s.cam.sx, s.cam.sy, r, 0xFFE8D8FF, 0.35f * c);
    }

    @Override
    void drawCenter(Scene s, Px f, Game g, float t) {
        // the screen is the thing here, drawn in place of the net
    }
}
