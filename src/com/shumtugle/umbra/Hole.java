package com.shumtugle.umbra;

/**
 * The black hole that drifts over the net. It pulls the ball, harder the closer
 * the ball passes, and a ball that crosses the horizon is gone. None of it is
 * hidden: the light bends around the hole, the grid on the table crowds toward
 * it, the ball's tail curves, and the hum grows as the ball comes near.
 *
 * Its place is a function of time alone, so a prediction that runs the clock
 * forward sees the hole exactly where the game will put it.
 */
final class Hole {
    static final float STRENGTH = 0.42f;
    static final float REACH2 = 1.2f;       // no pull beyond this squared distance
    static final float SOFT = 0.06f;        // softens the pull near the centre
    static final float Y = 0.42f;           // height above the table top
    static final float HORIZON = 0.055f;    // a ball whose centre gets this close is swallowed

    float x, z = Game.NZ;

    /** Moves the hole to where it is at time t. */
    void at(float t) {
        x = posX(t);
        z = posZ(t);
    }

    static float posX(float t) {
        return 0.55f * (float) Math.sin(t * 0.45f);
    }

    static float posZ(float t) {
        return Game.NZ + 0.30f * (float) Math.sin(t * 0.31f + 1f);
    }

    /**
     * Pulls the ball for h seconds at time t. True when the ball has crossed the
     * horizon and is gone.
     */
    static boolean pull(Ball b, float t, float h) {
        float dx = posX(t) - b.x, dy = Y - b.y, dz = posZ(t) - b.z;
        float r2 = dx * dx + dy * dy + dz * dz;
        if (r2 < HORIZON * HORIZON) return true;
        if (r2 > REACH2) return false;
        float a = STRENGTH / (r2 + SOFT) * h / (float) Math.sqrt(r2 + 1e-4f);
        b.vx += dx * a;
        b.vy += dy * a;
        b.vz += dz * a;
        return false;
    }

    /** 0 far away .. 1 at the horizon: how close the ball is, for the sound and the glow. */
    float closeness(Ball b) {
        float dx = x - b.x, dy = Y - b.y, dz = z - b.z;
        float r = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        return Col.clamp01(1f - (r - HORIZON) / 0.9f);
    }
}
