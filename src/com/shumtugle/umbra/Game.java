package com.shumtugle.umbra;

import java.util.Random;

/**
 * The match: two paddles, the ball, the realm, the score and the rules of the
 * rally. Plain Java; the phone only hears about it through Sink.
 *
 * World units are metres; x runs across the table, y up from the table top, z
 * along the court from the near end line (0) to the far one (L). A paddle moves
 * over the floor plane at a fixed height. A ring on the table under it shows the
 * zone it can strike: a ball inside that zone, low enough, that has bounced once
 * on the paddle's own half, is hit.
 */
final class Game {
    static final float HW = 0.7625f, L = 2.74f, NZ = L / 2f, NH = 0.1525f, NET_HW = HW + 0.15f;
    static final float PY = 0.20f;        // height of the paddle heads
    static final float RH = 0.21f;        // radius of the strike zone
    static final float REACH = 0.85f;     // how far past the end line a paddle may go
    static final float FRONT = 0.90f;     // how far over the table
    static final float SIDE = 1.15f;
    static final float HIT_LOW = -0.02f, HIT_HIGH = 0.55f;
    static final float STEP = 0.004f;     // physics step, seconds

    static final int SERVE = 0, RALLY = 1, POINT = 2, OVER = 3;
    static final int C_PADDLE = 0, C_TABLE = 1, C_NET = 2, C_CORD = 3, C_GONE = 4, C_POINT = 5, C_LOSS = 6,
            C_WON = 7, C_LOST = 8, C_LET = 9, C_START = 10, C_TICK = 11, C_SHOT = 12;
    static final float SHOT_SWING = 2.1f;   // a forward swipe this fast, in m/s, fires a shot where shots exist
    static final int TO_WIN = 11;

    interface Sink {
        void cue(int id, float vol, float pitch);

        void buzz(int ms);
    }

    /** A paddle on the floor plane. Humans set a target; the machine drives it. */
    static final class Paddle {
        final int side;
        float x, z, vx, vz;
        float tx, tz;
        float swing;       // 1 at the moment of a strike, fading

        Paddle(int side) {
            this.side = side;
            x = tx = 0f;
            z = tz = side == 0 ? -0.25f : L + 0.25f;
        }

        float zMin() {
            return side == 0 ? -REACH : L - FRONT;
        }

        float zMax() {
            return side == 0 ? FRONT : L + REACH;
        }

        /** The finger's pull: an exponential catch-up, with the speed measured for the swing. */
        void follow(float dt) {
            float k = 1f - (float) Math.exp(-dt * 30f);
            moveTo(x + (tx - x) * k, z + (tz - z) * k, dt);
        }

        /** The machine's walk: straight toward the goal at no more than vmax. */
        void drive(float gx, float gz, float vmax, float dt) {
            float dx = gx - x, dz = gz - z;
            float d = (float) Math.sqrt(dx * dx + dz * dz);
            float step = vmax * dt;
            if (d <= step || d < 1e-5f) moveTo(gx, gz, dt);
            else moveTo(x + dx / d * step, z + dz / d * step, dt);
        }

        private void moveTo(float nx, float nz, float dt) {
            nx = Math.max(-SIDE, Math.min(SIDE, nx));
            nz = Math.max(zMin(), Math.min(zMax(), nz));
            float ivx = (nx - x) / dt, ivz = (nz - z) / dt;
            vx += (ivx - vx) * 0.5f;
            vz += (ivz - vz) * 0.5f;
            x = nx;
            z = nz;
            swing = Math.max(0f, swing - dt * 5f);
        }

        void place(float px, float pz) {
            x = tx = px;
            z = tz = pz;
            vx = vz = 0f;
        }
    }

    final Sink sink;
    final Realm realm;
    final Ball ball = new Ball();
    final Paddle[] pad = {new Paddle(0), new Paddle(1)};
    final Ai[] ai;
    final Random rnd;
    boolean human = true;        // false: the machine plays both sides (the title screen)
    final int[] score = new int[2];
    int state = SERVE, server, firstServer, hitter, bounces, winner = -1;
    boolean held, serveAsked, serving, netTouched;
    float timer, clock;
    String msg = "";
    int msgSide = -1;
    float msgT;
    float lastSpeed;            // speed of the last strike, for the effects
    float netHitX, netHitAge = 9f, netHitPower;   // where and how long ago the net was struck
    float bounceX, bounceZ, bounceAge = 9f;       // the last bounce on the table
    float goneAge = 9f;                          // time since the realm took the ball
    private float lastNetCue = -1f;
    int shots, points;          // strikes and points so far, counted for the stand
    final int[] shotsLeft = new int[2];   // shots each side may still fire this match, where the realm allows
    int nets, gones;            // how many points ended in the net or were taken by the realm
    String lastReason = "";
    int lastWinner = -1;

    Game(Sink sink, Realm realm, long seed) {
        this.sink = sink;
        this.realm = realm;
        rnd = new Random(seed);
        ai = new Ai[]{new Ai(0, rnd), new Ai(1, rnd)};
        realm.at(0f);
    }

    void startMatch(boolean humanPlays, float skill) {
        human = humanPlays;
        ai[0].skill = ai[1].skill = skill;
        score[0] = score[1] = 0;
        shotsLeft[0] = shotsLeft[1] = realm.shots();
        winner = -1;
        firstServer = rnd.nextInt(2);
        pad[0].place(0f, -0.25f);
        pad[1].place(0f, L + 0.25f);
        nextServe();
    }

    private void nextServe() {
        int total = score[0] + score[1];
        boolean deuce = score[0] >= TO_WIN - 1 && score[1] >= TO_WIN - 1;
        server = firstServer ^ ((deuce ? total : total / 2) & 1);
        state = SERVE;
        held = true;
        serveAsked = false;
        ball.gone = false;
        timer = 0.9f + 0.4f * rnd.nextFloat();
        ai[0].forget();
        ai[1].forget();
        say(server == 0 ? (human ? "YOUR SERVE" : "SERVE") : (human ? "CPU SERVES" : "SERVE"), server, 1.2f);
    }

    void say(String s, int side, float seconds) {
        msg = s;
        msgSide = side;
        msgT = seconds;
    }

    /** The finger: where the player's paddle should go (floor coordinates). */
    void aimPaddle(float x, float z) {
        pad[0].tx = x;
        pad[0].tz = z;
    }

    /** The finger lifted. If the ball is waiting on the player's paddle, this serves it. */
    void release() {
        if (state == SERVE && human && server == 0 && held) serveAsked = true;
    }

    void update(float dt) {
        if (msgT > 0f) msgT -= dt;
        netHitAge += dt;
        bounceAge += dt;
        goneAge += dt;
        if (human) pad[0].follow(dt);
        else ai[0].move(pad[0], dt);
        ai[1].move(pad[1], dt);
        switch (state) {
            case SERVE:
                clock += dt;
                realm.at(clock);
                updateServe(dt);
                break;
            case RALLY:
                advance(dt);
                break;
            case POINT:
                advance(dt);
                timer -= dt;
                if (timer <= 0f) {
                    if (winner >= 0) state = OVER;
                    else nextServe();
                }
                break;
            default:
                clock += dt;
                realm.at(clock);
                break;
        }
    }

    private void updateServe(float dt) {
        Paddle p = pad[server];
        ball.x = p.x;
        ball.z = p.z + (server == 0 ? 0.07f : -0.07f);
        ball.y = PY + 0.12f + 0.012f * (float) Math.sin(clock * 7f);
        ball.vx = ball.vy = ball.vz = 0f;
        boolean machine = server == 1 || !human;
        if (machine) {
            timer -= dt;
            if (timer <= 0f) serve();
        } else if (serveAsked) {
            serve();
        }
    }

    private void serve() {
        Paddle p = pad[server];
        float power, aim, wobble = 0f;
        if (server == 0 && human) {
            power = Col.clamp01((p.vz + 0.5f) / 2.4f) * 0.7f;
            aim = 0.5f * p.x + 0.3f * p.vx;
        } else {
            power = ai[server].servePower();
            aim = ai[server].serveAim();
            wobble = ai[server].wobble() * 0.6f;
        }
        held = false;
        launch(server, power, aim, 1f, wobble, false);
        state = RALLY;
        hitter = server;
        bounces = 0;
        serving = true;
        netTouched = false;
        ai[1 - server].plan(this);
    }

    /**
     * Sends the ball from where it is to a point on the other half, as a ballistic
     * arc. Power sets how deep and how flat it goes; a flat ball is fast but crosses
     * the net low. Quality (1 = clean) and wobble (the machine's unsteadiness) set
     * how far the ball strays from the aim, in depth, across and in height over the
     * net, so a hard or careless shot can land in the net or out.
     */
    private void launch(int side, float power, float aimX, float quality, float wobble, boolean shot) {
        Ball b = ball;
        float dir = side == 0 ? 1f : -1f;
        power = Math.min(1.2f, power + realm.strikeBoost(side, pad[side].z));
        if (shot) {
            // a shot: as hard and as deep as a ball can go, true across, but low and wild over the net
            power = 1.1f;
            quality = 1f;
            wobble = 0f;
        }
        float miss = 1f - quality;
        float err = 0.012f + 0.2f * miss * miss + 0.10f * wobble;
        float tz = NZ + dir * (0.35f + 0.85f * power) + (float) rnd.nextGaussian() * err;
        float tx = Math.max(-0.62f, Math.min(0.62f, aimX)) + (float) rnd.nextGaussian() * err;
        // the height of the ball's centre where it crosses the net
        float clear = shot ? 0.012f : 0.035f + 0.12f * Math.max(0f, 1f - power) * Math.max(0f, 1f - power);
        float scatter = 0.008f + 0.012f * power + 0.05f * miss + 0.08f * wobble + (shot ? 0.03f : 0f);
        float yn = NH + Ball.R + clear + (float) rnd.nextGaussian() * scatter;
        // flight time that passes through that height and comes down on the target
        float s = (NZ - b.z) / (tz - b.z);
        float time = 0.5f;
        if (s > 0.05f && s < 0.95f) {
            float num = 2f * (yn - b.y * (1f - s) - s * Ball.R);
            time = num > 0f ? (float) Math.sqrt(num / (Ball.G * s * (1f - s))) : 0.24f;
        }
        time = Math.max(0.24f, Math.min(1.1f, time));
        b.impetus = 0f;
        if (!realm.launch(b, side, tx, tz, yn, power)) {
            b.vx = (tx - b.x) / time;
            b.vz = (tz - b.z) / time;
            b.vy = (Ball.R - b.y + 0.5f * Ball.G * time * time) / time;
        }
        lastSpeed = (float) Math.sqrt(b.vx * b.vx + b.vz * b.vz);
        shots++;
        pad[side].swing = 1f;
        sink.cue(C_PADDLE, 0.55f + 0.45f * Math.min(1f, power), 0.92f + 0.22f * Math.min(1f, power) + 0.06f * rnd.nextFloat());
        if (shot) {
            sink.cue(C_SHOT, 1f, 0.95f + 0.1f * rnd.nextFloat());
            realm.onShot(this, side);
            say("SHOT", side, 0.8f);
        }
        if (side == 0 && human) sink.buzz(shot ? 45 : 12);
    }

    private void advance(float dt) {
        int n = (int) Math.ceil(dt / STEP);
        float h = dt / n;
        for (int i = 0; i < n; i++) {
            realm.at(clock);
            int ev = ball.step(h, clock, realm);
            clock += h;
            if (state == RALLY) rules(ev);
            else if (ev == Ball.BOUNCE) {
                bounceAt();
                sink.cue(C_TABLE, Col.clamp01(Math.abs(ball.vy) / 3.5f) * 0.5f + 0.1f, 0.85f + 0.3f * rnd.nextFloat());
            } else if (ev == Ball.NET || ev == Ball.CORD) netAt(ev);
        }
        realm.at(clock);
    }

    private void bounceAt() {
        bounceX = ball.x;
        bounceZ = ball.z;
        bounceAge = 0f;
    }

    private void netAt(int ev) {
        float speed = Math.abs(ball.vz) + Math.abs(ball.vy);
        netHitX = ball.x;
        netHitAge = 0f;
        netHitPower = Col.clamp01(0.3f + speed / 4f);
        if (clock - lastNetCue > 0.08f) {
            lastNetCue = clock;
            if (ev == Ball.NET) sink.cue(C_NET, 0.5f + 0.5f * netHitPower, 0.9f + 0.2f * rnd.nextFloat());
            else sink.cue(C_CORD, 0.6f + 0.4f * netHitPower, 0.95f + 0.1f * rnd.nextFloat());
        }
    }

    private void rules(int ev) {
        Ball b = ball;
        if (ev == Ball.GONE) {
            goneAge = 0f;
            gones++;
            sink.cue(C_GONE, 1f, 1f);
            if (human) sink.buzz(40);
            point(1 - hitter, realm.takenReason());
            return;
        }
        if (ev == Ball.NET || ev == Ball.CORD) {
            netTouched = true;
            netAt(ev);
        }
        if (ev == Ball.BOUNCE) {
            bounceAt();
            sink.cue(C_TABLE, Col.clamp01(Math.abs(b.vy) / 3.5f) * 0.8f + 0.2f, 0.85f + 0.3f * rnd.nextFloat());
            int half = b.z < NZ ? 0 : 1;
            if (half == hitter) {
                if (netTouched) nets++;
                point(1 - hitter, netTouched ? "NET" : "SHORT");
                return;
            }
            if (serving && netTouched) {
                let();
                return;
            }
            serving = false;
            bounces++;
            if (bounces >= 2) {
                point(hitter, "2 BOUNCES");
                return;
            }
        }
        int r = 1 - hitter;
        if (bounces >= 1 && tryStrike(r)) return;
        boolean beyond = r == 0 ? b.z < -REACH - 0.15f : b.z > L + REACH + 0.15f;
        boolean fell = b.y < -0.05f && !Ball.onTable(b.x, b.z);
        if (beyond || fell) {
            if (bounces == 0) {
                if (netTouched) nets++;
                point(r, netTouched ? "NET" : "OUT");
            } else point(hitter, "MISS");
        }
    }

    /** A serve that touched the net and still landed right is played again. */
    private void let() {
        state = POINT;
        timer = 1.2f;
        say("LET", -1, 1.1f);
        sink.cue(C_LET, 0.7f, 1f);
        ai[0].forget();
        ai[1].forget();
    }

    /** The receiving paddle meets the ball. */
    private boolean tryStrike(int r) {
        Ball b = ball;
        Paddle p = pad[r];
        boolean toward = r == 0 ? b.vz < 0.3f : b.vz > -0.3f;
        if (!toward || b.y < HIT_LOW || b.y > HIT_HIGH) return false;
        float dx = b.x - p.x, dz = b.z - p.z;
        float d2 = dx * dx + dz * dz;
        if (d2 > RH * RH) return false;
        float rq = 1f - d2 / (RH * RH);
        float vq = b.y >= 0.04f && b.y <= 0.34f ? 1f : b.y < 0.04f ? 0.6f + 10f * Math.max(0f, b.y + 0.02f) : Math.max(0.4f, 1f - (b.y - 0.34f) * 2f);
        float q = Col.clamp01(0.25f + 0.75f * rq * vq);
        float power, aim, wobble = 0f;
        boolean shot = false;
        if (r == 0 && human) {
            power = Col.clamp01((p.vz + 0.5f) / 2.4f);
            aim = 0.5f * b.x + 0.35f * p.vx + 1.2f * dx;
            shot = shotsLeft[0] > 0 && p.vz > SHOT_SWING;
        } else {
            power = ai[r].power();
            aim = ai[r].aim(pad[1 - r].x);
            wobble = ai[r].wobble();
            shot = shotsLeft[r] > 0 && ai[r].wantsShot(score[r], score[1 - r]);
        }
        if (shot) shotsLeft[r]--;
        launch(r, power, aim, q, wobble, shot);
        hitter = r;
        bounces = 0;
        serving = false;
        netTouched = false;
        ai[r].forget();
        ai[1 - r].plan(this);
        return true;
    }

    private void point(int winnerSide, String why) {
        score[winnerSide]++;
        points++;
        lastReason = why;
        lastWinner = winnerSide;
        state = POINT;
        timer = goneAge < 0.1f ? 1.9f : 1.5f;
        say(why, winnerSide, timer - 0.1f);
        boolean over = (score[0] >= TO_WIN || score[1] >= TO_WIN) && Math.abs(score[0] - score[1]) >= 2;
        if (over) winner = score[0] > score[1] ? 0 : 1;
        boolean mine = human && winnerSide == 0;
        if (!human) sink.cue(C_POINT, 0.3f, 1f);
        else if (over) sink.cue(mine ? C_WON : C_LOST, 1f, 1f);
        else sink.cue(mine ? C_POINT : C_LOSS, 0.8f, 1f);
        ai[0].forget();
        ai[1].forget();
    }
}
