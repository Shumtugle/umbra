package com.shumtugle.umbra;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;

/**
 * The desktop stand: runs the same World as the phone, frame by frame, from a scene
 * script, and saves snapshots as PNG. No phone, no emulator, no platform classes.
 *
 * Scene commands, one per line (coordinates in game pixels, time in seconds):
 *   screen 1096 2560        phone screen in real pixels; sets the world's height
 *   wait 1.5                run the world for this long at 60 frames a second
 *   down x y | move x y | up x y
 *   drag x0 y0 x1 y1 0.3    a finger from one point to another over this time
 *   tap x y
 *   back
 *   shot name               save the current frame as name.png, scaled to the screen width
 *   bot on|off              a player made of touches takes the finger
 *   bot noise 0.04 | bot speed 4.5 | bot swipe 2.4 | bot react 0.3
 *   level N                 set the opponent level before a match
 *   sim 600                 run this many seconds without drawing, then print the tally
 *   time 300                draw this many frames and print the cost of one
 *   realm N                 switch to realm N, as a swipe on the title would
 *   until net|gone|result 60  play on until the ball strikes the net, falls into the hole, or a match is won
 *   close                   close the story page, as the phone's close mark would
 *   restart                 a new world on the same saved settings, as when the game is opened again
 *   cabinet hole 11 3       put an exhibit in the cabinet, as if that match had been won
 *   # comment
 */
public final class Stand implements World.Host {
    private static final float DT = 1f / 60f;

    private final File outDir;
    private World world;
    private Px frame;
    private Bot bot;
    private int screenW = 1096, screenH = 2560;
    private float time;
    private int frames, shots, buzzes;
    private final Map<Integer, Integer> cues = new TreeMap<Integer, Integer>();
    private final Map<String, Integer> prefs = new HashMap<String, Integer>();
    private final Map<String, String> texts = new HashMap<String, String>();
    private long lastSeenPoints;
    private Game lastGame;
    private boolean storyOpen;

    // tally of simulated play
    private int pointsYou, pointsCpu, matchesYou, matchesCpu, rallies, longest, shotsInRally;
    private final Map<String, Integer> reasons = new TreeMap<String, Integer>();
    private long shotsAtLastPoint;

    private Stand(File outDir) {
        this.outDir = outDir;
    }

    public static void main(String[] args) throws IOException {
        File scene = new File(args.length > 0 ? args[0] : "stand/scene.txt");
        File out = new File(args.length > 1 ? args[1] : "out/stand");
        out.mkdirs();
        new Stand(out).play(scene);
    }

    @Override
    public void buzz(int ms) {
        buzzes++;
    }

    @Override
    public void quit() {
        System.out.println(String.format("%7.2fs  quit requested", time));
    }

    @Override
    public void cue(int id, float vol, float pitch) {
        Integer n = cues.get(id);
        cues.put(id, n == null ? 1 : n + 1);
    }

    @Override
    public void hum(int kind, float level, float near) {
    }

    @Override
    public int pref(String key, int def) {
        Integer v = prefs.get(key);
        return v == null ? def : v;
    }

    @Override
    public void setPref(String key, int value) {
        prefs.put(key, value);
    }

    @Override
    public String prefText(String key) {
        String v = texts.get(key);
        return v == null ? "" : v;
    }

    @Override
    public void setPrefText(String key, String value) {
        texts.put(key, value);
    }

    @Override
    public void story(String key, String title, String note) {
        System.out.println(String.format("%7.2fs  story opened: %s", time, key));
        storyOpen = true;
    }

    private void start() {
        world = new World(this);
        int k = World.pixelSize(screenW);
        frame = new Px(screenW / k, screenH / k);
        world.resize(frame.w, frame.h);
        bot = null;
    }

    private void tick(boolean draw) {
        if (bot != null) bot.step(DT);
        world.update(DT);
        if (draw) world.draw(frame);
        time += DT;
        frames++;
        Game g = world.game;
        if (g != lastGame) {
            lastGame = g;
            lastSeenPoints = 0;
            shotsAtLastPoint = 0;
        }
        if (g.points != lastSeenPoints && world.screen != World.MENU) {
            lastSeenPoints = g.points;
            record(g);
        }
    }

    private void record(Game g) {
        if (g.lastWinner == 0) pointsYou++;
        else pointsCpu++;
        rallies++;
        int len = (int) (g.shots - shotsAtLastPoint);
        shotsAtLastPoint = g.shots;
        shotsInRally += len;
        longest = Math.max(longest, len);
        String key = (g.lastWinner == 0 ? "you win: " : "cpu wins: ") + g.lastReason;
        Integer n = reasons.get(key);
        reasons.put(key, n == null ? 1 : n + 1);
        if (g.winner >= 0) {
            if (g.winner == 0) matchesYou++;
            else matchesCpu++;
        }
    }

    private void step() {
        tick(true);
    }

    private void run(float seconds) {
        int n = Math.max(1, Math.round(seconds / DT));
        for (int i = 0; i < n; i++) step();
    }

    private void sim(float seconds) {
        int n = Math.max(1, Math.round(seconds / DT));
        float stuckSince = time;
        int lastState = -1, lastScreen = -1;
        for (int i = 0; i < n; i++) {
            tick(false);
            int st = world.game.state + 10 * world.screen + 100 * world.game.shots;
            if (st != lastState) {
                stuckSince = time;
                lastState = st;
            }
            if (time - stuckSince > 25f && world.game.state == Game.RALLY) {
                System.out.println(String.format("%7.2fs  STUCK in a rally for 25 s: ball %.2f %.2f %.2f v %.2f %.2f %.2f",
                        time, world.game.ball.x, world.game.ball.y, world.game.ball.z,
                        world.game.ball.vx, world.game.ball.vy, world.game.ball.vz));
                stuckSince = time;
            }
        }
        report();
    }

    private void report() {
        int total = pointsYou + pointsCpu;
        System.out.println(String.format("tally: %d points (you %d, cpu %d), matches you %d / cpu %d, rallies avg %.1f strikes, longest %d",
                total, pointsYou, pointsCpu, matchesYou, matchesCpu, rallies == 0 ? 0f : shotsInRally / (float) rallies, longest));
        for (Map.Entry<String, Integer> e : reasons.entrySet()) System.out.println("   " + e.getKey() + "  " + e.getValue());
        System.out.println("   cues " + cues + ", buzzes " + buzzes);
        pointsYou = pointsCpu = matchesYou = matchesCpu = rallies = longest = shotsInRally = 0;
        reasons.clear();
    }

    private void play(File scene) throws IOException {
        start();
        BufferedReader r = new BufferedReader(new FileReader(scene));
        String line;
        int no = 0;
        while ((line = r.readLine()) != null) {
            no++;
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\\s+");
            try {
                exec(p);
            } catch (RuntimeException e) {
                System.out.println("scene line " + no + ": " + line);
                throw e;
            }
        }
        r.close();
        System.out.println(String.format("done: %d frames, %.1fs of game time, %d shots, %d buzzes",
                frames, time, shots, buzzes));
    }

    private void exec(String[] p) throws IOException {
        String c = p[0];
        if (c.equals("screen")) {
            screenW = Integer.parseInt(p[1]);
            screenH = Integer.parseInt(p[2]);
            start();
        } else if (c.equals("wait")) {
            run(f(p[1]));
        } else if (c.equals("close")) {
            if (storyOpen) world.storyClosed();
            storyOpen = false;
            step();
        } else if (c.equals("restart")) {
            world = new World(this);
            world.resize(frame.w, frame.h);
            bot = null;
            step();
        } else if (c.equals("cabinet")) {
            world.cabinet.add(p[1], Integer.parseInt(p[2]), Integer.parseInt(p[3]), p.length > 4 ? Integer.parseInt(p[4]) : 0);
            setPrefText("cabinet", world.cabinet.save());
        } else if (c.equals("realm")) {
            world.pick(Integer.parseInt(p[1]));
        } else if (c.equals("time")) {
            int n = Integer.parseInt(p[1]);
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) step();
            long t1 = System.nanoTime();
            System.out.println(String.format("frame %dx%d: %.2f ms per update and draw", frame.w, frame.h, (t1 - t0) / 1e6 / n));
        } else if (c.equals("until")) {
            int n = Math.round(f(p[2]) / DT);
            for (int i = 0; i < n; i++) {
                step();
                Game g = world.game;
                if (p[1].equals("result") ? world.screen == World.RESULT && world.youWon
                        : p[1].equals("net") ? g.netHitAge < 0.02f && g.state == Game.RALLY : g.goneAge < 0.02f) break;
            }
        } else if (c.equals("sim")) {
            sim(f(p[1]));
        } else if (c.equals("down")) {
            world.touch(World.DOWN, f(p[1]), f(p[2]));
            step();
        } else if (c.equals("move")) {
            world.touch(World.MOVE, f(p[1]), f(p[2]));
            step();
        } else if (c.equals("up")) {
            world.touch(World.UP, f(p[1]), f(p[2]));
            step();
        } else if (c.equals("tap")) {
            world.touch(World.DOWN, f(p[1]), f(p[2]));
            step();
            world.touch(World.UP, f(p[1]), f(p[2]));
            step();
        } else if (c.equals("drag")) {
            float x0 = f(p[1]), y0 = f(p[2]), x1 = f(p[3]), y1 = f(p[4]);
            int n = Math.max(2, Math.round(f(p[5]) / DT));
            world.touch(World.DOWN, x0, y0);
            step();
            for (int i = 1; i <= n; i++) {
                float k = i / (float) n;
                world.touch(World.MOVE, x0 + (x1 - x0) * k, y0 + (y1 - y0) * k);
                step();
            }
            world.touch(World.UP, x1, y1);
            step();
        } else if (c.equals("back")) {
            world.back();
            step();
        } else if (c.equals("shot")) {
            save(p[1]);
        } else if (c.equals("bot")) {
            if (p[1].equals("on")) bot = new Bot(world, 7);
            else if (p[1].equals("off")) bot = null;
            else if (p[1].equals("noise")) bot.noise = f(p[2]);
            else if (p[1].equals("speed")) bot.vmax = f(p[2]);
            else if (p[1].equals("swipe")) bot.swipe = f(p[2]);
            else if (p[1].equals("react")) bot.react = f(p[2]);
        } else if (c.equals("level")) {
            world.level = Integer.parseInt(p[1]);
            world.realm().setLevel(world.level);
        } else {
            throw new IllegalArgumentException("unknown command: " + c);
        }
    }

    private static float f(String s) {
        return Float.parseFloat(s);
    }

    /** The frame scaled up by a whole factor with hard edges, as the phone shows it. */
    private void save(String name) throws IOException {
        int k = World.pixelSize(screenW);
        BufferedImage img = new BufferedImage(frame.w * k, frame.h * k, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < frame.h; y++)
            for (int x = 0; x < frame.w; x++) {
                int c = frame.a[y * frame.w + x] | 0xFF000000;
                for (int j = 0; j < k; j++)
                    for (int i = 0; i < k; i++) img.setRGB(x * k + i, y * k + j, c);
            }
        File f = new File(outDir, name + ".png");
        ImageIO.write(img, "png", f);
        shots++;
        System.out.println(String.format("%7.2fs  %s", time, f.getPath()));
    }
}
