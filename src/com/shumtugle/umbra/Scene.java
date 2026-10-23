package com.shumtugle.umbra;

/**
 * Draws the court. The camera never moves, so everything that never changes
 * (the realm's backdrop, the table top with its lines, the table's edge) is
 * painted once per screen size, with soft edges where a pixel is only partly
 * covered. Each frame copies that, then lays the living things over it: the grid
 * bent by the realm's force, its light on the table, shadows, the net, the
 * paddles, the ball and the realm's own thing, farthest first.
 */
final class Scene {
    static final float THICK = 0.035f;       // thickness of the table top
    static final float GRID = 0.25f;         // spacing of the grid on the table
    static final float LOOK = 1.5f;          // the ball is drawn this much larger than it is, to be seen at a glance

    final Cam cam;
    final int w, h;
    final Realm realm;
    private final int[] still;               // the backdrop with the table painted in

    // the table top, pixel by pixel: where it is on screen and in the world
    private int tn;
    private int[] tIdx;
    private float[] tX, tZ, tFoot, tCov, tFree;

    // the ball's recent places, for its tail
    private static final int TRAIL = 22;
    private final float[] trX = new float[TRAIL], trY = new float[TRAIL], trZ = new float[TRAIL];
    private int trN;
    private float trClock;
    private float goneX, goneY, goneZ;

    private int[] lensSrc = new int[0];   // see scratch()

    Scene(int w, int h, Realm realm) {
        this.w = w;
        this.h = h;
        this.realm = realm;
        cam = new Cam(w, h);
        still = new int[w * h];
        bake();
    }

    /** A buffer for passes that read the frame while they change it. */
    int[] scratch(int n) {
        if (lensSrc.length < n) lensSrc = new int[n];
        return lensSrc;
    }

    // ---- what never changes

    /** Paints the table into the still picture and lists its pixels for the living layers. */
    private void bake() {
        realm.backdrop(this, still);
        int cap = w * h;
        int[] idx = new int[cap];
        float[] xs = new float[cap], zs = new float[cap], foot = new float[cap], cov = new float[cap], free = new float[cap];
        int n = 0;
        final float inv = 1f / cam.f;
        for (int sy = 0; sy < h; sy++) {
            float zA = planeZ(sy, 0f), zB = planeZ(sy + 1, 0f), zT = planeZ(sy + 0.5f, 0f);
            float dcy = -(sy + 0.5f - cam.cy) * inv;
            float dirY = dcy * cam.cs - cam.sn;
            if (dirY > -1e-4f) continue;
            float tT = -cam.y / dirY;
            float footX = tT * inv;
            float footZ = Math.abs(zA - zB) + 1e-6f;
            float covZ = band(zT, 0f, Game.L, footZ);
            // the table's near edge: rows where the ray meets the face at z = 0
            for (int sx = 0; sx < w; sx++) {
                float dx = (sx + 0.5f - cam.cx) * inv;
                float x = tT * dx;
                float covX = band(x, -Game.HW, Game.HW, footX);
                float c = covX * covZ;
                int i = sy * w + sx;
                if (c > 0f) {
                    float mark = lines(x, zT, footX, footZ);
                    int surface = Col.mix(realm.surface(x, zT), realm.lines(), mark);
                    still[i] = Col.mix(still[i], surface, c);
                    idx[n] = i;
                    xs[n] = x;
                    zs[n] = zT;
                    foot[n] = Math.max(footX, footZ * 0.5f);
                    cov[n] = c;
                    free[n] = c * (1f - mark);
                    n++;
                }
                // the face, below the near edge: shaded darker toward the bottom
                float fy = faceY(sy + 0.5f);
                if (fy < 0f && fy > -THICK - 0.02f) {
                    float fFoot = Math.abs(faceY(sy) - faceY(sy + 1)) + 1e-6f;
                    float fx = faceT(sy + 0.5f) * dx;
                    float fc = band(fy, -THICK, 0f, fFoot) * band(fx, -Game.HW, Game.HW, faceT(sy + 0.5f) * inv) * (1f - c);
                    if (fc > 0f) {
                        float k = Col.clamp01(-fy / THICK);
                        int face = Col.mix(realm.faceTop(), realm.faceBottom(), k);
                        if (k < 0.25f) face = Col.mix(realm.bevel(), face, k * 4f);   // a lit bevel
                        still[i] = Col.mix(still[i], face, fc);
                    }
                }
            }
        }
        tn = n;
        tIdx = java.util.Arrays.copyOf(idx, n);
        tX = java.util.Arrays.copyOf(xs, n);
        tZ = java.util.Arrays.copyOf(zs, n);
        tFoot = java.util.Arrays.copyOf(foot, n);
        tCov = java.util.Arrays.copyOf(cov, n);
        tFree = java.util.Arrays.copyOf(free, n);
        realm.keepVisible(still);
    }

    /** Where the ray through a screen row meets the plane at height py. */
    private float planeZ(float row, float py) {
        float dcy = -(row - cam.cy) / cam.f;
        float dirY = dcy * cam.cs - cam.sn, dirZ = dcy * cam.sn + cam.cs;
        if (dirY > -1e-4f) return 1e9f;
        return cam.z + (py - cam.y) / dirY * dirZ;
    }

    /** Ray length to the near face plane (z = 0) through a screen row. */
    private float faceT(float row) {
        float dcy = -(row - cam.cy) / cam.f;
        float dirZ = dcy * cam.sn + cam.cs;
        return dirZ > 1e-4f ? (0f - cam.z) / dirZ : -1f;
    }

    /** Height at which the ray through a screen row meets the near face plane. */
    private float faceY(float row) {
        float t = faceT(row);
        if (t <= 0f) return 1f;
        float dcy = -(row - cam.cy) / cam.f;
        return cam.y + t * (dcy * cam.cs - cam.sn);
    }

    /** How much of a pixel spanning v +- foot/2 lies inside [lo, hi]. */
    static float band(float v, float lo, float hi, float foot) {
        float a = Math.max(v - foot * 0.5f, lo), b = Math.min(v + foot * 0.5f, hi);
        return Col.clamp01((b - a) / foot);
    }

    /** The painted lines: the border and the centre line, at least a pixel wide. */
    private static float lines(float x, float z, float footX, float footZ) {
        float wb = Math.max(0.022f, footX * 0.9f), wz = Math.max(0.022f, footZ * 0.9f);
        float m = band(x, Game.HW - wb, Game.HW, footX) + band(x, -Game.HW, -Game.HW + wb, footX);
        m = Math.max(m, band(z, 0f, wz, footZ) + band(z, Game.L - wz, Game.L, footZ));
        float wc = Math.max(0.008f, footX * 0.9f);
        m = Math.max(m, band(x, -wc * 0.5f, wc * 0.5f, footX) * 0.75f);
        return Col.clamp01(m);
    }

    // ---- the living frame

    /** Keeps the ball's tail. Called once per frame. */
    void update(Game g, float dt) {
        realm.tick(g, dt);
        if (g.state != Game.RALLY && g.state != Game.POINT || g.ball.gone) {
            if (!g.ball.gone) trN = 0;
            else if (trN > 0) {
                trClock += dt;
                if (trClock > 0.03f) {
                    trClock = 0f;
                    trN--;
                }
            }
            return;
        }
        goneX = g.ball.x;
        goneY = g.ball.y;
        goneZ = g.ball.z;
        trClock += dt;
        if (trClock < 0.018f) return;
        trClock = 0f;
        int keep = Math.min(trN, TRAIL - 1);
        for (int i = keep; i > 0; i--) {
            trX[i] = trX[i - 1];
            trY[i] = trY[i - 1];
            trZ[i] = trZ[i - 1];
        }
        trX[0] = g.ball.x;
        trY[0] = g.ball.y;
        trZ[0] = g.ball.z;
        trN = keep + 1;
    }

    void render(Px f, Game g, float t) {
        System.arraycopy(still, 0, f.a, 0, still.length);
        twinkle(f, t);
        table(f, g, t);
        realm.drawOnTable(this, f, g, t);
        zone(f, g);
        // the rest by depth, farthest first
        float[] depth = new float[5];
        depth[0] = depthOf(g.pad[1].x, Game.PY, g.pad[1].z);
        depth[1] = depthOf(g.ball.x, g.ball.y, g.ball.z);
        depth[2] = depthOf(0f, Game.NH * 0.5f, Game.NZ);
        depth[3] = depthOf(realm.x, realm.height(), realm.z);
        depth[4] = depthOf(g.pad[0].x, Game.PY, g.pad[0].z);
        boolean[] done = new boolean[5];
        for (int k = 0; k < 5; k++) {
            int far = -1;
            for (int i = 0; i < 5; i++) if (!done[i] && (far < 0 || depth[i] > depth[far])) far = i;
            done[far] = true;
            switch (far) {
                case 0:
                    paddle(f, g.pad[1], false);
                    break;
                case 1:
                    ball(f, g);
                    break;
                case 2:
                    if (!realm.drawBarrier(this, f, g, t)) net(f, g);
                    break;
                case 3:
                    realm.drawCenter(this, f, g, t);
                    break;
                default:
                    paddle(f, g.pad[0], g.human);
                    break;
            }
        }
        realm.drawOver(this, f, g, t);
    }

    private float depthOf(float x, float y, float z) {
        return cam.project(x, y, z) ? cam.zc : -1f;
    }

    private void twinkle(Px f, float t) {
        realm.twinkle(f.a, t);
    }

    /** The grid bent by the realm's force, its light and reflection, shadows and the marks of bounces. */
    private void table(Px f, Game g, float t) {
        final int[] a = f.a;
        final Realm rl = realm;
        final float hx = rl.x, hz = rl.z, hy = rl.height();
        final float bendA = rl.bendA(), bendC = rl.bendC(), gridGain = rl.gridGain(), swirl = rl.swirl();
        final int gc = rl.grid(), lc = rl.light(), rc = rl.ripple();
        final float gR = (gc >> 16) & 255, gG = (gc >> 8) & 255, gB = gc & 255;
        final float lR = (lc >> 16) & 255, lG = (lc >> 8) & 255, lB = lc & 255;
        final float rR = (rc >> 16) & 255, rG = (rc >> 8) & 255, rB = rc & 255;
        final float lightK = rl.lightK(), glossK = rl.gloss();
        Ball b = g.ball;
        boolean ballShadow = g.state != Game.OVER && !b.gone;
        float bR = Ball.R * LOOK * 1.25f + 0.06f * Math.max(0f, b.y), bDark = 0.55f / (1f + 2.2f * Math.max(0f, b.y));
        // the thing's reflection on the glossy top: where the camera sees it mirrored
        float s = cam.y / (cam.y + hy);
        float rx = cam.x + (hx - cam.x) * s, rz = cam.z + (hz - cam.z) * s;
        float flare = 1f + 0.8f * rl.closeness(b) * (b.gone ? 0f : 1f) + 2.5f * Math.max(0f, 1f - g.goneAge * 2f);
        float ring = g.bounceAge < 0.45f ? g.bounceAge : -1f;
        Game.Paddle p0 = g.pad[0], p1 = g.pad[1];
        float ringR = 0.03f + Math.max(0f, ring) * 0.55f, ringK = ring >= 0f ? (1f - ring / 0.45f) * 0.7f : 0f;
        for (int n = 0; n < tn; n++) {
            float x = tX[n], z = tZ[n], foot = tFoot[n], cov = tCov[n];
            int i = tIdx[n];
            int c = a[i];
            float cr = (c >> 16) & 255, cg = (c >> 8) & 255, cb = c & 255;
            // the grid, bent by the force: the clearest picture of it
            float dx = hx - x, dz = hz - z;
            float r2 = dx * dx + dz * dz;
            float k = bendA / (r2 + bendC);
            float px = x + dx * k, pz = z + dz * k;
            if (swirl != 0f) {
                // turned about the centre, more near it: a bijection, so it never folds either
                float ang = swirl / (1f + r2 * 0.8f), a2 = ang * ang;
                float sn = ang * (1f - a2 / 6f * (1f - a2 / 20f)), cs = 1f - a2 / 2f * (1f - a2 / 12f);
                float ux = px - hx, uz = pz - hz;
                px = hx + ux * cs - uz * sn;
                pz = hz + ux * sn + uz * cs;
            }
            float gx = px * (1f / GRID) + 64f, gz = pz * (1f / GRID) + 64f;
            float fx = gx - (int) gx, fz = gz - (int) gz;
            float lx = (fx < 0.5f ? fx : 1f - fx) * GRID, lz = (fz < 0.5f ? fz : 1f - fz) * GRID;
            float lw = Math.max(0.006f, foot * 0.55f), inv = 1f / foot;
            float ga = (lw - lx) * inv + 0.5f, gb = (lw - lz) * inv + 0.5f;
            float grid = ga > gb ? ga : gb;
            if (grid > 0f) {
                if (grid > 1f) grid = 1f;
                float gk = grid * tFree[n] * (0.5f + 0.9f / (1f + r2 * 2.5f)) * gridGain;
                cr += gR * gk;
                cg += gG * gk;
                cb += gB * gk;
            }
            // light from the thing, and its streak of reflection toward the camera
            float lk = lightK / (r2 + hy * hy) * flare;
            float ex = (x - rx) * (1f / 0.11f), ez = (z - rz) * (1f / 0.42f);
            float e2 = ex * ex + ez * ez;
            if (e2 < 9f) lk += (float) Math.exp(-e2) * glossK * flare;
            lk *= cov;
            cr += lR * lk;
            cg += lG * lk;
            cb += lB * lk;
            // a ripple where the ball last struck the table
            if (ringK > 0f) {
                float bx = x - g.bounceX, bz = z - g.bounceZ;
                float d2 = bx * bx + bz * bz;
                if (d2 < (ringR + 0.03f) * (ringR + 0.03f)) {
                    float e = Math.abs((float) Math.sqrt(d2) - ringR);
                    if (e < 0.03f) {
                        float rk = (1f - e / 0.03f) * ringK * cov;
                        cr += rR * rk;
                        cg += rG * rk;
                        cb += rB * rk;
                    }
                }
            }
            // shadows
            float shade = 1f;
            if (ballShadow) {
                float sx = x - b.x, sz = z - b.z;
                float d2 = sx * sx + sz * sz;
                if (d2 < bR * bR * 2.6f) shade -= Col.ramp(bR * 1.4f, bR * 0.5f, (float) Math.sqrt(d2)) * bDark;
            }
            float sx = x - p0.x, sz = z - p0.z, d2 = sx * sx + sz * sz;
            if (d2 < 0.05f) shade -= Col.ramp(0.21f, 0.10f, (float) Math.sqrt(d2)) * 0.32f;
            sx = x - p1.x;
            sz = z - p1.z;
            d2 = sx * sx + sz * sz;
            if (d2 < 0.05f) shade -= Col.ramp(0.21f, 0.10f, (float) Math.sqrt(d2)) * 0.32f;
            if (shade < 1f) {
                float m = 1f - (1f - shade) * cov;
                cr *= m;
                cg *= m;
                cb *= m;
            }
            int ir = cr > 255f ? 255 : (int) cr, ig = cg > 255f ? 255 : (int) cg, ib = cb > 255f ? 255 : (int) cb;
            a[i] = 0xFF000000 | (ir << 16) | (ig << 8) | ib;
        }
    }

    /** The player's strike zone: a ring on the table under the paddle. */
    private void zone(Px f, Game g) {
        if (g.state == Game.OVER) return;
        for (int i = 0; i < 2; i++) {
            if (i == 1 && g.human) continue;
            Game.Paddle p = g.pad[i];
            if (!cam.project(p.x, 0f, p.z)) continue;
            float cx = cam.sx, cy = cam.sy, sc = cam.scale();
            if (!cam.project(p.x, 0f, p.z + Game.RH)) continue;
            float yA = cam.sy;
            if (!cam.project(p.x, 0f, p.z - Game.RH)) continue;
            float ry = Math.abs(yA - cam.sy) / 2f;
            boolean mine = i == 0 && g.human;
            f.ringAA(cx, cy, Game.RH * sc, ry, mine ? 1.4f : 1f, mine ? 0xFFFFFFFF : 0xFFB8C8E0, mine ? 0.55f : 0.2f);
        }
    }

    // ---- objects

    private void ball(Px f, Game g) {
        if (g.state == Game.OVER) return;
        Ball b = g.ball;
        tail(f);
        realm.drawGuide(this, f, g);
        if (b.gone) {
            swallowed(f, g);
            return;
        }
        if (!cam.project(b.x, b.y, b.z)) return;
        float cx = cam.sx, cy = cam.sy, r = Math.max(3.2f, Ball.R * LOOK * cam.scale());
        sphere(f, cx, cy, r, 1f, g);
    }

    /** A lit white sphere with a soft edge and a faint glow. */
    private void sphere(Px f, float cx, float cy, float r, float op, Game g) {
        f.glow(cx, cy, r * 2.6f, realm.ballGlow(g.ball), realm.ballGlowK(g.ball) * op);
        // the warm side faces the realm's thing
        float hx = 0f, hy = 0f;
        if (cam.project(realm.x, realm.height(), realm.z)) {
            hx = cam.sx - cx;
            hy = cam.sy - cy;
            float d = (float) Math.sqrt(hx * hx + hy * hy) + 1e-3f;
            hx /= d;
            hy /= d;
        }
        int x0 = (int) Math.floor(cx - r - 1), x1 = (int) Math.ceil(cx + r + 1);
        int y0 = (int) Math.floor(cy - r - 1), y1 = (int) Math.ceil(cy + r + 1);
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                float u = (x + 0.5f - cx) / r, v = (y + 0.5f - cy) / r;
                float d2 = u * u + v * v;
                float d = (float) Math.sqrt(d2);
                float cov = (1f - d) * r + 0.5f;
                if (cov <= 0f) continue;
                float nz = (float) Math.sqrt(Math.max(0f, 1f - d2));
                float diff = Col.clamp01(-0.45f * u - 0.55f * v + 0.70f * nz);
                float rim = Col.clamp01(u * hx + v * hy) * (1f - nz);
                int c = Col.mix(0xFF5A6A88, 0xFFF6F3EC, 0.25f + 0.75f * diff);
                c = Col.addScaled(c, realm.rimLight(), rim * 0.9f);
                float spec = Col.clamp01(1f - ((u + 0.38f) * (u + 0.38f) + (v + 0.42f) * (v + 0.42f)) * 9f);
                c = Col.addScaled(c, 0xFFFFFFFF, spec * 0.8f);
                f.blend(x, y, c, op * Math.min(1f, cov));
            }
    }

    /** The tail of the ball: it bends where the force bends the path. */
    private void tail(Px f) {
        float px = 0f, py = 0f;
        boolean have = false;
        for (int i = 0; i < trN; i++) {
            if (!cam.project(trX[i], trY[i], trZ[i])) {
                have = false;
                continue;
            }
            float sx = cam.sx, sy = cam.sy, sc = cam.scale();
            if (have) {
                float k = 1f - i / (float) TRAIL;
                float width = Math.max(0.8f, Ball.R * sc * realm.tailWidth() * k);
                f.lineAA(px, py, sx, sy, width, realm.tail(), realm.tailAlpha() * k * k);
            }
            px = sx;
            py = sy;
            have = true;
        }
    }

    /** The ball taken by the realm's thing: stretched, shrinking, gone. */
    private void swallowed(Px f, Game g) {
        float a = g.goneAge / 0.45f;
        if (a >= 1f) return;
        float e = a * a;
        float x = goneX + (realm.x - goneX) * e, y = goneY + (realm.height() - goneY) * e, z = goneZ + (realm.z - goneZ) * e;
        if (!cam.project(x, y, z)) return;
        float sx = cam.sx, sy = cam.sy, r = Math.max(1f, Ball.R * LOOK * cam.scale() * (1f - a));
        if (cam.project(realm.x, realm.height(), realm.z)) f.lineAA(sx, sy, sx + (cam.sx - sx) * 0.6f, sy + (cam.sy - sy) * 0.6f, r * 1.2f, 0xFFFFE2B0, 0.6f * (1f - a));
        sphere(f, sx, sy, r, 1f - a * 0.5f, g);
    }

    private void paddle(Px f, Game.Paddle p, boolean player) {
        if (!cam.project(p.x, Game.PY, p.z)) return;
        float sx = cam.sx, sy = cam.sy, sc = cam.scale();
        float rx = 0.125f * sc, ry = rx * 0.86f;
        boolean near = p.side == 0;
        Racket k = realm.racket(player);
        // the handle points back toward its own player
        if (!cam.project(p.x + (near ? 0.025f : -0.025f), Game.PY - 0.03f, p.z + (near ? -0.25f : 0.25f))) return;
        float hx = cam.sx, hy = cam.sy;
        float ax = sx + (hx - sx) * 0.45f, ay = sy + (hy - sy) * 0.45f;
        float hw = Math.max(2f, 0.042f * sc);
        f.lineAA(ax, ay, hx, hy, hw + 1.2f, Col.mul(k.handle, 0.25f), 1f);
        f.lineAA(ax, ay, hx, hy, hw, k.handle, 1f);
        f.lineAA(ax - hw * 0.15f, ay, hx - hw * 0.15f, hy, Math.max(0.8f, hw * 0.3f), k.handleLight, 0.8f);
        f.discAA(hx, hy, hw * 0.62f, k.handleEnd, 1f);
        // a dark outline, then the blade: an edge round a face lit from the upper left
        f.ellipseAA(sx, sy, rx + 1.4f, ry + 1.4f, 0xFF120A06, 1f);
        blade(f, sx, sy, rx, ry, k);
        if (k.glow != 0) f.ringAA(sx, sy, rx + 0.8f, ry + 0.8f, 1f, k.glow, 0.35f);
        if (p.swing > 0f) f.ringAA(sx, sy, rx + 2f + 4f * (1f - p.swing), ry + 2f + 4f * (1f - p.swing), 1.2f, 0xFFFFFFFF, 0.5f * p.swing);
    }

    private static void blade(Px f, float cx, float cy, float rx, float ry, Racket k) {
        int x0 = (int) Math.floor(cx - rx - 1), x1 = (int) Math.ceil(cx + rx + 1);
        int y0 = (int) Math.floor(cy - ry - 1), y1 = (int) Math.ceil(cy + ry + 1);
        float e = 1f / Math.min(rx, ry);
        float edgeIn = 1f - Math.max(1.3f, 0.06f * rx) * e;
        for (int y = y0; y <= y1; y++)
            for (int x = x0; x <= x1; x++) {
                float u = (x + 0.5f - cx) / rx, v = (y + 0.5f - cy) / ry;
                float d = (float) Math.sqrt(u * u + v * v);
                float cov = (1f - d) / e + 0.5f;
                if (cov <= 0f) continue;
                float lit = Col.clamp01(0.5f - 0.42f * u - 0.55f * v);
                int c;
                if (d > edgeIn) {
                    c = Col.mix(k.edgeDark, k.edgeLight, lit);
                } else {
                    float grain = (Col.hash(x * 3 + 1, y * 5 + 2) - 0.5f) * k.grain;
                    c = Col.mix(k.faceDark, k.face, Col.clamp01(0.35f + 0.75f * lit + grain));
                    // a soft sheen along the lit rim, as on a curved face
                    float rim = Col.ramp(0.55f, 0.92f, d / edgeIn) * Col.clamp01(-0.6f * u - 0.8f * v);
                    c = Col.mix(c, k.faceLight, rim * 0.55f);
                    // the inner edge casts a thin line on the face
                    float lip = Col.ramp(edgeIn - 1.5f * e, edgeIn, d);
                    c = Col.mix(c, k.faceDark, lip * 0.6f);
                }
                f.blend(x, y, c, Math.min(1f, cov));
            }
    }

    /** The net: posts, a mesh you can see through, the white band on top; it ripples when struck. */
    private void net(Px f, Game g) {
        final float nz = Game.NZ, hw = Game.NET_HW;
        if (!cam.project(0f, Game.NH + 0.02f, nz)) return;
        int y0 = (int) Math.floor(cam.sy) - 1;
        if (!cam.project(0f, -0.01f, nz)) return;
        int y1 = (int) Math.ceil(cam.sy) + 1;
        final float inv = 1f / cam.f;
        float age = g.netHitAge, hit = g.netHitX, power = g.netHitPower;
        boolean ripple = age < 1.2f;
        int tape = realm.netTape(), threadC = realm.netThread();
        for (int sy = Math.max(0, y0); sy <= Math.min(h - 1, y1); sy++) {
            float wyA = netY(sy), wyB = netY(sy + 1), wy = netY(sy + 0.5f);
            float footY = Math.abs(wyA - wyB) + 1e-6f;
            float covY = band(wy, 0f, Game.NH, footY);
            if (covY <= 0f) continue;
            float t = netT(sy + 0.5f);
            float footX = t * inv;
            for (int sx = 0; sx < w; sx++) {
                float wx = t * (sx + 0.5f - cam.cx) * inv;
                float cov = covY * band(wx, -hw, hw, footX);
                if (cov <= 0f) continue;
                float yy = wy, glow = 0f;
                if (ripple) {
                    float d = Math.abs(wx - hit);
                    float wave = (float) Math.exp(-age * 3.5f - d * 2.5f) * power;
                    yy += 0.012f * wave * (float) Math.sin(d * 28f - age * 34f);
                    glow = wave * 0.6f;
                }
                int i = sy * w + sx;
                if (yy > Game.NH - 0.018f) {
                    f.a[i] = Col.mix(f.a[i], tape, cov);
                    continue;
                }
                float cell = 0.024f;
                float mx = Math.abs(wx / cell - Math.round(wx / cell)) * cell;
                float my = Math.abs(yy / cell - Math.round(yy / cell)) * cell;
                float lwX = Math.max(0.0025f, footX * 0.45f), lwY = Math.max(0.0025f, footY * 0.45f);
                float thread = Math.max(Col.clamp01((lwX - mx) / footX + 0.5f), Col.clamp01((lwY - my) / footY + 0.5f));
                int c = Col.mix(f.a[i], 0xFF060A16, 0.28f * cov);
                c = Col.mix(c, threadC, thread * 0.6f * cov);
                if (glow > 0f) c = Col.addScaled(c, realm.ripple(), glow * cov);
                f.a[i] = c;
            }
        }
        // posts at both ends
        for (int s = -1; s <= 1; s += 2) {
            if (!cam.project(s * hw, -THICK, nz)) continue;
            float ax = cam.sx, ay = cam.sy, sc = cam.scale();
            if (!cam.project(s * hw, Game.NH + 0.012f, nz)) continue;
            float pw = Math.max(2f, 0.03f * sc);
            f.lineAA(ax, ay, cam.sx, cam.sy, pw + 1f, 0xFF05070E, 1f);
            f.lineAA(ax, ay, cam.sx, cam.sy, pw, 0xFF3A4660, 1f);
            f.lineAA(ax - pw * 0.2f, ay, cam.sx - pw * 0.2f, cam.sy, Math.max(0.8f, pw * 0.3f), 0xFF9FC4E8, 0.7f);
        }
    }

    private float netT(float row) {
        float dcy = -(row - cam.cy) / cam.f;
        float dirZ = dcy * cam.sn + cam.cs;
        return dirZ > 1e-4f ? (Game.NZ - cam.z) / dirZ : 1e9f;
    }

    private float netY(float row) {
        float dcy = -(row - cam.cy) / cam.f;
        return cam.y + netT(row) * (dcy * cam.cs - cam.sn);
    }

    /** A pixel read between pixels, blended from its four neighbours. */
    static int sample(int[] src, int bw, int bh, float fx, float fy) {
        int ix = (int) Math.floor(fx), iy = (int) Math.floor(fy);
        float ax = fx - ix, ay = fy - iy;
        int c00 = at(src, bw, bh, ix, iy), c10 = at(src, bw, bh, ix + 1, iy);
        int c01 = at(src, bw, bh, ix, iy + 1), c11 = at(src, bw, bh, ix + 1, iy + 1);
        return Col.mix(Col.mix(c00, c10, ax), Col.mix(c01, c11, ax), ay);
    }

    private static int at(int[] src, int bw, int bh, int x, int y) {
        if (x < 0 || y < 0 || x >= bw || y >= bh) return 0xFF000000;
        return src[y * bw + x];
    }
}
