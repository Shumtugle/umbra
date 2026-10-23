package com.shumtugle.umbra;

/**
 * The darkroom. The ball flies a plain arc here, and the law is not in the ball
 * but in the eye: everything over the net, the other half of the table, the
 * opponent and the ball while it is over there, is seen through another eye. On
 * this side of the net the world is as it is; on that side it is an image.
 *
 * Every match takes one eye from a long list, from a bag that gives each of them
 * once before any comes back. The first eyes are instruments: a negative, a
 * daguerreotype that turns negative now and then, a cyanotype, old sepia, a
 * halftone screen, a mosaic, a heat camera, X-rays, a colour-blind eye, a long
 * exposure. The rest are painters, each by a manner and never by a picture.
 * Every eye has its catch, something it hides or shows that the plain eye would
 * not: the mosaic tells where the ball is only to the nearest cell, the heat
 * camera shows only what was struck, the long exposure leaves the ball faint at
 * the head of its own trail.
 *
 * The image is made at the depth of the net: everything farther has been drawn
 * by then and passes through the eye, everything nearer is drawn after it and
 * stays true, so a ball on this side flying high over the net is never touched.
 * A red safelight hangs over the net on this side, in our room.
 */
final class Darkroom extends Realm {
    static final float LAMP_Y = 1.05f, LAMP_Z = Game.NZ - 0.5f;
    static final float DECK = -0.76f;
    static final float LINE_Y = 0.45f, LINE_Z = Game.L + 0.5f;

    static final int NEGATIVE = 0, DAGUERREOTYPE = 1, CYANOTYPE = 2, SEPIA = 3, HALFTONE = 4, MOSAIC = 5, HEAT = 6,
            XRAY = 7, DALTON = 8, EXPOSURE = 9, VAN_GOGH = 10, MONET = 11, MANET = 12, RENOIR = 13, DEGAS = 14,
            CEZANNE = 15, GAUGUIN = 16, SEURAT = 17, MATISSE = 18, CHAGALL = 19, MALEVICH = 20, KLIMT = 21, MUNCH = 22,
            KANDINSKY = 23, PICASSO = 24, DALI = 25, MIRO = 26, MAGRITTE = 27, MODIGLIANI = 28, KLEE = 29, POLLOCK = 30;

    /** The eyes' card names, under the realm's stories. */
    static final String[] EYES = {"negative", "daguerreotype", "cyanotype", "sepia", "halftone", "mosaic", "heat",
            "xray", "dalton", "exposure", "vangogh", "monet", "manet", "renoir", "degas", "cezanne", "gauguin",
            "seurat", "matisse", "chagall", "malevich", "klimt", "munch", "kandinsky", "picasso", "dali", "miro",
            "magritte", "modigliani", "klee", "pollock"};

    /** What the far half is seen through, as the screen says it. */
    static final String[] SEEN = {"A NEGATIVE", "A DAGUERREOTYPE", "A CYANOTYPE", "OLD SEPIA", "A HALFTONE SCREEN",
            "A MOSAIC", "A HEAT CAMERA", "X-RAYS", "A COLOUR-BLIND EYE", "A LONG EXPOSURE", "VAN GOGH", "MONET",
            "MANET", "RENOIR", "DEGAS", "CEZANNE", "GAUGUIN", "SEURAT", "MATISSE", "CHAGALL", "MALEVICH", "KLIMT",
            "MUNCH", "KANDINSKY", "PICASSO", "DALI", "MIRO", "MAGRITTE", "MODIGLIANI", "KLEE", "POLLOCK"};

    private int eye;
    private Game seen;
    private int lastShots, striker = -1, splashes;
    private float strikeAt = -9f, now;

    // the far half of the frame: rows above the foot of the net
    private int w = -1, h = -1, yb;
    private float u, midR;
    private int[] src, keep, tmp, paint;
    private float[] cover, flowX, flowY, grain, heat;
    private boolean fresh = true;
    private Cam seenCam;
    private final java.util.Random rnd = new java.util.Random(1839);

    @Override
    String key() {
        return "darkroom";
    }

    @Override
    String name() {
        return "THE DARKROOM";
    }

    @Override
    String note() {
        return "THEIR HALF THROUGH " + SEEN[eye];
    }

    @Override
    String hint() {
        return "OVER THE NET YOU SEE AN IMAGE, NOT THE BALL";
    }

    @Override
    int seeds() {
        return EYES.length;
    }

    @Override
    void seed(int i) {
        int n = EYES.length;
        int e = ((i % n) + n) % n;
        if (e != eye) fresh = true;
        eye = e;
    }

    @Override
    int seed() {
        return eye;
    }

    @Override
    String seedName(int i) {
        return SEEN[((i % SEEN.length) + SEEN.length) % SEEN.length];
    }

    @Override
    String[] stories() {
        return new String[]{"darkroom/" + EYES[eye], "darkroom"};
    }


    /** High, so that the image over the net is large. */
    @Override
    Cam.Eye eye() {
        return EYE;
    }

    private static final Cam.Eye EYE = new Cam.Eye(5.0f, 58f, 0.2f, 0.35f, 0.4f, -0.45f);
    @Override
    float height() {
        return LAMP_Y;
    }

    @Override
    float posX(float t) {
        return 0f;
    }

    @Override
    float posZ(float t) {
        return LAMP_Z;
    }

    @Override
    float closeness(Ball b) {
        return 0f;
    }

    @Override
    boolean act(Ball b, float t, float h) {
        return false;
    }

    @Override
    void tick(Game g, float dt) {
        now = g.clock;
        if (g != seen) {
            seen = g;
            lastShots = g.shots;
            striker = -1;
            strikeAt = -9f;
            splashes = 0;
            fresh = true;
        }
        if (g.shots != lastShots) {
            lastShots = g.shots;
            striker = g.hitter;
            strikeAt = now;
            if (eye == POLLOCK) splashes += power > 1.3f ? 2 : 1;
        }
    }

    // ---- the eye, laid over the far half at the depth of the net

    private void prepare(Scene s) {
        if (s.cam == seenCam && src != null) return;
        seenCam = s.cam;
        w = s.w;
        h = s.h;
        Cam cam = s.cam;
        yb = cam.project(0f, 0f, Game.NZ) ? Math.max(0, Math.min(h, (int) Math.floor(cam.sy))) : 0;
        u = w / 274f;
        midR = cam.project(0f, 0f, 2.05f) ? Math.max(3.2f, Ball.R * Scene.LOOK * cam.scale()) : 4f;
        int n = Math.max(1, w * yb);
        src = new int[n];
        keep = new int[n];
        tmp = new int[n];
        paint = new int[n];
        cover = new float[n];
        heat = new float[n];
        flowX = null;
        flowY = null;
        fresh = true;
    }

    /** The image: the far half through today's eye. The ordinary net is drawn over it afterwards. */
    @Override
    boolean drawBarrier(Scene s, Px f, Game g, float t) {
        prepare(s);
        if (yb <= 2) return false;
        System.arraycopy(f.a, 0, src, 0, w * yb);
        if (fresh) {
            System.arraycopy(src, 0, keep, 0, w * yb);
            java.util.Arrays.fill(cover, 0f);
            fresh = false;
        }
        Cam cam = s.cam;
        Ball b = g.ball;
        boolean far = false;
        float bx = 0f, by = 0f, br = 0f;
        if (g.state != Game.OVER && !b.gone && cam.project(b.x, b.y, b.z)) {
            float zb = cam.zc;
            bx = cam.sx;
            by = cam.sy;
            br = Math.max(3.2f, Ball.R * Scene.LOOK * cam.scale());
            if (cam.project(0f, Game.NH * 0.5f, Game.NZ)) far = zb > cam.zc;
        }
        int[] a = f.a;
        switch (eye) {
            case NEGATIVE: negative(a); break;
            case DAGUERREOTYPE: daguerreotype(a, t); break;
            case CYANOTYPE: cyanotype(a); break;
            case SEPIA: sepia(a, t); break;
            case HALFTONE: halftone(a); break;
            case MOSAIC: cells(a, Math.max(3, Math.round((6f + 2f * power) * u)), false); break;
            case HEAT: heat(a, s, g, far, bx, by, br); break;
            case XRAY: xray(a); break;
            case DALTON: dalton(a); break;
            case EXPOSURE: exposure(a); break;
            case VAN_GOGH: vanGogh(f, far, bx, by, br); break;
            case MONET: monet(a); break;
            case MANET: manet(a); break;
            case RENOIR: renoir(f, t); break;
            case DEGAS: degas(a, g, t); break;
            case CEZANNE: cezanne(a); break;
            case GAUGUIN: gauguin(a); break;
            case SEURAT: seurat(a); break;
            case MATISSE: matisse(a); break;
            case CHAGALL: chagall(f, s, g, t, far, br); break;
            case MALEVICH: malevich(f, s, g, far, bx, by, br); break;
            case KLIMT: klimt(a, t); break;
            case MUNCH: munch(a, t); break;
            case KANDINSKY: kandinsky(f, t, far, bx, by, br); break;
            case PICASSO: picasso(a); break;
            case DALI: dali(a, t); break;
            case MIRO: miro(f, far, bx, by, br); break;
            case MAGRITTE: magritte(f, far, bx, by, br); break;
            case MODIGLIANI: modigliani(a); break;
            case KLEE: cells(a, Math.max(3, Math.round(6f * u)), true); break;
            default: pollock(a); break;
        }
        return false;
    }

    // ---- small tools

    private int at(int x, int y) {
        if (x < 0) x = 0;
        else if (x >= w) x = w - 1;
        if (y < 0) y = 0;
        else if (y >= yb) y = yb - 1;
        return src[y * w + x];
    }

    private int at(float x, float y) {
        return at((int) Math.floor(x), (int) Math.floor(y));
    }

    private static float lum(int c) {
        return (0.299f * ((c >> 16) & 255) + 0.587f * ((c >> 8) & 255) + 0.114f * (c & 255)) / 255f;
    }

    private static int rgb(float r, float g, float b) {
        return Col.rgb((int) r, (int) g, (int) b);
    }

    /** One colour times another, as light through a filter. */
    private static int tint(int c, int k) {
        return Col.rgb(((c >> 16) & 255) * ((k >> 16) & 255) / 255, ((c >> 8) & 255) * ((k >> 8) & 255) / 255,
                (c & 255) * (k & 255) / 255);
    }

    private static final float[] SINE = new float[4096];

    static {
        for (int i = 0; i < SINE.length; i++) SINE[i] = (float) Math.sin(i * 6.283185307179586 / SINE.length);
    }

    /** A quick sine for work done at every pixel, good to a thousandth. */
    private static float sin(float x) {
        return SINE[(int) (x * (4096f / 6.2831853f)) & 4095];
    }

    private static float smooth(float t) {
        t = Col.clamp01(t);
        return t * t * (3f - 2f * t);
    }

    /** Smooth noise in 0..1. */
    private static float noise(float x, float y) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        float fx = x - ix, fy = y - iy;
        fx = fx * fx * (3f - 2f * fx);
        fy = fy * fy * (3f - 2f * fy);
        float p = Col.hash(ix, iy), q = Col.hash(ix + 1, iy), r = Col.hash(ix, iy + 1), s = Col.hash(ix + 1, iy + 1);
        return p + (q - p) * fx + (r - p) * fy + (p - q - r + s) * fx * fy;
    }

    /** Colour along a row of stops, evenly spaced from 0 to 1. */
    private static int ramp(int[] stops, float v) {
        v = Col.clamp01(v) * (stops.length - 1);
        int i = Math.min(stops.length - 2, (int) v);
        return Col.mix(stops[i], stops[i + 1], v - i);
    }

    /** How much a pixel is an edge: the change of brightness around it. */
    private float edge(int x, int y) {
        return Math.abs(lum(at(x + 1, y)) - lum(at(x - 1, y))) + Math.abs(lum(at(x, y + 1)) - lum(at(x, y - 1)));
    }

    private void fillBlend(Px f, float cx, float cy, float half, float halfY, int c, float op) {
        int x0 = Math.max(0, Math.round(cx - half)), x1 = Math.min(w - 1, Math.round(cx + half) - 1);
        int y0 = Math.max(0, Math.round(cy - halfY)), y1 = Math.min(yb - 1, Math.round(cy + halfY) - 1);
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) f.blend(x, y, c, op);
    }

    // ---- the instruments

    /** A colour negative, with the orange cast of its film. */
    private void negative(int[] a) {
        for (int i = 0, n = w * yb; i < n; i++) {
            int c = src[i];
            a[i] = rgb(255 - ((c >> 16) & 255), (255 - ((c >> 8) & 255)) * 0.86f, (255 - (c & 255)) * 0.7f);
        }
    }

    /** Silver on a mirror: at one angle a positive, at another a negative, and it turns over by itself. */
    private void daguerreotype(int[] a, float t) {
        float k = smooth(0.5f + 1.8f * (float) Math.sin(t * 0.55f));
        for (int y = 0; y < yb; y++)
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float l = lum(src[i]);
                float v = l + (1f - 2f * l) * k;
                float dx = (x - w * 0.5f) / (w * 0.55f), dy = (y - yb * 0.55f) / (yb * 0.7f);
                float vig = Col.clamp01(1f - 0.55f * (dx * dx + dy * dy));
                int c = Col.mix(0xFF181C22, 0xFFDDE4EA, v * (0.4f + 0.6f * vig));
                a[i] = Col.mix(c, 0xFF4A3A5C, (1f - vig) * 0.55f);
            }
    }

    /** Prussian blue: every shade of the world in one colour, on paper. */
    private void cyanotype(int[] a) {
        for (int y = 0; y < yb; y++)
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float l = lum(src[i]);
                l = l * (1.25f - 0.25f * l);
                l += (Col.hash(x, y) - 0.5f) * 0.06f;
                a[i] = Col.mix(0xFF0A2560, 0xFFE6EEF4, l);
            }
    }

    /** An old print: brown, flat, scratched and dusty. The ball sinks into the wood. */
    private void sepia(int[] a, float t) {
        int frame = (int) (t * 12f);
        for (int y = 0; y < yb; y++)
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float l = 0.2f + 0.58f * lum(src[i]) + (Col.hash(x * 3 + frame, y) - 0.5f) * 0.07f;
                float dx = (x - w * 0.5f) / (w * 0.6f), dy = (y - yb * 0.5f) / (yb * 0.8f);
                l *= 1f - 0.35f * (dx * dx + dy * dy);
                if (Col.hash(x, frame) > 0.993f) l += 0.15f;
                if (Col.hash(x + y * 131, frame) > 0.9993f) l -= 0.35f;
                a[i] = Col.mix(0xFF22160C, 0xFFF0DCB4, l);
            }
    }

    /** A newspaper's screen of dots, turned at forty-five degrees: big dots for dark, none for light. */
    private void halftone(int[] a) {
        float cell = 4.2f * u;
        for (int y = 0; y < yb; y++)
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float l = lum(src[i]);
                float p = (x + y) * 0.7071f / cell, q = (x - y) * 0.7071f / cell;
                float fp = p - (float) Math.floor(p) - 0.5f, fq = q - (float) Math.floor(q) - 0.5f;
                float d = (float) Math.sqrt(fp * fp + fq * fq);
                float rr = 0.64f * (float) Math.sqrt(1f - l);
                a[i] = Col.mix(0xFFEDE6D3, 0xFF1A1A24, Col.clamp01((rr - d) * cell + 0.5f));
            }
    }

    /**
     * Square cells of one colour each: the plain mosaic, or the painter's, with
     * every cell washed in a colour of its own and a thin line between them.
     */
    private void cells(int[] a, int cell, boolean painted) {
        int[] wash = {0xFFE8A0A0, 0xFFE8C880, 0xFF80B8B0, 0xFFA898D0, 0xFFD8C8A8, 0xFF90A8D8};
        for (int cy = yb; cy > 0; cy -= cell) {
            int y0 = Math.max(0, cy - cell);
            for (int x0 = 0; x0 < w; x0 += cell) {
                int x1 = Math.min(w, x0 + cell);
                long r = 0, g = 0, b = 0;
                int n = 0;
                for (int y = y0; y < cy; y++)
                    for (int x = x0; x < x1; x++) {
                        int c = src[y * w + x];
                        r += (c >> 16) & 255;
                        g += (c >> 8) & 255;
                        b += c & 255;
                        n++;
                    }
                if (n == 0) continue;
                int c = Col.rgb((int) (r / n), (int) (g / n), (int) (b / n));
                if (painted) {
                    float l = lum(c);
                    int k = wash[(int) (Col.hash(x0 / cell, cy / cell) * wash.length)];
                    c = Col.mix(c, Col.mul(k, 0.35f + 1.1f * l), 0.55f);
                }
                for (int y = y0; y < cy; y++)
                    for (int x = x0; x < x1; x++) {
                        boolean seam = x == x0 || y == y0;
                        a[y * w + x] = seam ? (painted ? Col.mix(c, 0xFF5A4030, 0.5f) : Col.mul(c, 0.7f)) : c;
                    }
            }
        }
    }

    private static final int[] IRON = {0xFF06030E, 0xFF1E0A4A, 0xFF7A1070, 0xFFD0302A, 0xFFFF9A20, 0xFFFFF4C0};

    /** Only warmth shows: the struck ball, the opponent's hand and paddle, the spot where the ball last hit. */
    private void heat(int[] a, Scene s, Game g, boolean far, float bx, float by, float br) {
        int n = w * yb;
        for (int i = 0; i < n; i++) heat[i] = 0.06f + 0.3f * lum(src[i]);
        Cam cam = s.cam;
        if (far) warm(bx, by, br * 1.5f, 0.9f);
        Game.Paddle p = g.pad[1];
        if (cam.project(p.x, Game.PY, p.z)) {
            float struck = striker == 1 ? (float) Math.exp(-(now - strikeAt) / 1.2f) : 0f;
            warm(cam.sx, cam.sy, 0.09f * cam.scale(), 0.3f + 0.5f * struck);
        }
        if (g.bounceZ > Game.NZ && g.bounceAge < 4f && cam.project(g.bounceX, 0f, g.bounceZ))
            warm(cam.sx, cam.sy, 0.05f * cam.scale(), 0.7f * (float) Math.exp(-g.bounceAge / 1.1f));
        for (int i = 0; i < n; i++) a[i] = ramp(IRON, heat[i]);
    }

    private void warm(float cx, float cy, float r, float k) {
        int x0 = Math.max(0, (int) (cx - 3 * r)), x1 = Math.min(w - 1, (int) (cx + 3 * r));
        int y0 = Math.max(0, (int) (cy - 3 * r)), y1 = Math.min(yb - 1, (int) (cy + 3 * r));
        float inv = 1f / (2f * r * r);
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                heat[y * w + x] += k * (float) Math.exp(-(dx * dx + dy * dy) * inv);
            }
    }

    private static final int[] GLOW = {0xFF020812, 0xFF1A4A5A, 0xFFD8F4FF};

    /** Density as light: edges and dense things glow blue-white on black. */
    private void xray(int[] a) {
        for (int y = 0; y < yb; y++)
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float v = 0.08f + 0.32f * lum(src[i]) + Math.min(1f, edge(x, y) * 2.4f);
                a[i] = ramp(GLOW, v);
            }
    }

    /** An eye with no green cones: red and green fall together into one olive. */
    private void dalton(int[] a) {
        for (int i = 0, n = w * yb; i < n; i++) {
            int c = src[i];
            float r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
            a[i] = rgb(0.625f * r + 0.375f * g, 0.7f * r + 0.3f * g, 0.3f * g + 0.7f * b);
        }
    }

    /** The shutter stays open: what stands still is sharp, what moves is a faint trail, faintest where it is now. */
    private void exposure(int[] a) {
        float k = 0.2f - 0.04f * Math.min(1f, power - 1f);
        for (int i = 0, n = w * yb; i < n; i++) {
            int c = Col.mix(keep[i], src[i], k);
            keep[i] = c;
            a[i] = c;
        }
    }

    // ---- the painters

    /** The brush follows the wind: strokes along swirling currents, blue and yellow, rings about the ball. */
    private void vanGogh(Px f, boolean far, float bx, float by, float br) {
        if (flowX == null) {
            flowX = new float[w * yb];
            flowY = new float[w * yb];
            grain = new float[w * yb];
            float[][] eddy = {{0.22f, 0.25f, 1f}, {0.72f, 0.4f, -1f}, {0.48f, 0.82f, 1f}, {0.9f, 0.1f, 1f}};
            float reach = 0.22f * w;
            for (int y = 0; y < yb; y++)
                for (int x = 0; x < w; x++) {
                    float th = 0.5f * (float) Math.sin(y * 0.07f / u + x * 0.03f / u);
                    float vx = (float) Math.cos(th), vy = 0.4f * (float) Math.sin(th);
                    for (float[] e : eddy) {
                        float dx = x - e[0] * w, dy = y - e[1] * yb;
                        float d2 = dx * dx + dy * dy + 1f, d = (float) Math.sqrt(d2);
                        float k = 2.2f * e[2] * reach * reach / (d2 + reach * reach);
                        vx += -dy / d * k;
                        vy += dx / d * k;
                    }
                    float m = (float) Math.sqrt(vx * vx + vy * vy) + 1e-4f;
                    float fx = vx / m, fy = vy / m;
                    flowX[y * w + x] = fx;
                    flowY[y * w + x] = fy;
                    // each stroke a little lighter or darker than its neighbours
                    float across = (x * fy - y * fx) / (2.2f * u), along = (x * fx + y * fy) / (6f * u);
                    int lane = (int) Math.floor(across);
                    grain[y * w + x] = 0.86f + 0.28f * Col.hash(lane, (int) Math.floor(along + Col.hash(lane, 7) * 3f));
                }
        }
        int[] a = f.a;
        float step = 1.8f * u;
        for (int y = 0; y < yb; y++)
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float fx = flowX[i] * step, fy = flowY[i] * step;
                int r = 0, g = 0, b = 0;
                for (int k = -2; k <= 2; k++) {
                    int c = at((int) (x + 0.5f + fx * k), (int) (y + 0.5f + fy * k));
                    r += (c >> 16) & 255;
                    g += (c >> 8) & 255;
                    b += c & 255;
                }
                int c = Col.rgb(r / 5, g / 5, b / 5);
                c = Col.mix(c, lum(c) < 0.42f ? 0xFF14307A : 0xFFF2C838, 0.32f);
                a[i] = Col.mul(c, grain[i]);
            }
        if (far) {
            f.glow(bx, by, br * 3f, 0xFFFFE070, 0.35f);
            f.ringAA(bx, by, br * 1.9f, br * 1.9f, 1.2f * u, 0xFFFFE070, 0.6f);
            f.ringAA(bx, by, br * 2.8f, br * 2.8f, 1f * u, 0xFFF2C838, 0.35f);
        }
    }

    private static final int[] DAY = {0xFFC8C8FF, 0xFFFFF4DC, 0xFFFF9E70, 0xFFC8C8FF};

    /** Broken dabs of colour, no black anywhere, and a light that goes from morning to evening over the match. */
    private void monet(int[] a) {
        int light = ramp(DAY, (now / 150f) % 1f);
        float cell = 3f * u;
        for (int y = 0; y < yb; y++)
            for (int x = 0; x < w; x++) {
                int cx = (int) (x / cell), cy = (int) (y / cell);
                float jx = (Col.hash(cx, cy) - 0.5f) * 5f * u, jy = (Col.hash(cy + 77, cx) - 0.5f) * 3f * u;
                int c = Col.mix(at(x + jx, y + jy), at(x - jy * 0.5f, y + jx * 0.3f), 0.5f);
                c = tint(c, light);
                a[y * w + x] = Col.mix(c, Col.mul(light, 0.75f), 0.34f);
            }
    }

    /** Flat colour, true black and no half tones: the shadow of the ball is as bold as the ball. */
    private void manet(int[] a) {
        for (int i = 0, n = w * yb; i < n; i++) {
            int c = src[i];
            float l = lum(c);
            int mid = Col.mix(Col.mul(c, 0.5f / Math.max(0.05f, l)), 0xFF6A6658, 0.4f);
            int light = Col.mix(c, 0xFFF4EEE0, 0.6f);
            int v = Col.mix(0xFF0A0A0C, mid, smooth((l - 0.28f) / 0.04f));
            a[i] = Col.mix(v, light, smooth((l - 0.62f) / 0.04f));
        }
    }

    /** Warm and soft, with round spots of sun through the leaves drifting over it; some look just like the ball. */
    private void renoir(Px f, float t) {
        int[] a = f.a;
        int d = Math.max(1, Math.round(2f * u));
        for (int y = 0; y < yb; y++)
            for (int x = 0; x < w; x++) {
                int c0 = at(x, y), c1 = at(x + d, y), c2 = at(x - d, y), c3 = at(x, y + d), c4 = at(x, y - d);
                int r = ((c0 >> 16) & 255) * 2 + ((c1 >> 16) & 255) + ((c2 >> 16) & 255) + ((c3 >> 16) & 255) + ((c4 >> 16) & 255);
                int g = ((c0 >> 8) & 255) * 2 + ((c1 >> 8) & 255) + ((c2 >> 8) & 255) + ((c3 >> 8) & 255) + ((c4 >> 8) & 255);
                int b = (c0 & 255) * 2 + (c1 & 255) + (c2 & 255) + (c3 & 255) + (c4 & 255);
                float shade = 0.82f + 0.36f * noise(x / (18f * u) + t * 0.1f, y / (12f * u));
                a[y * w + x] = rgb(r / 6f * 1.06f * shade, g / 6f * shade, b / 6f * 0.88f * shade);
            }
        for (int k = 0; k < 7; k++) {
            float sx = w * (0.5f + 0.42f * (float) Math.sin(t * 0.13f * (k + 1) + k * 1.7f));
            float sy = yb * (0.45f + 0.4f * (float) Math.sin(t * 0.11f * (k + 2) + k));
            f.glow(sx, sy, midR * 2.2f, 0xFFFFF0D0, 0.25f);
            f.discAA(sx, sy, midR * 1.05f, 0xFFFFF6E0, 0.55f);
        }
    }

    /** Pastel strokes, and a frame that cuts the scene off at one side, as a snapshot would; it changes side each point. */
    private void degas(int[] a, Game g, float t) {
        boolean left = ((g.score[0] + g.score[1]) & 1) == 0;
        float cut = w * (0.2f + 0.08f * (float) Math.sin(t * 0.3f));
        for (int y = 0; y < yb; y++) {
            float jag = (Col.hash(y / Math.max(1, (int) (3 * u)), 5) - 0.5f) * 3f * u;
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float off = left ? x - cut - jag : w - x - cut - jag;
                if (off < 0f) {
                    a[i] = Col.mix(0xFF3E3228, 0xFF4A3C30, Col.hash(x, y));
                    continue;
                }
                int c = src[i];
                float l = lum(c);
                c = Col.mix(c, 0xFFF2E2D2, 0.22f);
                if (l < 0.4f) c = Col.mix(c, 0xFF2A5A5A, 0.2f);
                float stroke = sin((x * 0.8f + y * 1.1f) / u * 1.4f);
                a[i] = Col.mul(c, 0.94f + 0.06f * stroke);
            }
        }
    }

    /** The world built of short slanting strokes laid side by side, each one flat, in earth, green and blue. */
    private void cezanne(int[] a) {
        float ca = 0.5f, sa = 0.866f, len = 5.5f * u, wid = 2.4f * u;
        for (int y = 0; y < yb; y++)
            for (int x = 0; x < w; x++) {
                float p = x * ca + y * sa, q = -x * sa + y * ca;
                int iq = (int) Math.floor(q / wid);
                int ip = (int) Math.floor(p / len + Col.hash(iq, 3));
                float pc = (ip + 0.5f - Col.hash(iq, 3)) * len, qc = (iq + 0.5f) * wid;
                int c = at(pc * ca - qc * sa, pc * sa + qc * ca);
                float l = lum(c);
                c = Col.mix(c, l < 0.5f ? 0xFF2A4A5E : 0xFFD49A5A, 0.22f);
                float gap = q / wid - iq;
                float k = (0.93f + 0.14f * Col.hash(ip, iq)) * (gap < 0.12f ? 0.88f : 1f);
                a[y * w + x] = Col.mul(c, k);
            }
    }

    /** Flat fields of warm colour inside thick dark outlines: the ball is known by its rim. */
    private void gauguin(int[] a) {
        int n = w * yb;
        for (int i = 0; i < n; i++) {
            int c = src[i];
            int r = Col.clamp((int) (((c >> 16) & 255) * 1.08f)), g = (c >> 8) & 255, b = (int) ((c & 255) * 0.82f);
            tmp[i] = Col.rgb(Math.round(r / 85f) * 85, Math.round(g / 85f) * 85, Math.round(b / 85f) * 85);
        }
        int k = Math.max(1, Math.round(u));
        for (int y = 0; y < yb; y++)
            for (int x = 0; x < w; x++) {
                int i = y * w + x, c = tmp[i];
                int cr = x + k < w ? tmp[i + k] : c, cd = y + k < yb ? tmp[i + k * w] : c;
                int diff = Math.abs(Col.r(c) - Col.r(cr)) + Math.abs(Col.g(c) - Col.g(cr)) + Math.abs(Col.b(c) - Col.b(cr))
                        + Math.abs(Col.r(c) - Col.r(cd)) + Math.abs(Col.g(c) - Col.g(cd)) + Math.abs(Col.b(c) - Col.b(cd));
                a[i] = diff > 60 ? 0xFF1C140E : Col.mix(c, 0xFFE8A040, 0.12f);
            }
    }

    /** Dots of pure colour on bare paper, mixed only in the eye. */
    private void seurat(int[] a) {
        float cell = 4.5f * u;
        for (int y = 0; y < yb; y++)
            for (int x = 0; x < w; x++) {
                int ix = (int) (x / cell), iy = (int) (y / cell);
                float h1 = Col.hash(ix, iy), h2 = Col.hash(iy * 7 + 3, ix * 5 + 1);
                float dcx = (ix + 0.5f + (h1 - 0.5f) * 0.3f) * cell, dcy = (iy + 0.5f + (h2 - 0.5f) * 0.3f) * cell;
                int cc = at(dcx, dcy);
                float r = (cc >> 16) & 255, g = (cc >> 8) & 255, b = cc & 255, l = lum(cc) * 255f;
                // most dots are the colour itself made purer; one in four is its opposite, to make it sing
                int dot = rgb(l + (r - l) * 1.9f, l + (g - l) * 1.9f, l + (b - l) * 1.9f);
                if (Col.hash(ix * 13 + 5, iy * 17 + 11) < 0.25f) dot = rgb(l - (r - l) * 1.6f, l - (g - l) * 1.6f, l - (b - l) * 1.6f);
                float dx = x + 0.5f - dcx, dy = y + 0.5f - dcy;
                float cov = Col.clamp01(0.42f * cell - (float) Math.sqrt(dx * dx + dy * dy) + 0.5f);
                a[y * w + x] = Col.mix(Col.mix(0xFFF0E8D8, cc, 0.6f), dot, cov * 0.9f);
            }
    }

    private static final int[] FAUVE = {0xFF1E2A78, 0xFF22885A, 0xFFE2402C, 0xFFF29AB0, 0xFFFFF2A8};

    /** Bold flat colour chosen by brightness alone: the table may be red, the shadow blue. */
    private void matisse(int[] a) {
        for (int i = 0, n = w * yb; i < n; i++) {
            float v = lum(src[i]) * (FAUVE.length - 1);
            int k = Math.min(FAUVE.length - 2, (int) v);
            a[i] = Col.mix(FAUVE[k], FAUVE[k + 1], smooth((v - k - 0.5f) * 6f + 0.5f));
        }
    }

    /** Everything floats in a blue dream, and the ball's shadow drifts loose from the ball. */
    private void chagall(Px f, Scene s, Game g, float t, boolean far, float br) {
        int[] a = f.a;
        for (int y = 0; y < yb; y++)
            for (int x = 0; x < w; x++) {
                int c = at(x + 1.5f * u * sin(y * 0.05f / u + t * 0.5f),
                        y + 3f * u * sin(x * 0.04f / u + t * 0.7f) - 2f * u * sin(t * 0.4f));
                float l = lum(c);
                a[y * w + x] = Col.mix(tint(c, 0xFF8A8AF0), Col.mix(c, 0xFFFFF0C8, 0.3f), smooth((l - 0.55f) * 3f));
            }
        if (far) {
            Ball b = g.ball;
            Cam cam = s.cam;
            if (cam.project(b.x + 0.22f * (float) Math.sin(t * 1.3f), 0.001f, b.z + 0.15f * (float) Math.cos(t * 0.9f)))
                f.ellipseAA(cam.sx, cam.sy, br * 1.1f, br * 0.5f, 0xFF101030, 0.45f);
        }
    }

    /** White canvas and flat geometry: the table a few thin lines, the paddle a red block, the ball a black square. */
    private void malevich(Px f, Scene s, Game g, boolean far, float bx, float by, float br) {
        int[] a = f.a;
        for (int y = 0; y < yb; y++)
            for (int x = 0; x < w; x++) a[y * w + x] = Col.mix(0xFFEDE8DA, 0xFFE2DCCB, Col.hash(x, y) * 0.6f);
        // a still composition at the sides, larger than anything in play
        f.lineAA(0.06f * w, 0.18f * yb, 0.3f * w, 0.05f * yb, 7f * u, 0xFFC8282A, 0.95f);
        f.lineAA(0.08f * w, 0.32f * yb, 0.22f * w, 0.36f * yb, 2.5f * u, 0xFF141414, 0.95f);
        f.lineAA(0.74f * w, 0.08f * yb, 0.95f * w, 0.2f * yb, 4f * u, 0xFFE8B820, 0.95f);
        f.lineAA(0.8f * w, 0.3f * yb, 0.9f * w, 0.28f * yb, 9f * u, 0xFF2A3A8A, 0.9f);
        // the table: its far half in thin grey lines
        Cam cam = s.cam;
        float hw = Game.HW, nz = Game.NZ, l = Game.L;
        float[][] seg = {{-hw, nz, -hw, l}, {hw, nz, hw, l}, {-hw, l, hw, l}, {0f, nz, 0f, l}};
        for (float[] e : seg) {
            if (!cam.project(e[0], 0f, e[1])) continue;
            float ax = cam.sx, ay = cam.sy;
            if (cam.project(e[2], 0f, e[3])) f.lineAA(ax, ay, cam.sx, cam.sy, 0.9f * u, 0xFF8A8478, 0.7f);
        }
        Game.Paddle p = g.pad[1];
        if (cam.project(p.x, Game.PY, p.z)) {
            float sc = 0.085f * cam.scale();
            fillBlend(f, cam.sx, cam.sy, sc, sc * 0.7f, 0xFFC8282A, 1f);
        }
        if (far) {
            Ball b = g.ball;
            if (cam.project(b.x, 0f, b.z)) fillBlend(f, cam.sx, cam.sy, br * 0.9f, br * 0.45f, 0xFF8A8478, 0.4f);
            fillBlend(f, bx, by, br * 0.95f, br * 0.95f, 0xFF101010, 1f);
        }
    }

    /** Gold leaf and ornament: rings, squares and spirals, and among them pale circles the size of the ball. */
    private void klimt(int[] a, float t) {
        float cell = 10f * u;
        for (int y = 0; y < yb; y++)
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                float l = lum(src[i]);
                int c = Col.mix(0xFF3A2808, 0xFFF0CC6A, l * (1.1f - 0.1f * l));
                c = Col.mul(c, 0.94f + 0.08f * sin(x * 0.9f / u + y * 0.6f / u + t * 1.5f));
                int ix = (int) (x / cell), iy = (int) (y / cell);
                float fx = x + 0.5f - (ix + 0.5f) * cell, fy = y + 0.5f - (iy + 0.5f) * cell;
                float d = (float) Math.sqrt(fx * fx + fy * fy), kind = Col.hash(ix, iy);
                float mark = 0f;
                int ink = 0xFF2A1A08;
                if (kind < 0.4f) mark = Col.clamp01(0.6f * u - Math.abs(d - 0.32f * cell) + 0.5f);
                else if (kind < 0.55f) mark = Math.max(Math.abs(fx), Math.abs(fy)) > 0.36f * cell ? 0.8f : 0f;
                else if (kind < 0.585f) {
                    mark = Col.clamp01(midR - d + 0.5f);
                    ink = 0xFFF8F0D8;
                } else if (kind < 0.75f) {
                    float ang = (float) Math.atan2(fy, fx) / 6.2831853f + 0.5f;
                    float turns = d / (0.12f * cell) - ang;
                    mark = d < 0.42f * cell ? Col.clamp01(1f - Math.abs(turns - Math.round(turns)) * 4f) * 0.7f : 0f;
                }
                float mid = 1f - Math.abs(l - 0.45f) * 1.6f;
                a[i] = Col.mix(c, ink, mark * (ink == 0xFFF8F0D8 ? 0.7f : Col.clamp01(mid) * 0.65f));
            }
    }

    /** Everything wavers, in bands of orange and blue. */
    private void munch(int[] a, float t) {
        for (int y = 0; y < yb; y++)
            for (int x = 0; x < w; x++) {
                float dx = 3f * u * sin(y * 0.09f / u + t * 0.9f + x * 0.02f / u);
                float dy = 2f * u * sin(x * 0.05f / u + t * 0.6f);
                int c = at(x + dx, y + dy);
                float l = lum(c);
                c = Col.mix(c, l > 0.5f ? 0xFFE8703A : 0xFF2A4A7A, 0.38f);
                float band = sin((y + 4f * u * sin(x * 0.04f / u)) * 0.6f / u);
                a[y * w + x] = Col.mul(c, 0.9f + 0.1f * band);
            }
    }

    private static final int[] BAUHAUS = {0xFFD8342A, 0xFF2A4AA8, 0xFFF2C21E, 0xFF161616, 0xFF7A3A9A, 0xFF2E8A4E};

    /** Circles and lines on cream: the ball is one circle among many, the only one that keeps moving. */
    private void kandinsky(Px f, float t, boolean far, float bx, float by, float br) {
        int[] a = f.a;
        for (int y = 0; y < yb; y++)
            for (int x = 0; x < w; x++) {
                int c = Col.mix(0xFFEDE3CC, src[y * w + x], 0.28f);
                float e = edge(x, y);
                a[y * w + x] = Col.mix(c, 0xFF1A1A1A, Col.clamp01((e - 0.12f) * 4f));
            }
        f.lineAA(0.05f * w, 0.9f * yb, 0.55f * w, 0.05f * yb, 1f * u, 0xFF161616, 0.8f);
        f.lineAA(0.4f * w, 0.95f * yb, 0.98f * w, 0.35f * yb, 0.8f * u, 0xFF161616, 0.7f);
        for (int k = 0; k < 11; k++) {
            float cx = w * (0.08f + 0.84f * Col.hash(k, 1)) + 6f * u * (float) Math.sin(t * 0.3f + k);
            float cy = yb * (0.08f + 0.84f * Col.hash(k, 2)) + 5f * u * (float) Math.cos(t * 0.25f + k * 2);
            float r = k % 3 == 0 ? midR : midR * (1.5f + 3f * Col.hash(k, 3));
            int c = BAUHAUS[k % BAUHAUS.length];
            f.discAA(cx, cy, r, c, 0.85f);
            if (k % 2 == 0) f.ringAA(cx, cy, r, r, 0.8f * u, 0xFF161616, 0.9f);
            if (k % 4 == 1) f.discAA(cx, cy, r * 0.45f, BAUHAUS[(k + 2) % BAUHAUS.length], 0.9f);
        }
        if (far) {
            f.discAA(bx, by, br, 0xFFD8342A, 1f);
            f.ringAA(bx, by, br, br, 0.8f * u, 0xFF161616, 1f);
            f.discAA(bx, by, br * 0.4f, 0xFF2A4AA8, 1f);
        }
    }

    private static final float[][] CUTS = {{0.35f, 0.5f, 1.2f}, {0.62f, 0.4f, 2.1f}, {0.5f, 0.75f, 0.3f}};
    private static final int[] FACET = {0xFFC89A5A, 0xFF5A6A7A, 0xFF6A4A32, 0xFFE8DCC0, 0xFF8A8A84, 0xFF9A7A4A, 0xFF4A5A4A, 0xFFB8A888};
    private static final float[][] SHIFT = {{6, -3}, {-7, 2}, {3, 5}, {-4, -6}, {8, 4}, {-2, -8}, {5, -5}, {-6, 6}};

    /** Facets, each seen from its own place, and every thing twice: the ball too. */
    private void picasso(int[] a) {
        int nc = CUTS.length;
        float[] sn = new float[nc], cs = new float[nc];
        for (int k = 0; k < nc; k++) {
            sn[k] = (float) Math.sin(CUTS[k][2]);
            cs[k] = (float) Math.cos(CUTS[k][2]);
        }
        for (int y = 0; y < yb; y++)
            for (int x = 0; x < w; x++) {
                int id = 0;
                float near = 99f;
                for (int k = 0; k < nc; k++) {
                    float d = (x - CUTS[k][0] * w) * sn[k] - (y - CUTS[k][1] * yb) * cs[k];
                    if (d > 0f) id |= 1 << k;
                    near = Math.min(near, Math.abs(d));
                }
                float ox = SHIFT[id][0] * u, oy = SHIFT[id][1] * u;
                int c = Col.mix(at(x + ox, y + oy), at(x - ox * 0.6f + 9f * u, y + oy * 0.4f), 0.42f);
                c = Col.mix(c, FACET[id], 0.28f);
                a[y * w + x] = near < 0.7f * u ? 0xFF2A2220 : c;
            }
    }

    /** Soft things melting downward in a desert light; the ball pulls out into a drop. */
    private void dali(int[] a, float t) {
        float big = 18f * u;
        float[] melt = new float[w];
        for (int x = 0; x < w; x++) melt[x] = noise(x / (14f * u), t * 0.04f);
        for (int y = 0; y < yb; y++) {
            float down = (float) y / yb;
            down *= (float) Math.sqrt(down);
            for (int x = 0; x < w; x++) {
                float n = melt[x];
                float drip = big * n * n * n * down;
                float sx = x + 2f * u * sin(y * 0.03f / u + n * 3f);
                int r = 0, g = 0, b = 0;
                for (int k = 0; k < 4; k++) {
                    int c = at(sx, y - drip - k * drip * 0.15f);
                    r += (c >> 16) & 255;
                    g += (c >> 8) & 255;
                    b += c & 255;
                }
                int c = Col.rgb(r / 4, g / 4, b / 4);
                a[y * w + x] = Col.mix(c, lum(c) > 0.5f ? 0xFFF0C080 : 0xFF1E2A5A, 0.25f);
            }
        }
    }

    /** Cream ground, black lines, a few blots of pure colour and stars; the ball becomes a small black star. */
    private void miro(Px f, boolean far, float bx, float by, float br) {
        int[] a = f.a;
        for (int y = 0; y < yb; y++)
            for (int x = 0; x < w; x++) {
                int c = Col.mix(0xFFEBDFC4, src[y * w + x], 0.15f);
                a[y * w + x] = Col.mix(c, 0xFF141414, Col.clamp01((edge(x, y) - 0.15f) * 5f));
            }
        f.discAA(0.18f * w, 0.3f * yb, 8f * u, 0xFFD83228, 0.95f);
        f.ellipseAA(0.82f * w, 0.22f * yb, 10f * u, 6f * u, 0xFF2A48A8, 0.95f);
        f.discAA(0.66f * w, 0.62f * yb, 4f * u, 0xFFF2C21E, 0.95f);
        f.discAA(0.3f * w, 0.75f * yb, 7f * u, 0xFF141414, 0.95f);
        f.discAA(0.3f * w + 3f * u, 0.75f * yb - 2f * u, 6f * u, 0xFFEBDFC4, 1f);
        float[][] stars = {{0.1f, 0.12f}, {0.55f, 0.1f}, {0.9f, 0.55f}, {0.45f, 0.45f}};
        for (float[] st : stars) star(f, st[0] * w, st[1] * yb, 5f * u, 0.8f * u);
        if (far) {
            star(f, bx, by, br * 1.5f, Math.max(1f, br * 0.3f));
            f.discAA(bx, by, br * 0.45f, 0xFFD83228, 1f);
        }
    }

    private static void star(Px f, float cx, float cy, float r, float wdt) {
        for (int k = 0; k < 3; k++) {
            double an = k * Math.PI / 3 + 0.3;
            float dx = (float) Math.cos(an) * r, dy = (float) Math.sin(an) * r;
            f.lineAA(cx - dx, cy - dy, cx + dx, cy + dy, wdt, 0xFF141414, 1f);
        }
    }

    /** Night on everything, and the ball is a piece of the day sky with clouds. */
    private void magritte(Px f, boolean far, float bx, float by, float br) {
        int[] a = f.a;
        for (int i = 0, n = w * yb; i < n; i++) a[i] = Col.mix(Col.mul(src[i], 0.42f), 0xFF0A1630, 0.35f);
        if (!far) return;
        float rr = br * 1.15f;
        int x0 = Math.max(0, (int) (bx - rr - 1)), x1 = Math.min(w - 1, (int) (bx + rr + 1));
        int y0 = Math.max(0, (int) (by - rr - 1)), y1 = Math.min(yb - 1, (int) (by + rr + 1));
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                float dx = x + 0.5f - bx, dy = y + 0.5f - by;
                float cov = Col.clamp01(rr - (float) Math.sqrt(dx * dx + dy * dy) + 0.5f);
                if (cov <= 0f) continue;
                int sky = Col.mix(0xFF5E9EE6, 0xFFC4E0F6, (dy + rr) / (2f * rr));
                float cloud = 0f;
                float[][] puffs = {{-0.35f, 0.1f, 0.32f}, {0.05f, 0.2f, 0.38f}, {0.4f, 0.05f, 0.28f}};
                for (float[] p : puffs) {
                    float px = dx - p[0] * rr, py = dy - p[1] * rr;
                    cloud = Math.max(cloud, Col.clamp01(p[2] * rr - (float) Math.sqrt(px * px + py * py) + 0.5f));
                }
                f.blend(x, y, Col.mix(sky, 0xFFFFFFFF, cloud * 0.9f), cov);
            }
    }

    /** Long and narrow, in warm ochre: the far half stretched upward, so the ball seems farther than it is. */
    private void modigliani(int[] a) {
        float cx = w * 0.5f;
        for (int y = 0; y < yb; y++)
            for (int x = 0; x < w; x++) {
                float sy = yb - (yb - y) / 1.22f, sx = cx + (x - cx) * 1.12f;
                int c = Col.mix(at(sx, sy), at(sx, sy + 1f), 0.5f);
                a[y * w + x] = Col.mix(c, tint(c, 0xFFE8A060), 0.45f);
            }
    }

    private static final int[] DRIP = {0xFF141414, 0xFFF4F0E6, 0xFFC89A3A, 0xFF8A8A8A, 0xFF8A3A1A};

    /** Raw canvas, and every stroke flings a skein of paint over it that stays till the match is over. */
    private void pollock(int[] a) {
        while (splashes > 0) {
            splashes--;
            splash();
        }
        for (int i = 0, n = w * yb; i < n; i++) {
            int c = Col.mix(src[i], 0xFFD6C8A8, 0.4f);
            a[i] = cover[i] > 0f ? Col.mix(c, paint[i], cover[i]) : c;
        }
    }

    private void splash() {
        int col = DRIP[rnd.nextInt(DRIP.length)];
        float x = rnd.nextFloat() * w, y = rnd.nextFloat() * yb;
        float ang = rnd.nextFloat() * 6.2831853f, len = (40f + 70f * rnd.nextFloat()) * u;
        float ph = rnd.nextFloat() * 6f, bend = (rnd.nextFloat() - 0.5f) * 0.06f;
        for (float s = 0f; s < len; s += 0.8f * u) {
            ang += bend;
            x += (float) Math.cos(ang) * 0.8f * u;
            y += (float) Math.sin(ang) * 0.8f * u;
            float r = (0.4f + 1.4f * (0.5f + 0.5f * (float) Math.sin(s / u * 0.3f + ph)) * rnd.nextFloat()) * u;
            drop(x, y, r, col);
        }
        int n = 6 + rnd.nextInt(9);
        for (int k = 0; k < n; k++) {
            float t = rnd.nextFloat() * len;
            float px = x - (float) Math.cos(ang) * t + (rnd.nextFloat() - 0.5f) * 12f * u;
            float py = y - (float) Math.sin(ang) * t + (rnd.nextFloat() - 0.5f) * 12f * u;
            drop(px, py, k % 4 == 0 ? midR : (0.5f + 1.7f * rnd.nextFloat()) * u, col);
        }
    }

    private void drop(float cx, float cy, float r, int col) {
        int x0 = Math.max(0, (int) (cx - r - 1)), x1 = Math.min(w - 1, (int) (cx + r + 1));
        int y0 = Math.max(0, (int) (cy - r - 1)), y1 = Math.min(yb - 1, (int) (cy + r + 1));
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                float c = Col.clamp01(r - (float) Math.sqrt(dx * dx + dy * dy) + 0.5f);
                if (c <= 0f) continue;
                int i = y * w + x;
                paint[i] = cover[i] > 0f ? Col.mix(paint[i], col, c) : col;
                cover[i] = Math.max(cover[i], c);
            }
    }

    // ---- the darkroom itself

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
        prints(s, new Px(w, h, out));
    }

    /** Dark linoleum in a chequer, a pool of red from the safelight, the table's shadow. */
    private static int floor(float x, float z, float foot) {
        float tu = x / 0.45f + 50f, tv = z / 0.45f + 50f;
        int c = (((int) tu + (int) tv) & 1) == 0 ? 0xFF141010 : 0xFF1C1715;
        float eu = Math.min(tu - (int) tu, (int) tu + 1 - tu) * 0.45f, ev = Math.min(tv - (int) tv, (int) tv + 1 - tv) * 0.45f;
        c = Col.mix(c, 0xFF0A0808, 0.5f * Math.max(Col.clamp01((0.004f - eu) / foot + 0.5f), Col.clamp01((0.004f - ev) / foot + 0.5f)));
        float dz = z - LAMP_Z;
        c = Col.addScaled(c, 0xFF8A1A10, 0.4f / (1f + (x * x + dz * dz) * 0.9f));
        float ez = z - 2.1f;
        c = Col.addScaled(c, 0xFF8A847A, 0.3f / (1f + (x * x + ez * ez) * 2f));
        float under = Col.ramp(0.3f, -0.05f, Math.max(Math.abs(x) - Game.HW, Math.max(-z, z - Game.L)));
        return Col.mul(c, 1f - 0.55f * under);
    }

    /** A line across the far end of the room, prints hanging from it to dry. */
    private static void prints(Scene s, Px f) {
        Cam cam = s.cam;
        float span = 1.7f;
        float px = 0f, py = 0f;
        boolean first = true;
        for (float x = -span; x <= span + 1e-3f; x += 0.1f) {
            float y = LINE_Y - 0.05f * (1f - (x / span) * (x / span));
            if (!cam.project(x, y, LINE_Z)) continue;
            if (!first) f.lineAA(px, py, cam.sx, cam.sy, 0.8f, 0xFF4A3C38, 0.9f);
            px = cam.sx;
            py = cam.sy;
            first = false;
        }
        for (int k = 0; k < 7; k++) {
            float cx = -1.32f + k * 0.44f;
            float top = LINE_Y - 0.05f * (1f - (cx / span) * (cx / span)) - 0.01f;
            if (!cam.project(cx - 0.14f, top, LINE_Z)) continue;
            float x0 = cam.sx, y0 = cam.sy;
            if (!cam.project(cx + 0.14f, top - 0.2f, LINE_Z)) continue;
            float x1 = cam.sx, y1 = cam.sy;
            for (int y = (int) y0; y <= (int) y1; y++)
                for (int x = (int) x0; x <= (int) x1; x++) {
                    if (x < 0 || x >= f.w || y < 0 || y >= f.h) continue;
                    float pu = (x - x0) / Math.max(1f, x1 - x0), pv = (y - y0) / Math.max(1f, y1 - y0);
                    f.a[y * f.w + x] = print(k, pu, pv);
                }
            f.discAA((x0 + x1) * 0.5f, y0, 1.2f, 0xFF8A7A60, 1f);
        }
    }

    /** One little photograph under red light: a horizon, a face, a tree, a window. */
    private static int print(int k, float pu, float pv) {
        if (pu < 0.08f || pu > 0.92f || pv < 0.08f || pv > 0.9f) return 0xFF8A5A50;
        float u = (pu - 0.08f) / 0.84f, v = (pv - 0.08f) / 0.82f;
        float l;
        switch (k % 4) {
            case 0:
                l = v < 0.55f ? 0.75f - 0.3f * v : 0.25f - 0.1f * v;
                break;
            case 1: {
                float dx = (u - 0.5f) / 0.28f, dy = (v - 0.45f) / 0.36f;
                l = dx * dx + dy * dy < 1f ? 0.7f : 0.2f;
                break;
            }
            case 2:
                l = Math.abs(u - 0.5f) < 0.06f && v > 0.5f || (u - 0.5f) * (u - 0.5f) + (v - 0.35f) * (v - 0.35f) < 0.08f ? 0.15f : 0.7f;
                break;
            default:
                l = (Math.abs(u - 0.5f) < 0.04f || Math.abs(v - 0.5f) < 0.04f) ? 0.2f : 0.65f - 0.2f * u;
        }
        return Col.mix(0xFF1A0806, 0xFFD87A68, l);
    }

    /**
     * Our half lies in the safelight's red; the far half is the easel under the
     * enlarger, in its white light, where the image is made.
     */
    @Override
    int surface(float x, float z) {
        float dz = z - LAMP_Z;
        int c = Col.mix(0xFF201E1C, 0xFF4A1A14, 0.45f * (float) Math.exp(-(x * x + dz * dz) / 0.8f) * Col.ramp(Game.NZ + 0.1f, Game.NZ - 0.2f, z));
        float ez = z - 2.05f;
        float easel = (float) Math.exp(-(x * x / 0.9f + ez * ez / 0.7f)) * Col.ramp(Game.NZ, Game.NZ + 0.2f, z);
        return Col.mix(c, 0xFFB4ACA0, 0.8f * easel);
    }

    @Override
    int lines() {
        return 0xFFE6DCD2;
    }

    @Override
    int grid() {
        return 0xFF2C2826;
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
        return 0xFFFF3A28;
    }

    @Override
    float lightK() {
        return 0f;
    }

    @Override
    float gloss() {
        return 0.08f;
    }

    @Override
    int faceTop() {
        return 0xFF2A2624;
    }

    @Override
    int faceBottom() {
        return 0xFF0A0808;
    }

    @Override
    int bevel() {
        return 0xFFB07A70;
    }

    @Override
    int tail() {
        return 0xFFFF7A5A;
    }

    @Override
    float tailAlpha() {
        return 0.3f;
    }

    @Override
    int netThread() {
        return 0xFF6A5A54;
    }

    @Override
    int netTape() {
        return 0xFFEEE4DA;
    }

    @Override
    int ripple() {
        return 0xFFFF7A5A;
    }

    @Override
    int ballGlow(Ball b) {
        return 0xFFFF6A50;
    }

    @Override
    float ballGlowK(Ball b) {
        return 0.15f;
    }

    @Override
    int rimLight() {
        return 0xFFFF4A30;
    }

    @Override
    int hum() {
        return Synth.HUM_DARKROOM;
    }

    // red bakelite for the player, green for the opponent: the colour-blind eye cannot tell them apart
    private static final Racket MINE = new Racket(0xFFB02A24, 0xFFE8786A, 0xFF4A0C08, 0xFF2A2420, 0xFF8A7A70,
            0xFF3A2A20, 0xFF7A5A44, 0xFFFF5A4A, 0, 0.05f);
    private static final Racket THEIRS = new Racket(0xFF2A7A3A, 0xFF7AD08A, 0xFF0A2A12, 0xFF2A2420, 0xFF8A7A70,
            0xFF3A2A20, 0xFF7A5A44, 0xFF5AFF7A, 0xFF5AFF7A, 0.05f);

    @Override
    Racket racket(boolean player) {
        return player ? MINE : THEIRS;
    }

    /** The safelight: a dark shade on a cord, red glass, and its glow on our side of the net. */
    @Override
    void drawCenter(Scene s, Px f, Game g, float t) {
        Cam cam = s.cam;
        if (!cam.project(0f, LAMP_Y, LAMP_Z)) return;
        float lx = cam.sx, ly = cam.sy, sc = cam.scale();
        if (cam.project(0f, LAMP_Y + 0.9f, LAMP_Z)) f.lineAA(lx, ly, cam.sx, cam.sy, 0.9f, 0xFF3A302C, 0.9f);
        f.glow(lx, ly + 0.03f * sc, 0.3f * sc, 0xFFFF3020, 0.3f);
        // a dome of dark metal, its rim, and the red glass under it
        f.ellipseAA(lx, ly - 0.015f * sc, 0.055f * sc, 0.05f * sc, 0xFF1E1816, 1f);
        f.ellipseAA(lx - 0.018f * sc, ly - 0.035f * sc, 0.018f * sc, 0.01f * sc, 0xFF6A5A52, 0.7f);
        f.ellipseAA(lx, ly + 0.02f * sc, 0.075f * sc, 0.022f * sc, 0xFF3A2C28, 1f);
        f.ellipseAA(lx, ly + 0.022f * sc, 0.062f * sc, 0.016f * sc, 0xFFFF4A30, 1f);
        f.ellipseAA(lx - 0.015f * sc, ly + 0.02f * sc, 0.018f * sc, 0.006f * sc, 0xFFFFB0A0, 0.8f);
    }
}
