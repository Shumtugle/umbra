package com.shumtugle.umbra;

/**
 * The camera: high above the near end of the table, looking down the court
 * steeply enough that the table fills the frame. World units are metres; x runs
 * across the table, y up from the table top, z along the court from the near end
 * line (0) to the far end (2.74). There is no roll, so rows of the screen are
 * rows of constant depth on any horizontal plane.
 */
final class Cam {
    static final float PITCH = 0.9425f;    // radians below the horizon (54 degrees)
    static final float HEIGHT = 3.8f, BACK = -0.2f;

    final float x = 0f, y = HEIGHT, z = BACK;
    final float sn = (float) Math.sin(PITCH), cs = (float) Math.cos(PITCH);
    final float f, cx, cy;

    float sx, sy, zc;    // result of project()
    float gx, gz;        // result of ground()

    Cam(int w, int h) {
        // Fit the court: the near edge of the table nearly as wide as the screen, and
        // everything that matters, from a ball high over the far end to the player's
        // paddle well behind the near one, inside the height.
        float xNear = unit(Game.HW, 0f, 0f, true);
        float top = unit(0f, 0.5f, Game.L + 0.6f, false);
        float bottom = unit(0f, 0.2f, -0.55f, false);
        float fw = 0.96f * w / 2f / xNear;
        float fh = 0.92f * h / (bottom - top);
        f = Math.min(fw, fh);
        cx = w / 2f;
        cy = 0.515f * h - f * (top + bottom) / 2f;
    }

    /** The unit-focal screen offset of a world point: across when wantX, else down. */
    private float unit(float wx, float wy, float wz, boolean wantX) {
        float dy = wy - y, dz = wz - z;
        float zz = -dy * sn + dz * cs;
        float yy = dy * cs + dz * sn;
        return wantX ? (wx - x) / zz : -yy / zz;
    }

    /** Projects a world point; false when it is behind the camera. */
    boolean project(float wx, float wy, float wz) {
        float dx = wx - x, dy = wy - y, dz = wz - z;
        float zz = -dy * sn + dz * cs;
        if (zz < 0.05f) return false;
        float yy = dy * cs + dz * sn;
        float k = f / zz;
        sx = cx + dx * k;
        sy = cy - yy * k;
        zc = zz;
        return true;
    }

    /** Screen pixels per metre at the depth of the last projected point. */
    float scale() {
        return f / zc;
    }

    /** The point on the horizontal plane at height planeY seen through screen pixel (px, py). */
    boolean ground(float px, float py, float planeY) {
        float dxn = (px - cx) / f;
        float dcy = -(py - cy) / f;
        float dirY = dcy * cs - sn;
        float dirZ = dcy * sn + cs;
        if (dirY > -1e-4f) return false;
        float t = (planeY - y) / dirY;
        gx = x + t * dxn;
        gz = z + t * dirZ;
        return true;
    }
}
