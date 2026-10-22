package com.shumtugle.umbra;

/**
 * The whole program above the court: the title screen (where the machine plays
 * itself), the match, the result, the cabinet of exhibits won, and the interface
 * drawn over the picture. Plain Java with no platform classes, so the same code
 * runs on the phone and on the desktop stand.
 *
 * The shell calls, always from one thread: touch() for each queued touch,
 * update(dt), then draw(frame). Coordinates are game pixels; the frame is w by h,
 * and each game pixel is a whole number of screen pixels.
 */
final class World implements Game.Sink {
    static final String VERSION = "0.6";   // shown on screen; keep in step with versionName
    static final int TARGET_W = 270;       // about this many game pixels across

    static final int DOWN = 0, MOVE = 1, UP = 2;
    static final int MENU = 0, PLAY = 1, RESULT = 2, CABINET = 3;

    /** What the game may ask of the phone. The stand answers with nothing. */
    interface Host {
        void buzz(int ms);

        void quit();

        void cue(int id, float vol, float pitch);

        /** The realm's hum: its kind, its level 0..1, and how near the ball is to the realm's thing, 0..1. */
        void hum(int kind, float level, float near);

        int pref(String key, int def);

        void setPref(String key, int value);

        String prefText(String key);

        void setPrefText(String key, String value);

        /** Opens the story of a realm over the game; the game waits until storyClosed(). */
        void story(String key, String title, String note);
    }

    /** How many screen pixels make one game pixel on a screen this wide. */
    static int pixelSize(int screenW) {
        return Math.max(1, Math.round(screenW / (float) TARGET_W));
    }

    private final Host host;
    int w = TARGET_W, h = 1;
    float lift;                            // the paddle rides this far above the finger
    int screen = MENU;
    int level, won, lost;                  // level is the opponent's strength; it follows the results unseen
    final Realm[] realms = {new Hole(), new Station(), new Nucleus(), new Lyceum(), new River()};
    int realmIndex;
    java.util.Random shuffle = new java.util.Random(System.nanoTime());
    private int[] bag = new int[0];
    private int bagNext;
    private Scene[] scenes = new Scene[realms.length];
    Scene scene;
    Game game;
    boolean touching, storyOpen;
    float clock, resultTime;
    boolean youWon;
    final Cabinet cabinet = new Cabinet();
    Cabinet.Item newItem;                  // the exhibit just won, shown on the result screen
    private float cabinetScroll;
    private boolean resume;                // a match was left unfinished; the next one picks it up
    private int savedPoints = -1;
    private long seed = 1;
    private float downX, downY, lastY;

    World(Host host) {
        this.host = host;
        level = Math.max(1, host.pref("level", 1));
        won = host.pref("won", 0);
        lost = host.pref("lost", 0);
        cabinet.load(host.prefText("cabinet"));
        int left = host.pref("m.realm", -1);
        if (left >= 0 && left < realms.length) {
            realmIndex = left;
            resume = true;
        } else {
            realmIndex = 0;                // every visit begins at the hole
        }
    }

    Realm realm() {
        return realms[realmIndex];
    }

    /** The number of the match about to be played, counting all played before it. */
    int round() {
        return won + lost + 1;
    }

    /**
     * After a match, the next realm: drawn from a shuffled bag of all the others,
     * so every realm comes round before any comes twice, never the same one twice
     * in a row, and the order is a surprise each time.
     */
    private void nextRealm() {
        if (realms.length < 2) return;
        if (bagNext >= bag.length) {
            bag = new int[realms.length - 1];
            for (int i = 0; i < bag.length; i++) bag[i] = i + 1;
            for (int i = bag.length - 1; i > 0; i--) {
                int j = shuffle.nextInt(i + 1), t = bag[i];
                bag[i] = bag[j];
                bag[j] = t;
            }
            if (bag.length > 1 && bag[0] == realmIndex) {
                int t = bag[0];
                bag[0] = bag[1];
                bag[1] = t;
            }
            bagNext = 0;
        }
        show(bag[bagNext++]);
    }

    private void show(int i) {
        realmIndex = (i + realms.length) % realms.length;
        scene = sceneOf(realmIndex);
    }

    void resize(int width, int height) {
        w = width;
        h = height;
        lift = 0.13f * w;
        scenes = new Scene[realms.length];
        scene = sceneOf(realmIndex);
        if (game == null) attract();
    }

    /** The scene of a realm, painted the first time it is needed. */
    private Scene sceneOf(int i) {
        if (scenes[i] == null) scenes[i] = new Scene(w, h, realms[i]);
        return scenes[i];
    }

    /** Goes to a given realm and its title screen; for checking a realm by hand. */
    void pick(int i) {
        show(i);
        resume = false;
        attract();
    }

    // ---- sound and haptics, as the game asks for them

    @Override
    public void cue(int id, float vol, float pitch) {
        if (screen == PLAY || screen == RESULT) host.cue(id, vol, pitch);
    }

    @Override
    public void buzz(int ms) {
        host.buzz(ms);
    }

    // ---- flow

    private void attract() {
        realm().setLevel(level);
        game = new Game(this, realm(), seed++);
        game.startMatch(false, 0.62f);
        screen = MENU;
    }

    /** The opponent's skill at a level: gentle at first, never perfect. */
    static float skill(int level) {
        return Math.min(0.95f, 0.10f + 0.08f * (level - 1));
    }

    private void begin() {
        realm().setLevel(level);
        game = new Game(this, realm(), seed++ * 7919L);
        screen = PLAY;
        game.startMatch(true, skill(level));
        if (resume) {
            game.resume(host.pref("m.s0", 0), host.pref("m.s1", 0), host.pref("m.first", 0),
                    host.pref("m.shot0", game.shotsLeft[0]), host.pref("m.shot1", game.shotsLeft[1]), host.pref("m.behind", 0));
            resume = false;
        }
        savedPoints = -1;
        host.cue(Game.C_START, 0.8f, 1f);
    }

    /** Writes the match so far, so that closing the game never loses it. */
    private void saveMatch() {
        Game g = game;
        host.setPref("m.realm", realmIndex);
        host.setPref("m.s0", g.score[0]);
        host.setPref("m.s1", g.score[1]);
        host.setPref("m.first", g.firstServer);
        host.setPref("m.shot0", g.shotsLeft[0]);
        host.setPref("m.shot1", g.shotsLeft[1]);
        host.setPref("m.behind", g.worstBehind);
    }

    private void clearMatch() {
        host.setPref("m.realm", -1);
    }

    void back() {
        if (storyOpen) return;
        if (screen == MENU) host.quit();
        else if (screen == CABINET) screen = MENU;
        else {
            // leaving a match half played: it waits on the title screen
            if (screen == PLAY && game.winner < 0) {
                saveMatch();
                resume = true;
            }
            attract();
        }
    }

    private boolean onStoryButton(float x, float y) {
        float dx = x - (w - 14), dy = y - 14;
        return dx * dx + dy * dy < 16f * 16f;
    }

    private void openStory() {
        storyOpen = true;
        touching = false;
        host.cue(Game.C_TICK, 0.5f, 1.2f);
        Realm r = realm();
        host.story(r.key(), r.name(), r.note());
    }

    /** The story was closed: the game goes on where it stopped. */
    void storyClosed() {
        storyOpen = false;
    }

    void touch(int type, float x, float y) {
        if (storyOpen) return;
        if (type == DOWN && (screen == MENU || screen == PLAY) && onStoryButton(x, y)) {
            openStory();
            return;
        }
        if (screen == CABINET) {
            if (type == DOWN) {
                downY = lastY = y;
            } else if (type == MOVE) {
                cabinetScroll = Math.max(0f, cabinetScroll - (y - lastY));
                lastY = y;
            } else if (type == UP && Math.abs(y - downY) < 6f && y < 40f) {
                screen = MENU;
            }
            return;
        }
        if (screen == MENU) {
            if (type == DOWN) {
                downX = x;
                downY = y;
                touching = true;
            } else if (type == UP) {
                touching = false;
                if (Math.abs(x - downX) < 0.1f * w && Math.abs(y - downY) < 0.1f * w) {
                    if (y > h - 46) {
                        host.cue(Game.C_TICK, 0.5f, 1f);
                        cabinetScroll = 0f;
                        screen = CABINET;
                    } else begin();
                }
            }
            return;
        }
        if (type == DOWN) {
            touching = true;
            if (screen == RESULT && resultTime > 0.8f) {
                host.cue(Game.C_TICK, 0.6f, 1f);
                nextRealm();
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
        if (storyOpen) {
            host.hum(game.realm.hum(), 0.25f, 0f);
            return;
        }
        if (screen != CABINET) {
            game.update(dt);
            scene.update(game, dt);
        }
        float near = game.ball.gone || game.state == Game.SERVE ? 0f : game.realm.closeness(game.ball);
        host.hum(game.realm.hum(), screen == PLAY ? 1f : 0.35f, near * near);
        if (screen == PLAY && game.points != savedPoints && game.winner < 0) {
            savedPoints = game.points;
            saveMatch();
        }
        if (screen == MENU && game.state == Game.OVER) {
            game.startMatch(false, 0.62f);
        } else if (screen == PLAY && game.state == Game.OVER) {
            screen = RESULT;
            resultTime = 0f;
            youWon = game.winner == 0;
            newItem = null;
            if (youWon) {
                won++;
                level++;
                newItem = cabinet.add(realm().key(), game.score[0], game.score[1], game.worstBehind);
                host.setPrefText("cabinet", cabinet.save());
            } else {
                lost++;
                level = Math.max(1, level - 1);
            }
            clearMatch();
            host.setPref("level", level);
            host.setPref("won", won);
            host.setPref("lost", lost);
        } else if (screen == RESULT) {
            resultTime += dt;
        }
    }

    // ---- drawing

    void draw(Px f) {
        if (screen == CABINET) {
            cabinetScreen(f);
            return;
        }
        scene.render(f, game, game.clock);
        if (screen == MENU) title(f);
        else scoreboard(f);
        if (screen == PLAY) hints(f);
        if (screen == RESULT) result(f);
        if (screen == MENU || screen == PLAY) storyButton(f);
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

    /** A question mark in a ring, top right: the story of this realm. */
    private void storyButton(Px f) {
        float cx = w - 14, cy = 14;
        f.discAA(cx, cy, 9f, 0xFF0A0C14, 0.65f);
        f.ringAA(cx, cy, 9f, 9f, 1.3f, 0xFFFFC857, 0.9f);
        PixelFont.draw(f, "?", (int) cx - 2, (int) cy - 3, 1, 0xFFFFC857);
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
        center(f, "TABLE TENNIS WHERE PHYSICS BENDS", ty + 7 * big + 14, 1, 0xFFA8BCE0);
        // the realm this match is played in
        int ry = (int) (h * 0.6f);
        shade(f, ry - 16, ry + 40, 0.4f);
        Realm r = realm();
        center(f, r.name(), ry, 2, 0xFFFFC857);
        center(f, r.note(), ry + 22, 1, 0xFFA8BCE0);
        int py = (int) (h * 0.76f);
        shade(f, py - 14, py + 40, 0.45f);
        String go = resume ? "CONTINUE  " + host.pref("m.s0", 0) + " : " + host.pref("m.s1", 0) : "TOUCH TO PLAY";
        if (((int) (clock * 1.6f)) % 2 == 0) center(f, go, py, 2, 0xFFF2F6FA);
        center(f, "ROUND " + round() + "   WON " + won + "   LOST " + lost, py + 24, 1, 0xFFA8BCE0);
        shelf(f);
    }

    /** The bottom of the title screen: the last exhibits won, on a shelf. Touch it to open the cabinet. */
    private void shelf(Px f) {
        int y = h - 18;
        shade(f, h - 50, h, 0.4f);
        f.rect(6, y + 2, w - 6, y + 5, 0xFF4A2E18);
        f.rect(6, y + 2, w - 6, y + 3, 0xFF8A6040);
        int n = cabinet.items.size();
        if (n == 0) {
            center(f, "WIN A MATCH TO START THE CABINET", y - 14, 1, 0xFF6A7898);
            return;
        }
        int show = Math.min(n, 8);
        float step = (w - 40) / 8f, x0 = 20 + step / 2;
        for (int i = 0; i < show; i++) Cabinet.draw(f, cabinet.items.get(n - show + i), x0 + i * step, y + 2, 11f, clock);
        String label = "CABINET " + n;
        text(f, label, w - PixelFont.width(label, 1) - 8, y - 32, 1, 0xFF8FA4CC);
    }

    /** All exhibits, on shelves in a dark wooden cabinet; drag to scroll, touch the top to close. */
    private void cabinetScreen(Px f) {
        f.clear(0xFF120C08);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                float g = (float) Math.sin(x * 0.21f + Math.sin(y * 0.013f) * 3f) * 0.5f + 0.5f;
                f.a[y * w + x] = Col.mix(0xFF1A110A, 0xFF24180E, g * 0.6f);
            }
        int cols = 3, top = 44, rowH = 104;
        float cell = w / (float) cols;
        int n = cabinet.items.size();
        int rows = Math.max(3, (n + cols - 1) / cols);
        float maxScroll = Math.max(0f, top + rows * rowH - h + 20);
        if (cabinetScroll > maxScroll) cabinetScroll = maxScroll;
        for (int r = 0; r < rows; r++) {
            int sy = (int) (top + (r + 1) * rowH - cabinetScroll) - 18;
            if (sy < -rowH || sy > h + rowH) continue;
            f.rect(4, sy, w - 4, sy + 5, 0xFF5A3A1E);
            f.rect(4, sy, w - 4, sy + 1, 0xFF9A7048);
            f.rect(4, sy + 5, w - 4, sy + 8, 0xFF0A0604);
            for (int c = 0; c < cols; c++) {
                int i = r * cols + c;
                if (i >= n) continue;
                Cabinet.Item it = cabinet.items.get(i);
                float cx = cell * (c + 0.5f);
                Cabinet.draw(f, it, cx, sy, 27f, clock);
                String s = it.mine + ":" + it.theirs;
                int pw = PixelFont.width(s, 1) + 6;
                f.rect((int) cx - pw / 2, sy + 9, (int) cx + pw / 2, sy + 19, it.gold ? 0xFFD8A830 : 0xFF8A6A3A);
                PixelFont.draw(f, s, (int) cx - pw / 2 + 3, sy + 11, 1, 0xFF1A1008);
            }
        }
        // the glass doors' edge and the title
        f.rect(0, 0, w, top - 6, 0xFF0E0906);
        f.rect(0, top - 7, w, top - 5, 0xFF8A6040);
        center(f, "THE CABINET", 10, 2, 0xFFFFC857);
        String sub = n == 0 ? "NOTHING YET. WIN A MATCH." : n + (n == 1 ? " EXHIBIT" : " EXHIBITS") + "   TOUCH HERE TO CLOSE";
        center(f, sub, 28, 1, 0xFFA8BCE0);
    }

    private void scoreboard(Px f) {
        Game g = game;
        String a = Integer.toString(g.score[0]), b = Integer.toString(g.score[1]);
        int m = 8, right = w - m - 26;      // room for the story button at the far right
        text(f, "YOU", m, 7, 1, 0xFFFF8E7A);
        text(f, "CPU", right - PixelFont.width("CPU", 1), 7, 1, 0xFFA898F0);
        text(f, a, m, 18, 3, 0xFFF2F6FA);
        text(f, b, right - PixelFont.width(b, 3), 18, 3, 0xFFF2F6FA);
        String name = g.realm.name();
        text(f, name, (w - PixelFont.width(name, 1)) / 2 - 8, 7, 1, 0xFFFFC857);
        String rd = "ROUND " + round();
        text(f, rd, (w - PixelFont.width(rd, 1)) / 2 - 8, 17, 1, 0xFF8FA4CC);
        // shots still in hand, where the realm has them: a small pistol for each
        for (int side = 0; side < 2; side++)
            for (int k = 0; k < g.shotsLeft[side]; k++) pistol(f, side == 0 ? m + k * 14 : right - 11 - k * 14, 42, side == 1);
        // who serves: a small ball by that side's score
        float sx = g.server == 0 ? m + PixelFont.width(a, 3) + 7 : right - PixelFont.width(b, 3) - 7;
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

    private static final String[] PISTOL = {
            "##########.",
            "#########..",
            ".####......",
            "####.......",
            "###........"};

    private static void pistol(Px f, int x, int y, boolean flip) {
        for (int r = 0; r < PISTOL.length; r++)
            for (int c = 0; c < PISTOL[r].length(); c++) {
                if (PISTOL[r].charAt(c) != '#') continue;
                int px = flip ? x + PISTOL[r].length() - 1 - c : x + c;
                f.rect(px + 1, y + r + 1, px + 2, y + r + 2, 0xFF02030A);
                f.rect(px, y + r, px + 1, y + r + 1, r == 0 ? 0xFFD8DEE6 : 0xFFB89870);
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
        } else if (g.score[0] + g.score[1] < 2 && g.state == Game.POINT) {
            // the law of the realm, named at the start of every match
            center(f, game.realm.hint(), y + 6, 1, 0xFFFFC890);
        }
    }

    private void result(Px f) {
        int y0 = (int) (h * 0.2f), y1 = (int) (h * 0.62f);
        shade(f, y0, y1, 0.3f);
        int ty = (int) (h * 0.24f);
        center(f, youWon ? "YOU WIN" : "YOU LOSE", ty, 4, youWon ? 0xFF9BE68A : 0xFFF28B82);
        center(f, game.score[0] + " : " + game.score[1], ty + 40, 3, 0xFFF2F6FA);
        if (newItem != null) {
            // the new exhibit, rising into view
            float k = Math.min(1f, resultTime / 0.6f);
            float ey = ty + 132 + (1f - k) * 30f;
            Cabinet.draw(f, newItem, w / 2f, ey, 30f * (0.6f + 0.4f * k), clock);
            String what = newItem.gold && newItem.comeback ? "ON GOLD, WITH A RIBBON" : newItem.gold ? "ON GOLD" : newItem.comeback ? "A RIBBON FOR THE COMEBACK" : "NEW IN THE CABINET";
            center(f, what, (int) ey + 16, 1, newItem.gold ? 0xFFFFD040 : 0xFFFFC857);
        }
        if (resultTime > 0.8f && ((int) (clock * 1.6f)) % 2 == 0) center(f, "TOUCH TO CONTINUE", (int) (h * 0.7f), 1, 0xFFF2F6FA);
    }
}
