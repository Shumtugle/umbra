package com.shumtugle.umbra;

import java.util.Random;

/**
 * A player made of touches. It reads the ball the way a person would from the
 * screen (a prediction of where it comes down), moves the finger at a human top
 * speed, and swipes through the ball with the same events a finger makes. It
 * never calls the game directly, so it cannot bend a rule.
 */
final class Bot {
    private final World w;
    private final Random rnd;
    float noise = 0.04f;        // metres of error in where it puts the ring
    float vmax = 4.5f;          // top speed of the finger on the table, m/s
    float swipe = 2.4f;         // forward speed of the swipe through the ball, m/s
    float react = 0f;           // seconds before it starts to move after the other side strikes

    private float now, lastTap;
    private float fx, fz = -0.25f;     // finger position on the paddle plane
    private boolean down;
    private float serveAt;
    private int plannedShot = -1;
    private float ix, iz, hitAt, goAt;

    Bot(World w, long seed) {
        this.w = w;
        rnd = new Random(seed);
    }

    private void press(boolean isDown) {
        Cam c = w.scene.cam;
        if (!c.project(fx, Game.PY, fz)) return;
        float x = c.sx, y = c.sy + w.lift;
        if (isDown) {
            if (!down) w.touch(World.DOWN, x, y);
            else w.touch(World.MOVE, x, y);
            down = true;
        } else {
            w.touch(World.UP, x, y);
            down = false;
        }
    }

    private void goTo(float gx, float gz, float speed, float dt) {
        float dx = gx - fx, dz = gz - fz;
        float d = (float) Math.sqrt(dx * dx + dz * dz), step = speed * dt;
        if (d <= step) {
            fx = gx;
            fz = gz;
        } else {
            fx += dx / d * step;
            fz += dz / d * step;
        }
    }

    void step(float dt) {
        now += dt;
        if (w.screen == World.MENU || (w.screen == World.RESULT && w.resultTime > 1.0f)) {
            if (now - lastTap > 0.6f) {
                w.touch(World.DOWN, w.w / 2f, w.h / 2f);
                w.touch(World.UP, w.w / 2f, w.h / 2f);
                lastTap = now;
                down = false;
            }
            return;
        }
        if (w.screen != World.PLAY) return;
        Game g = w.game;
        if (g.state == Game.SERVE && g.server == 0 && g.held) {
            if (!down) {
                fx = (rnd.nextFloat() - 0.5f) * 0.8f;
                fz = -0.25f;
                press(true);
                serveAt = now + 0.3f;
            } else {
                goTo(fx, -0.1f, 1.0f, dt);
                press(true);
                if (now >= serveAt) press(false);
            }
            return;
        }
        if (g.state != Game.RALLY) {
            if (down) press(true);
            return;
        }
        if (!down) press(true);
        if (g.hitter == 1 && plannedShot != g.shots) plan(g);
        if (g.hitter == 1 && plannedShot == g.shots && now < goAt) {
            press(true);
            return;
        }
        if (g.hitter == 1 && plannedShot == g.shots) {
            float t = hitAt - now;
            if (t > 0.10f) goTo(ix, iz - 0.22f, vmax, dt);
            else goTo(ix, iz + 0.13f, swipe, dt);
        } else {
            goTo(0f, -0.3f, 1.2f, dt);
        }
        press(true);
    }

    /** Reads the ball: the first moment after its bounce when it is low enough to strike. */
    private void plan(Game g) {
        plannedShot = g.shots;
        goAt = now + react * (0.85f + 0.3f * rnd.nextFloat());
        Ball s = new Ball();
        s.set(g.ball);
        boolean landed = false;
        float t = 0f;
        ix = s.x;
        iz = -0.3f;
        hitAt = now + 0.6f;
        for (int i = 0; i < 960; i++) {
            boolean b = s.step(1f / 240f, g.clock + t, g.realm) == Ball.BOUNCE;
            t += 1f / 240f;
            if (s.gone) break;
            if (b && s.z < Game.NZ) landed = true;
            if (landed && s.y >= 0.10f && s.y <= 0.40f && s.vz < 0.05f && s.z < Game.FRONT - 0.05f && s.z > -0.75f) {
                ix = s.x;
                iz = s.z;
                hitAt = now + t;
                break;
            }
            if (s.y < -0.5f) break;
        }
        ix += (float) rnd.nextGaussian() * noise;
        iz += (float) rnd.nextGaussian() * noise * 0.5f;
    }
}
