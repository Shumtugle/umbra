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
    static final String VERSION = "0.10";   // shown on screen; keep in step with versionName
    static final int TARGET_W = 270;       // about this many game pixels across

    static final int DOWN = 0, MOVE = 1, UP = 2;
    static final int MENU = 0, PLAY = 1, RESULT = 2, CABINET = 3, DEBUG = 4;

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
    final Realm[] realms = {new Hole(), new Station(), new Nucleus(), new Lyceum(), new River(), new Chamber(), new Slit(), new Darkroom()};
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
    private boolean resume;
    private boolean pinned;                // a realm picked by hand stays put, for checking it                // a match was left unfinished; the next one picks it up
    private int savedPoints = -1;
    private long seed = 1;
    private float downX, downY, lastX, lastY;

    // the debug screen: any realm, any version, any level, and nothing it plays is counted
    boolean debug;
    int dbRealm, dbSeed, dbLevel = 1;
    private float dbScroll, holdStart;
    private boolean holdUsed;

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
        if (realms.length < 2 || pinned) return;
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

    /** A version picked by hand on the stand; -1 leaves it to the bag. */
    int seedPinned = -1;

    /**
     * The version of a realm for its next match, from a shuffled bag of its own
     * that is kept between sessions, so that each version comes round once before
     * any comes again. The title screen peeks at it; the match takes it.
     */
    private int nextSeed(Realm r, boolean take) {
        int n = r.seeds();
        if (seedPinned >= 0) return seedPinned % n;
        String key = "seeds." + r.key();
        int[] bag = null;
        int next = 0;
        String[] p = host.prefText(key).split(",");
        if (p.length == n + 1) {
            try {
                next = Integer.parseInt(p[0]);
                bag = new int[n];
                for (int i = 0; i < n; i++) bag[i] = Integer.parseInt(p[i + 1]);
            } catch (NumberFormatException e) {
                bag = null;
            }
        }
        if (bag == null || next < 0 || next >= n) {
            bag = new int[n];
            for (int i = 0; i < n; i++) bag[i] = i;
            for (int i = n - 1; i > 0; i--) {
                int j = shuffle.nextInt(i + 1), t = bag[i];
                bag[i] = bag[j];
                bag[j] = t;
            }
            next = 0;
        }
        int s = ((bag[next] % n) + n) % n;
        if (take) next++;
        StringBuilder out = new StringBuilder().append(next);
        for (int v : bag) out.append(',').append(v);
        host.setPrefText(key, out.toString());
        return s;
    }

    private void show(int i) {
        realmIndex = (i + realms.length) % realms.length;
        scene = sceneOf(realmIndex);
    }

    void resize(int width, int height) {
        w = width;
        h = height;
        lift = Cam.LIFT * w;
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
        pinned = true;
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

    /** The level matches are played at: the real one, or the one set on the debug screen. */
    private int playLevel() {
        return debug ? dbLevel : level;
    }

    private void attract() {
        realm().setLevel(playLevel());
        if (realm().seeds() > 0) realm().seed(nextSeed(realm(), false));
        game = new Game(this, realm(), seed++);
        game.startMatch(false, 0.62f);
        screen = MENU;
    }

    /** The opponent's skill at a level: gentle at first, never perfect. */
    static float skill(int level) {
        return Math.min(0.95f, 0.10f + 0.08f * (level - 1));
    }

    private void begin() {
        realm().setLevel(playLevel());
        Realm r = realm();
        if (r.seeds() > 0) r.seed(resume ? host.pref("m.seed", r.seed()) : nextSeed(r, true));
        game = new Game(this, realm(), seed++ * 7919L);
        screen = PLAY;
        game.startMatch(true, skill(playLevel()));
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
        host.setPref("m.seed", g.realm.seed());
    }

    private void clearMatch() {
        host.setPref("m.realm", -1);
    }

    void back() {
        if (storyOpen) return;
        if (screen == MENU) host.quit();
        else if (screen == CABINET || screen == DEBUG) screen = MENU;
        else {
            // leaving a match half played: it waits on the title screen
            if (screen == PLAY && game.winner < 0 && !debug) {
                saveMatch();
                resume = true;
            }
            attract();
        }
    }

    // the story button: a ring with a question mark in the top right corner, large enough for a thumb
    static final float BTN_R = 15f, BTN_X = 21f, BTN_Y = 21f;

    private boolean onStoryButton(float x, float y) {
        float dx = x - (w - BTN_X), dy = y - BTN_Y;
        return dx * dx + dy * dy < (BTN_R + 10f) * (BTN_R + 10f);
    }

    private void openStory() {
        storyOpen = true;
        touching = false;
        host.cue(Game.C_TICK, 0.5f, 1.2f);
        Realm r = realm();
        StringBuilder keys = new StringBuilder();
        for (String k : r.stories()) keys.append(keys.length() > 0 ? " " : "").append(k);
        host.story(keys.toString(), r.name(), r.note());
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
            } else if (type == UP && Math.abs(y - downY) < 6f && y < 56f) {
                screen = MENU;
            }
            return;
        }
        if (screen == DEBUG) {
            debugTouch(type, x, y);
            return;
        }
        if (screen == MENU) {
            if (type == DOWN) {
                downX = lastX = x;
                downY = lastY = y;
                touching = true;
                holdStart = clock;
                holdUsed = false;
            } else if (type == MOVE) {
                lastX = x;
                lastY = y;
            } else if (type == UP) {
                touching = false;
                if (holdUsed) {
                    holdUsed = false;
                    return;
                }
                if (Math.abs(x - downX) < 0.1f * w && Math.abs(y - downY) < 0.1f * w) {
                    if (y > h - 62) {
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
        // a long hold on the title opens the debug screen
        if (screen == MENU && touching && !holdUsed && clock - holdStart > 1.2f && downY < titleBottom()
                && Math.abs(lastX - downX) < 0.05f * w && Math.abs(lastY - downY) < 0.05f * w) {
            holdUsed = true;
            openDebug();
        }
        if (screen == DEBUG) return;
        if (screen != CABINET) {
            game.update(dt);
            scene.update(game, dt);
        }
        float near = game.ball.gone || game.state == Game.SERVE ? 0f : game.realm.closeness(game.ball);
        host.hum(game.realm.hum(), screen == PLAY ? 1f : 0.35f, near * near);
        if (screen == PLAY && game.points != savedPoints && game.winner < 0 && !debug) {
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
            if (debug) {
                // a debug match counts for nothing
            } else if (youWon) {
                won++;
                level++;
                newItem = cabinet.add(realm().key(), game.score[0], game.score[1], game.worstBehind);
                host.setPrefText("cabinet", cabinet.save());
            } else {
                lost++;
                level = Math.max(1, level - 1);
            }
            if (!debug) clearMatch();
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
        if (screen == DEBUG) {
            debugScreen(f);
            return;
        }
        scene.render(f, game, game.clock);
        if (screen == MENU) title(f);
        else scoreboard(f);
        if (screen == PLAY) hints(f);
        if (screen == RESULT) result(f);
        if (screen == MENU || screen == PLAY) storyButton(f);
        if (screen == MENU) {
            if (debug) text(f, "DEBUG  LEVEL " + dbLevel, 4, h - 11, 1, 0xFFFF6A5A);
            String v = "V" + VERSION;
            PixelFont.draw(f, v, w - PixelFont.width(v, 1) - 4, h - 11, 1, 0xFF56607C);
        }
    }

    private void text(Px f, String s, int x, int y, int scale, int color) {
        PixelFont.draw(f, s, x + scale, y + scale, scale, 0xFF02030A);
        PixelFont.draw(f, s, x, y, scale, color);
    }

    private void center(Px f, String s, int y, int scale, int color) {
        text(f, s, (w - PixelFont.width(s, scale)) / 2, y, scale, color);
    }

    /**
     * Centred text broken into lines no wider than the screen allows, at the given
     * scale; returns the height it took.
     */
    private int block(Px f, String s, int y, int scale, int color) {
        int max = w - 16, lineH = 9 * scale;
        String[] words = s.split(" ");
        StringBuilder line = new StringBuilder();
        int lines = 0;
        for (String word : words) {
            if (word.isEmpty()) continue;
            String next = line.length() == 0 ? word : line + " " + word;
            if (PixelFont.width(next, scale) > max && line.length() > 0) {
                center(f, line.toString(), y + lines * lineH, scale, color);
                lines++;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) {
            center(f, line.toString(), y + lines * lineH, scale, color);
            lines++;
        }
        return lines * lineH;
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
        float cx = w - BTN_X, cy = BTN_Y;
        f.discAA(cx, cy, BTN_R, 0xFF0A0C14, 0.7f);
        f.ringAA(cx, cy, BTN_R, BTN_R, 2f, 0xFFFFC857, 0.95f);
        PixelFont.draw(f, "?", (int) cx - 4, (int) cy - 6, 2, 0xFFFFC857);
    }

    /** The lowest row of the big title, where a long hold opens the debug screen. */
    private float titleBottom() {
        int big = Math.max(4, (int) (w * 0.84f / 30f));
        return h * 0.11f + 7 * big + 30;
    }

    private void title(Px f) {
        for (int i = 0; i < f.a.length; i++) f.a[i] = Col.mul(f.a[i], 0.62f);
        int big = Math.max(4, (int) (w * 0.84f / 30f));
        int ty = (int) (h * 0.11f);
        shade(f, ty - 24, ty + 7 * big + 56, 0.45f);
        int tw = PixelFont.width("UMBRA", big);
        int tx = (w - tw) / 2;
        // the title, lit from below by the disk
        PixelFont.draw(f, "UMBRA", tx + big / 2, ty + big / 2, big, 0xFF000000);
        int[] rows = new int[7];
        for (int r = 0; r < 7; r++) rows[r] = Col.mix(0xFFF6F8FF, 0xFFFFB45A, r / 6f);
        PixelFont.draw(f, "UMBRA", tx, ty, big, 0, rows);
        block(f, "TABLE TENNIS WHERE PHYSICS BENDS", ty + 7 * big + 12, 2, 0xFFA8BCE0);
        // the realm this match is played in
        int ry = (int) (h * 0.56f);
        shade(f, ry - 16, ry + 62, 0.4f);
        Realm r = realm();
        center(f, r.name(), ry, PixelFont.width(r.name(), 3) <= w - 12 ? 3 : 2, 0xFFFFC857);
        block(f, r.note(), ry + 30, 2, 0xFFA8BCE0);
        int py = (int) (h * 0.74f);
        shade(f, py - 14, py + 44, 0.45f);
        String go = resume ? "CONTINUE " + host.pref("m.s0", 0) + ":" + host.pref("m.s1", 0) : "TOUCH TO PLAY";
        if (((int) (clock * 1.6f)) % 2 == 0) center(f, go, py, 3, 0xFFF2F6FA);
        center(f, "ROUND " + round() + "  WON " + won + "  LOST " + lost, py + 30, 2, 0xFFA8BCE0);
        shelf(f);
    }

    /** The bottom of the title screen: the last exhibits won, on a shelf. Touch it to open the cabinet. */
    private void shelf(Px f) {
        int y = h - 20;
        shade(f, h - 66, h, 0.4f);
        f.rect(6, y + 2, w - 6, y + 6, 0xFF4A2E18);
        f.rect(6, y + 2, w - 6, y + 3, 0xFF8A6040);
        int n = cabinet.items.size();
        if (n == 0) {
            block(f, "WIN A MATCH TO START THE CABINET", y - 34, 2, 0xFF6A7898);
            return;
        }
        int show = Math.min(n, 6);
        float step = (w - 24) / 6f, x0 = 12 + step / 2;
        for (int i = 0; i < show; i++) Cabinet.draw(f, cabinet.items.get(n - show + i), x0 + i * step, y + 2, 15f, clock);
    }

    // ---- the debug screen

    private static final int DB_HEAD = 62, DB_ROW = 24, DB_CELL = 26;

    private void openDebug() {
        host.cue(Game.C_TICK, 0.6f, 1.5f);
        touching = false;
        dbRealm = realmIndex;
        Realm r = realm();
        dbSeed = r.seeds() > 0 ? r.seed() : 0;
        if (!debug) dbLevel = level;
        dbScroll = 0f;
        screen = DEBUG;
    }

    private int dbSeedsTop() {
        return DB_HEAD + realms.length * DB_ROW + 10;
    }

    private int dbNameY() {
        int n = realms[dbRealm].seeds();
        return dbSeedsTop() + (n + 7) / 8 * DB_CELL + 6;
    }

    private int dbLevelY() {
        return realms[dbRealm].seeds() > 0 ? dbNameY() + 40 : dbSeedsTop();
    }

    private int dbPlayY() {
        return dbLevelY() + 46;
    }

    private int dbOffY() {
        return dbPlayY() + 52;
    }

    private void debugTouch(int type, float x, float y) {
        if (type == DOWN) {
            downX = x;
            downY = lastY = y;
            return;
        }
        if (type == MOVE) {
            float bottom = (debug ? dbOffY() + 44 : dbPlayY() + 52) + 20;
            dbScroll = Math.max(0f, Math.min(Math.max(0f, bottom - h), dbScroll - (y - lastY)));
            lastY = y;
            return;
        }
        if (Math.abs(y - downY) > 8f || Math.abs(x - downX) > 8f) return;
        if (y < DB_HEAD - 6) {
            screen = MENU;
            return;
        }
        float sy = y + dbScroll;
        host.cue(Game.C_TICK, 0.5f, 1.2f);
        if (sy >= DB_HEAD && sy < DB_HEAD + realms.length * DB_ROW) {
            dbRealm = (int) ((sy - DB_HEAD) / DB_ROW);
            int n = realms[dbRealm].seeds();
            dbSeed = n > 0 ? Math.min(dbSeed, n - 1) : 0;
            return;
        }
        int n = realms[dbRealm].seeds();
        float cw = (w - 8) / 8f;
        if (n > 0 && sy >= dbSeedsTop() && sy < dbSeedsTop() + (n + 7) / 8 * DB_CELL) {
            int k = (int) ((sy - dbSeedsTop()) / DB_CELL) * 8 + (int) Math.min(7, Math.max(0, (x - 4) / cw));
            if (k < n) dbSeed = k;
            return;
        }
        if (sy >= dbLevelY() && sy < dbLevelY() + 34) {
            dbLevel = x < w / 2f ? Math.max(1, dbLevel - 1) : Math.min(30, dbLevel + 1);
            return;
        }
        if (sy >= dbPlayY() && sy < dbPlayY() + 40) {
            debug = true;
            resume = false;
            show(dbRealm);
            pinned = true;
            seedPinned = n > 0 ? dbSeed : -1;
            attract();
            return;
        }
        if (debug && sy >= dbOffY() && sy < dbOffY() + 36) {
            debug = false;
            pinned = false;
            seedPinned = -1;
            attract();
        }
    }

    /** Every realm in a list, its versions in a grid, the level, and a button to play them. */
    private void debugScreen(Px f) {
        for (int i = 0; i < f.a.length; i++) f.a[i] = 0xFF0C0E14;
        int s = (int) dbScroll;
        for (int i = 0; i < realms.length; i++) {
            int y = DB_HEAD + i * DB_ROW - s;
            boolean on = i == dbRealm;
            if (on) f.rect(6, y, w - 6, y + DB_ROW - 3, 0xFF2A3350);
            String name = realms[i].name() + (realms[i].seeds() > 0 ? "  " + realms[i].seeds() : "");
            int sc = PixelFont.width(name, 2) <= w - 24 ? 2 : 1;
            text(f, name, 12, y + (DB_ROW - 3 - 7 * sc) / 2, sc, on ? 0xFFFFC857 : 0xFFA8BCE0);
        }
        Realm r = realms[dbRealm];
        int n = r.seeds();
        if (n > 0) {
            float cw = (w - 8) / 8f;
            for (int k = 0; k < n; k++) {
                int x0 = (int) (4 + (k % 8) * cw), y0 = dbSeedsTop() + (k / 8) * DB_CELL - s;
                boolean on = k == dbSeed;
                f.rect(x0 + 1, y0 + 1, (int) (x0 + cw) - 1, y0 + DB_CELL - 2, on ? 0xFFFFC857 : 0xFF1C2232);
                String num = Integer.toString(k + 1);
                text(f, num, x0 + (int) ((cw - PixelFont.width(num, 2)) / 2), y0 + 5, 2, on ? 0xFF1A1008 : 0xFFA8BCE0);
            }
            block(f, r.seedName(dbSeed), dbNameY() - s, 2, 0xFFFFC857);
        }
        int ly = dbLevelY() - s;
        f.rect(6, ly, w / 2 - 3, ly + 30, 0xFF1C2232);
        f.rect(w / 2 + 3, ly, w - 6, ly + 30, 0xFF1C2232);
        text(f, "-", 18, ly + 5, 3, 0xFFA8BCE0);
        text(f, "+", w - 18 - PixelFont.width("+", 3), ly + 5, 3, 0xFFA8BCE0);
        center(f, "LEVEL " + dbLevel, ly + 8, 2, 0xFFF2F6FA);
        int py = dbPlayY() - s;
        f.rect(6, py, w - 6, py + 38, 0xFF2E6A3E);
        center(f, "PLAY THIS", py + 9, 3, 0xFFF2F6FA);
        if (debug) {
            int oy = dbOffY() - s;
            f.rect(6, oy, w - 6, oy + 32, 0xFF5A2A2A);
            center(f, "DEBUG OFF", oy + 9, 2, 0xFFF2F6FA);
        }
        // the header stays put; touch it to close
        f.rect(0, 0, w, DB_HEAD - 6, 0xFF06070A);
        center(f, "DEBUG", 8, 3, 0xFFFF6A5A);
        center(f, "NOTHING PLAYED HERE COUNTS", 36, 1, 0xFF8FA4CC);
        PixelFont.draw(f, "X", w - 20, 10, 2, 0xFFFFC857);
    }

    /** All exhibits, on shelves in a dark wooden cabinet; drag to scroll, touch the top to close. */
    private void cabinetScreen(Px f) {
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                float g = (float) Math.sin(x * 0.21f + Math.sin(y * 0.013f) * 3f) * 0.5f + 0.5f;
                f.a[y * w + x] = Col.mix(0xFF1A110A, 0xFF24180E, g * 0.6f);
            }
        int cols = 2, top = 62, rowH = 128;
        float cell = w / (float) cols;
        int n = cabinet.items.size();
        int rows = Math.max(3, (n + cols - 1) / cols);
        float maxScroll = Math.max(0f, top + rows * rowH - h + 20);
        if (cabinetScroll > maxScroll) cabinetScroll = maxScroll;
        for (int r = 0; r < rows; r++) {
            int sy = (int) (top + (r + 1) * rowH - cabinetScroll) - 26;
            if (sy < -rowH || sy > h + rowH) continue;
            f.rect(4, sy, w - 4, sy + 6, 0xFF5A3A1E);
            f.rect(4, sy, w - 4, sy + 1, 0xFF9A7048);
            f.rect(4, sy + 6, w - 4, sy + 9, 0xFF0A0604);
            for (int c = 0; c < cols; c++) {
                int i = r * cols + c;
                if (i >= n) continue;
                Cabinet.Item it = cabinet.items.get(i);
                float cx = cell * (c + 0.5f);
                Cabinet.draw(f, it, cx, sy, 40f, clock);
                String s = it.mine + ":" + it.theirs;
                int pw = PixelFont.width(s, 2) + 10;
                f.rect((int) cx - pw / 2, sy + 11, (int) cx + pw / 2, sy + 29, it.gold ? 0xFFD8A830 : 0xFF8A6A3A);
                PixelFont.draw(f, s, (int) cx - pw / 2 + 5, sy + 13, 2, 0xFF1A1008);
            }
        }
        // the cabinet's top rail and its name; touch here to close
        f.rect(0, 0, w, top - 6, 0xFF0E0906);
        f.rect(0, top - 7, w, top - 4, 0xFF8A6040);
        center(f, "THE CABINET", 8, 3, 0xFFFFC857);
        String sub = n == 0 ? "NOTHING YET" : n + (n == 1 ? " EXHIBIT" : " EXHIBITS");
        center(f, sub, 34, 2, 0xFFA8BCE0);
        PixelFont.draw(f, "X", w - 20, 10, 2, 0xFFFFC857);
    }

    private void scoreboard(Px f) {
        Game g = game;
        String a = Integer.toString(g.score[0]), b = Integer.toString(g.score[1]);
        // the band at the bottom, under the finger: nothing there is touched, all of it is read between points
        int y0 = h - (int) (Cam.BAND * w);
        shade(f, y0 - 8, h, 0.5f);
        int m = 8, right = w - m;
        text(f, "YOU", m, y0 + 3, 2, 0xFFFF8E7A);
        text(f, "CPU", right - PixelFont.width("CPU", 2), y0 + 3, 2, 0xFFA898F0);
        text(f, a, m, y0 + 20, 4, 0xFFF2F6FA);
        text(f, b, right - PixelFont.width(b, 4), y0 + 20, 4, 0xFFF2F6FA);
        // the realm's name, and the round, between the scores
        String name = g.realm.name();
        int left = m + 44, room = right - 44 - left;
        int ns = PixelFont.width(name, 2) <= room ? 2 : 1;
        text(f, name, left + (room - PixelFont.width(name, ns)) / 2, y0 + 8, ns, 0xFFFFC857);
        String rd = debug ? "DEBUG  LEVEL " + dbLevel : "ROUND " + round();
        text(f, rd, left + (room - PixelFont.width(rd, 1)) / 2, y0 + 28, 1, debug ? 0xFFFF6A5A : 0xFF8FA4CC);
        // shots still in hand, where the realm has them: a small pistol for each, just over the band
        for (int side = 0; side < 2; side++)
            for (int k = 0; k < g.shotsLeft[side]; k++) pistol(f, side == 0 ? m + k * 26 : right - 22 - k * 26, y0 - 16, side == 1);
        // who serves: a ball by that side's score
        float sx = g.server == 0 ? m + PixelFont.width(a, 4) + 9 : right - PixelFont.width(b, 4) - 9;
        f.discAA(sx, y0 + 34, 3.6f, 0xFFF6F3EC, 1f);
        if (g.msgT > 0f && g.state != Game.OVER) {
            int col = g.msgSide == 0 ? 0xFF9BE68A : g.msgSide == 1 ? 0xFFF28B82 : 0xFFF2F6FA;
            if (!g.human) col = 0xFFF2F6FA;
            float k = Math.min(1f, g.msgT * 4f);
            int y = (int) (h * 0.235f);
            shade(f, y - 12, y + 36, 1f - 0.45f * k);
            int sc = PixelFont.width(g.msg, 3) <= w - 16 ? 3 : 2;
            center(f, g.msg, y, sc, Col.mix(0xFF000000, col, k));
        }
    }

    private static final String[] PISTOL = {
            "##########.",
            "#########..",
            ".####......",
            "####.......",
            "###........"};

    /** A small pistol, two game pixels to each of its dots. */
    private static void pistol(Px f, int x, int y, boolean flip) {
        for (int r = 0; r < PISTOL.length; r++)
            for (int c = 0; c < PISTOL[r].length(); c++) {
                if (PISTOL[r].charAt(c) != '#') continue;
                int px = x + 2 * (flip ? PISTOL[r].length() - 1 - c : c), py = y + 2 * r;
                f.rect(px + 1, py + 1, px + 3, py + 3, 0xFF02030A);
                f.rect(px, py, px + 2, py + 2, r == 0 ? 0xFFD8DEE6 : 0xFFB89870);
            }
    }

    private void hints(Px f) {
        Game g = game;
        int y = h - (int) (Cam.BAND * w) - 34;
        if (g.state == Game.SERVE && g.server == 0 && g.held) {
            if (won + lost == 0) {
                block(f, "SLIDE TO MOVE THE PADDLE. LIFT TO SERVE", y - 8, 2, 0xFFF2F6FA);
            } else if (!touching) {
                block(f, "TOUCH, THEN LIFT TO SERVE", y, 2, 0xFFF2F6FA);
            }
        } else if (g.score[0] + g.score[1] == 0 && won + lost == 0 && g.state == Game.RALLY) {
            block(f, "RING UNDER THE BALL. SWIPE FORWARD", y - 8, 2, 0xFFF2F6FA);
        } else if (g.score[0] + g.score[1] < 2 && g.state == Game.POINT) {
            // the law of the realm, named at the start of every match
            block(f, game.realm.hint(), y - 8, 2, 0xFFFFC890);
        }
    }

    private void result(Px f) {
        int y0 = (int) (h * 0.18f), y1 = (int) (h * 0.66f);
        shade(f, y0, y1, 0.3f);
        int ty = (int) (h * 0.22f);
        center(f, youWon ? "YOU WIN" : "YOU LOSE", ty, 5, youWon ? 0xFF9BE68A : 0xFFF28B82);
        center(f, game.score[0] + " : " + game.score[1], ty + 48, 4, 0xFFF2F6FA);
        if (newItem != null) {
            // the new exhibit, rising into view
            float k = Math.min(1f, resultTime / 0.6f);
            float ey = ty + 168 + (1f - k) * 30f;
            Cabinet.draw(f, newItem, w / 2f, ey, 40f * (0.6f + 0.4f * k), clock);
            String what = newItem.gold && newItem.comeback ? "ON GOLD, WITH A RIBBON" : newItem.gold ? "ON GOLD" : newItem.comeback ? "A RIBBON FOR THE COMEBACK" : "NEW IN THE CABINET";
            block(f, what, (int) ey + 16, 2, newItem.gold ? 0xFFFFD040 : 0xFFFFC857);
        }
        if (resultTime > 0.8f && ((int) (clock * 1.6f)) % 2 == 0) center(f, "TOUCH TO GO ON", (int) (h * 0.74f), 2, 0xFFF2F6FA);
    }
}
