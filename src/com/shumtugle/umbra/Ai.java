package com.shumtugle.umbra;

import java.util.Random;

/**
 * The machine that plays one side. It follows the same rules as a finger: its
 * paddle has a top speed, it reads the ball only when the other side has hit it,
 * it reacts after a delay, and it can only strike what lands inside the reach
 * of its paddle. Skill (0..1) sets how fast it moves, how soon it reacts, how
 * well it reads the bounce, and how steady its returns are: a weak machine
 * sends more balls into the net and out.
 */
final class Ai {
    final int side;
    private final Random rnd;
    float skill = 0.3f;

    private boolean planned;
    private float tx, tz, delay;

    Ai(int side, Random rnd) {
        this.side = side;
        this.rnd = rnd;
    }

    float homeZ() {
        return side == 1 ? Game.L + 0.25f : -0.25f;
    }

    private boolean inZone(Ball s) {
        if (s.y < 0.10f || s.y > 0.45f) return false;
        if (side == 1) return s.z >= Game.L - 0.45f && s.z <= Game.L + 0.75f && s.vz > 0f;
        return s.z <= 0.45f && s.z >= -0.75f && s.vz < 0f;
    }

    private boolean inReach(Ball s) {
        if (s.y < 0.10f || s.y > 0.45f) return false;
        if (side == 1) return s.z >= Game.L - Game.FRONT + 0.05f && s.vz > -0.3f;
        return s.z <= Game.FRONT - 0.05f && s.vz < 0.3f;
    }

    /** The other side has just hit the ball: read where it will come down and set off for it. */
    void plan(Game g) {
        Ball s = new Ball();
        s.set(g.ball);
        boolean landed = false, found = false, spare = false;
        float ix = 0f, iz = homeZ(), sx = 0f, sz = 0f;
        final float h = 1f / 240f;
        float t = g.clock;
        for (int i = 0; i < 960; i++) {
            int ev = s.step(h, t, g.realm);
            t += h;
            if (s.gone) break;
            if (ev == Ball.BOUNCE && (s.z < Game.NZ ? 0 : 1) == side) landed = true;
            if (landed && inZone(s)) {
                ix = s.x;
                iz = s.z;
                found = true;
                break;
            }
            // a ball that stops short, over the table, can still be met where it is
            if (landed && !spare && inReach(s)) {
                spare = true;
                sx = s.x;
                sz = s.z;
            }
            if (s.y < -0.5f) break;
        }
        if (!found && spare) {
            ix = sx;
            iz = sz;
        } else if (!found) {
            ix = s.x;                 // it cannot be reached, but a player still runs for it
            iz = homeZ();
        }
        float sigma = 0.02f + 0.22f * (1f - skill) * (1f - skill);
        tx = ix + (float) rnd.nextGaussian() * sigma;
        tz = iz + (float) rnd.nextGaussian() * sigma * 0.7f;
        delay = 0.08f + 0.34f * (1f - skill) + rnd.nextFloat() * 0.05f;
        planned = true;
    }

    void forget() {
        planned = false;
    }

    /** Moves the paddle for dt seconds. */
    void move(Game.Paddle p, float dt) {
        float gx = 0f, gz = homeZ(), vmax = 0.9f;
        if (planned) {
            if (delay > 0f) {
                delay -= dt;
                gx = p.x;
                gz = p.z;
            } else {
                gx = tx;
                gz = tz;
                vmax = 1.1f + 2.7f * skill;
            }
        }
        p.drive(gx, gz, vmax, dt);
    }

    /** How hard it swings, 0..1. */
    float power() {
        return Col.clamp01(0.2f + 0.8f * skill * (0.45f + 0.55f * rnd.nextFloat()));
    }

    /** How unsteady its hand is, 0..1: the spread of its returns in depth, width and height. */
    float wobble() {
        return 0.55f * (1f - skill);
    }

    /** Where across the other half it aims, given where the other paddle stands. */
    float aim(float otherX) {
        if (rnd.nextFloat() < 0.35f + 0.5f * skill) return (otherX > 0f ? -1f : 1f) * (0.25f + 0.4f * rnd.nextFloat());
        return (rnd.nextFloat() - 0.5f) * 1.0f;
    }

    float servePower() {
        return 0.15f + 0.3f * rnd.nextFloat();
    }

    float serveAim() {
        return (rnd.nextFloat() - 0.5f) * 1.0f;
    }
}
