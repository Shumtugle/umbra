package com.shumtugle.umbra;

/**
 * The camera: high above the near end of the table, looking down the court
 * steeply enough that the table fills the frame. World units are metres; x runs
 * across the table, y up from the table top, z along the court from the near end
 * line (0) to the far end (2.74). There is no roll, so rows of the screen are
 * rows of constant depth on any horizontal plane.
 *
 * Every realm has an eye of its own, and it moves a little from one match to the
 * next. The eye is fixed for a whole match: the table and the room are painted
 * once for it, and the paddle under the finger never drifts while the view holds.
 */
final class Cam {
    /** Where the eye stands and what it keeps in view. */
    static final class Eye {
        final float height, pitch, back;   // metres up, radians below the horizon, metres behind the near end
        final float topY, topZ;            // the farthest thing kept in view: a ball this high this far back
        final float deep;                  // the paddle's deepest place behind the near end that stays in view

        Eye(float height, float pitchDeg, float back, float topY, float topBehind, float deep) {
            this.height = height;
            this.pitch = (float) Math.toRadians(pitchDeg);
            this.back = back;
            this.topY = topY;
            this.topZ = Game.L + topBehind;
            this.deep = deep;
        }

        /** The same eye moved a little for a given match: up or down, tilted, forward or back. */
        Eye vary(int match, String realm) {
            int k = realm.hashCode();
            float a = 2f * Col.hash(match, k) - 1f, b = 2f * Col.hash(k, match * 7 + 1) - 1f, c = 2f * Col.hash(match * 13 + 5, k ^ 77) - 1f;
            return new Eye(height + 0.3f * a, (float) Math.toDegrees(pitch) + 2.5f * b, back + 0.12f * c,
                    topY, topZ - Game.L, deep);
        }

        boolean same(Eye o) {
            return o != null && o.height == height && o.pitch == pitch && o.back == back && o.topY == topY
                    && o.topZ == topZ && o.deep == deep;
        }
    }

    /** The usual eye: high and well back, so the far half is not squeezed. */
    static final Eye HIGH = new Eye(4.6f, 56f, 0.1f, 0.35f, 0.4f, -0.45f);

    /** An eye set on the stand, to try a view; null leaves each realm its own. */
    static Eye tried;
    /** The band at the bottom of the screen, as a share of the width, kept for the finger and the score. */
    static final float BAND = 0.21f;
    /** How far above the finger the paddle rides, as a share of the width. */
    static final float LIFT = 0.13f;

    final Eye eye;
    final float x = 0f, y, z;
    final float sn, cs;
    final float f, cx, cy;

    float sx, sy, zc;    // result of project()
    float gx, gz;        // result of ground()

    Cam(int w, int h, Eye eye) {
        this.eye = eye;
        y = eye.height;
        z = eye.back;
        sn = (float) Math.sin(eye.pitch);
        cs = (float) Math.cos(eye.pitch);
        // Fit the court: the near edge of the table nearly as wide as the screen, and
        // everything that matters, from a ball high over the far end to the player's
        // paddle well behind the near one, inside the height.
        float xNear = unit(Game.HW, 0f, 0f, true);
        float top = unit(0f, eye.topY, eye.topZ, false);
        float bottom = unit(0f, 0.2f, eye.deep, false);
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
