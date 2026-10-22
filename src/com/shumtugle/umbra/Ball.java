package com.shumtugle.umbra;

/**
 * The ball and the laws it keeps: the realm's force, gravity, the bounce on the
 * table top, and the net. The net is a real thing in the way: below its top cord
 * the mesh stops the ball dead, and the cord itself deflects whatever grazes it.
 */
final class Ball {
    static final float R = 0.04f;           // radius, a little larger than the real one so it can be seen
    static final float G = 6.2f;            // gravity, gentler than the real one so arcs can be read
    static final float RESTITUTION = 0.82f;
    static final float GRIP = 0.97f;        // horizontal speed kept through a bounce

    /** What happened during a step. */
    static final int NONE = 0, BOUNCE = 1, NET = 2, CORD = 3, GONE = 4;

    float x, y, z, vx, vy, vz;
    boolean gone;
    // for realms where a struck ball carries a push of its own: how long it lasts, and where it ends
    float impetus, impetus0, aimX, aimY, aimZ;
    float charge;               // for realms with fields: +1, -1, or 0

    void set(Ball o) {
        x = o.x;
        y = o.y;
        z = o.z;
        vx = o.vx;
        vy = o.vy;
        vz = o.vz;
        gone = o.gone;
        impetus = o.impetus;
        impetus0 = o.impetus0;
        aimX = o.aimX;
        aimY = o.aimY;
        aimZ = o.aimZ;
        charge = o.charge;
    }

    static boolean onTable(float x, float z) {
        return x >= -Game.HW && x <= Game.HW && z >= 0f && z <= Game.L;
    }

    /** Advances by h seconds, starting at time t, in the given realm. */
    int step(float h, float t, Realm realm) {
        if (gone) return NONE;
        if (realm.act(this, t, h)) {
            gone = true;
            return GONE;
        }
        float pz = z;
        vy -= G * h;
        x += vx * h;
        y += vy * h;
        z += vz * h;
        if (y <= R && vy < 0f && onTable(x, z)) {
            y = R;
            impetus = 0f;
            vy = -vy * RESTITUTION;
            vx *= GRIP;
            vz *= GRIP;
            return BOUNCE;
        }
        return net(pz);
    }

    private int net(float pz) {
        if (Math.abs(x) > Game.NET_HW || y > Game.NH + R || y < -R) return NONE;
        float side = pz < Game.NZ ? -1f : 1f;          // which side the ball came from
        float off = (z - Game.NZ) * side;               // distance from the net plane, on that side
        if (off >= R) return NONE;
        if (y < Game.NH) {
            // the mesh: it swallows most of the speed and drops the ball on its own side
            if (vz * side >= 0f) return NONE;
            z = Game.NZ + side * R;
            impetus = 0f;
            vz = -vz * 0.12f;
            vx *= 0.5f;
            vy = vy > 0f ? vy * 0.3f : vy * 0.6f;
            return NET;
        }
        // the cord: a thin round edge at the top of the mesh
        float dy = y - Game.NH, dz = z - Game.NZ;
        if (off < 0f) dz = 0f;                          // crossed it inside the step: treat as touching from above
        float d = (float) Math.sqrt(dy * dy + dz * dz);
        if (d >= R || d < 1e-5f) return NONE;
        float ny = dy / d, nz = dz / d;
        float vn = vy * ny + vz * nz;
        if (vn >= 0f) return NONE;
        float ty = vy - vn * ny, tz = vz - vn * nz;
        vy = ty * 0.82f - 0.3f * vn * ny;
        vz = tz * 0.82f - 0.3f * vn * nz;
        vx *= 0.9f;
        impetus = 0f;
        y = Game.NH + ny * R;
        z = Game.NZ + nz * R;
        return CORD;
    }
}
