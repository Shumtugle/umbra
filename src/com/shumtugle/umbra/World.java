package com.shumtugle.umbra;

/**
 * The whole program above the court: the title screen (where the machine plays
 * itself), the match, the result, and the interface drawn over the picture.
 * Plain Java with no platform classes, so the same code runs on the phone and on
 * the desktop stand.
 *
 * The shell calls, always from one thread: touch() for each queued touch,
 * update(dt), then draw(frame). Coordinates are game pixels; the frame is w by h,
 * and each game pixel is a whole number of screen pixels.
 */
final class World implements Game.Sink {
    static final String VERSION = "0.1";   // shown on screen; keep in step with versionName
    static final int TARGET_W = 270;       // about this many game pixels across

    static final int DOWN = 0, MOVE = 1, UP = 2;
    static final int MENU = 0, PLAY = 1, RESULT = 2;

    /** What the game may ask of the phone. The stand answers with nothing. */
    interface Host {
        void buzz(int ms);

        void quit();

        void cue(int id, float vol, float pitch);

        /** The hum of the hole, 0..1, and how near the ball is to it, 0..1. */
        void hum(float level, float near);

        int pref(String key, int def);

        void setPref(String key, int value);
    }

    /** How many screen pixels make one game pixel on a screen this wide. */
    static int pixelSize(int screenW) {
        return Math.max(1, Math.round(screenW / (float) TARGET_W));
    }

    private final Host host;
    int w = TARGET_W, h = 1;
    float lift;                            // the paddle rides this far above the finger
    int screen = MENU;
    int level, won, lost;
    Scene scene;
    Game game;
    boolean touching;
    float clock, resultTime;
    boolean youWon;
    private long seed = 1;
    private float downX, downY;

    World(Host host) {
        this.host = host;
        level = Math.max(1, host.pref("level", 1));
        won = host.pref("won", 0);
        lost = host.pref("lost", 0);
    }

    void resize(int width, int height) {
        w = width;
        h = height;
        lift = 0.13f * w;
        scene = new Scene(w, h);
        if (game == null) attract();
    }

    // ---- sound and haptics, as the game asks for them

    @Override
    public void cue(int id, float vol, float pitch) {
        if (screen != MENU) host.cue(id, vol, pitch);
    }

    @Override
    public void buzz(int ms) {
        host.buzz(ms);
    }

    // ---- flow

    private void attract() {
        game = new Game(this, seed++);
        game.startMatch(false, 0.62f);
        screen = MENU;
    }

    /** The opponent's skill at a level: gentle at first, never perfect. */
    static float skill(int level) {
        return Math.min(0.95f, 0.10f + 0.08f * (level - 1));
    }

    private void begin() {
        game = new Game(this, seed++ * 7919L);
        screen = PLAY;
        game.startMatch(true, skill(level));
        host.cue(Game.C_START, 0.8f, 1f);
    }

    void back() {
        if (screen == MENU) host.quit();
        else attract();
    }

    void touch(int type, float x, float y) {
        if (screen == MENU) {
            if (type == DOWN) {
                downX = x;
                downY = y;
                touching = true;
            } else if (type == UP) {
                touching = false;
                if (Math.abs(x - downX) < 0.1f * w && Math.abs(y - downY) < 0.1f * w) begin();
            }
            return;
        }
        if (type == DOWN) {
            touching = true;
            if (screen == RESULT && resultTime > 0.8f) {
                host.cue(Game.C_TICK, 0.6f, 1f);
                attract();
                return;
            }
        } else if (type == UP) {
            touching = false;
        }
        if (screen != PLAY) return;
        aim(x, y);
        if (type == UP) game.release();
    }

    private void aim(float x, float y) {
        if (scene.cam.ground(x, y - lift, Game.PY)) game.aimPaddle(scene.cam.gx, scene.cam.gz);
    }

    void update(float dt) {
        clock += dt;
        game.update(dt);
        scene.update(game, dt);
        float near = game.ball.gone || game.state == Game.SERVE ? 0f : game.hole.closeness(game.ball);
        host.hum(screen == MENU ? 0.35f : 1f, near * near);
        if (screen == MENU && game.state == Game.OVER) {
            game.startMatch(false, 0.62f);
        } else if (screen == PLAY && game.state == Game.OVER) {
            screen = RESULT;
            resultTime = 0f;
            youWon = game.winner == 0;
            if (youWon) {
                won++;
                level++;
            } else {
                lost++;
                level = Math.max(1, level - 1);
            }
            host.setPref("level", level);
            host.setPref("won", won);
            host.setPref("lost", lost);
        } else if (screen == RESULT) {
            resultTime += dt;
        }
    }

    // ---- drawing

    void draw(Px f) {
        scene.render(f, game, game.clock);
        if (screen == MENU) title(f);
        else scoreboard(f);
        if (screen == PLAY) hints(f);
        if (screen == RESULT) result(f);
        String v = "V" + VERSION;
        PixelFont.draw(f, v, w - PixelFont.width(v, 1) - 4, h - 11, 1, 0xFF56607C);
    }

    private void text(Px f, String s, int x, int y, int scale, int color) {
        PixelFont.draw(f, s, x + scale, y + scale, scale, 0xFF02030A);
        PixelFont.draw(f, s, x, y, scale, color);
    }

    private void center(Px f, String s, int y, int scale, int color) {
        text(f, s, (w - PixelFont.width(s, scale)) / 2, y, scale, color);
    }

    /** Darkens rows y0..y1 by k, fading in and out over a few rows. */
    private void shade(Px f, int y0, int y1, float k) {
        int fade = Math.max(1, (y1 - y0) / 6);
        for (int y = Math.max(0, y0); y < Math.min(h, y1); y++) {
            float e = Math.min(1f, Math.min(y - y0, y1 - 1 - y) / (float) fade);
            float m = 1f - (1f - k) * e;
            for (int x = 0; x < w; x++) f.a[y * w + x] = Col.mul(f.a[y * w + x], m);
        }
    }

    private void title(Px f) {
        for (int i = 0; i < f.a.length; i++) f.a[i] = Col.mul(f.a[i], 0.62f);
        int big = Math.max(4, (int) (w * 0.84f / 30f));
        int ty = (int) (h * 0.12f);
        shade(f, ty - 24, ty + 7 * big + 40, 0.45f);
        int tw = PixelFont.width("UMBRA", big);
        int tx = (w - tw) / 2;
        // the title, lit from below by the disk
        PixelFont.draw(f, "UMBRA", tx + big / 2, ty + big / 2, big, 0xFF000000);
        int[] rows = new int[7];
        for (int r = 0; r < 7; r++) rows[r] = Col.mix(0xFFF6F8FF, 0xFFFFB45A, r / 6f);
        PixelFont.draw(f, "UMBRA", tx, ty, big, 0, rows);
        center(f, "TABLE TENNIS BY A BLACK HOLE", ty + 7 * big + 14, 1, 0xFFA8BCE0);
        int py = (int) (h * 0.80f);
        shade(f, py - 14, py + 44, 0.45f);
        if (((int) (clock * 1.6f)) % 2 == 0) center(f, "TOUCH TO PLAY", py, 2, 0xFFF2F6FA);
        center(f, "LEVEL " + level + "   WON " + won + "   LOST " + lost, py + 24, 1, 0xFFA8BCE0);
    }

    private void scoreboard(Px f) {
        Game g = game;
        String a = Integer.toString(g.score[0]), b = Integer.toString(g.score[1]);
        int m = 8;
        text(f, "YOU", m, 7, 1, 0xFFFF8E7A);
        text(f, "CPU", w - m - PixelFont.width("CPU", 1), 7, 1, 0xFFA898F0);
        text(f, a, m, 18, 3, 0xFFF2F6FA);
        text(f, b, w - m - PixelFont.width(b, 3), 18, 3, 0xFFF2F6FA);
        String lv = "LEVEL " + level;
        text(f, lv, (w - PixelFont.width(lv, 1)) / 2, 7, 1, 0xFF8FA4CC);
        // who serves: a small ball by that side's score
        float sx = g.server == 0 ? m + PixelFont.width(a, 3) + 7 : w - m - PixelFont.width(b, 3) - 7;
        f.discAA(sx, 28, 2.6f, 0xFFF6F3EC, 1f);
        if (g.msgT > 0f && g.state != Game.OVER) {
            int col = g.msgSide == 0 ? 0xFF9BE68A : g.msgSide == 1 ? 0xFFF28B82 : 0xFFF2F6FA;
            if (!g.human) col = 0xFFF2F6FA;
            float k = Math.min(1f, g.msgT * 4f);
            int y = (int) (h * 0.235f);
            shade(f, y - 10, y + 26, 1f - 0.45f * k);
            center(f, g.msg, y, 2, Col.mix(0xFF000000, col, k));
        }
    }

    private void hints(Px f) {
        Game g = game;
        int y = h - 34;
        if (g.state == Game.SERVE && g.server == 0 && g.held) {
            if (won + lost == 0) {
                center(f, "SLIDE TO MOVE THE PADDLE", y, 1, 0xFFF2F6FA);
                center(f, "LIFT YOUR FINGER TO SERVE", y + 12, 1, 0xFFF2F6FA);
            } else if (!touching) {
                center(f, "TOUCH, THEN LIFT TO SERVE", y + 6, 1, 0xFFF2F6FA);
            }
        } else if (g.score[0] + g.score[1] == 0 && won + lost == 0 && g.state == Game.RALLY) {
            center(f, "PUT THE RING UNDER THE BALL", y, 1, 0xFFF2F6FA);
            center(f, "SWIPE FORWARD TO HIT HARD", y + 12, 1, 0xFFF2F6FA);
        } else if (won + lost == 0 && g.score[0] + g.score[1] < 3 && g.state == Game.POINT) {
            center(f, "THE HOLE PULLS THE BALL", y + 6, 1, 0xFFFFC890);
        }
    }

    private void result(Px f) {
        int y0 = (int) (h * 0.24f), y1 = (int) (h * 0.52f);
        shade(f, y0, y1, 0.3f);
        int ty = (int) (h * 0.29f);
        center(f, youWon ? "YOU WIN" : "YOU LOSE", ty, 4, youWon ? 0xFF9BE68A : 0xFFF28B82);
        center(f, game.score[0] + " : " + game.score[1], ty + 42, 3, 0xFFF2F6FA);
        center(f, "NEXT LEVEL " + level, ty + 74, 1, 0xFFFFC857);
        if (resultTime > 0.8f && ((int) (clock * 1.6f)) % 2 == 0) center(f, "TOUCH TO CONTINUE", (int) (h * 0.62f), 1, 0xFFF2F6FA);
    }
}
