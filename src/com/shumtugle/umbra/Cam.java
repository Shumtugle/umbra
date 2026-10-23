package com.shumtugle.umbra;

/**
 * The camera: high above the near end of the table, looking down the court
 * steeply enough that the table fills the frame. World units are metres; x runs
 * across the table, y up from the table top, z along the court from the near end
 * line (0) to the far end (2.74). There is no roll, so rows of the screen are
 * rows of constant depth on any horizontal plane.
 */
final class Cam {
    // where the eye is; not final, so that the stand can try other views
    static float PITCH = 0.9425f;          // radians below the horizon (54 degrees)
    static float HEIGHT = 3.8f, BACK = -0.2f;
    // the farthest thing kept in view: a ball this high this far behind the far end
    static float TOP_Y = 0.5f, TOP_Z = Game.L + 0.6f;
    // the paddle's deepest place behind the near end that must stay in view
    static float DEEP = -0.55f;
    /** The band at the bottom of the screen, as a share of the width, kept for the finger and the score. */
    static final float BAND = 0.18f;
    /** How far above the finger the paddle rides, as a share of the width. */
    static final float LIFT = 0.13f;

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
        float top = unit(0f, TOP_Y, TOP_Z, false);
        float bottom = unit(0f, 0.2f, DEEP, false);
        // the court fits between a thin margin at the top and the lowest place the paddle can
        // reach, a lift above a finger at the very bottom; the score lies under that finger
        float y0 = 0.01f * h, y1 = h - LIFT * w - 2f;
        float fw = 0.96f * w / 2f / xNear;
        float fh = (y1 - y0) / (bottom - top);
        f = Math.min(fw, fh);
        cx = w / 2f;
        cy = (y0 + y1) / 2f - f * (top + bottom) / 2f;
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
