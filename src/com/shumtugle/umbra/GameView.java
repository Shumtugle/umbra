package com.shumtugle.umbra;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * The phone side of the game loop. It owns a thread, queues touches from the UI
 * thread and hands them to the world on the game thread, so game state is only
 * ever touched there. The world paints a small frame, which is scaled up to the
 * screen by a whole number without smoothing: every game pixel is a crisp square
 * of the same size.
 */
final class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {
    private final World world;
    private Thread thread;
    private volatile boolean running;
    private int viewW, viewH, W, H, k = 1;
    private Px frame;
    private Bitmap fb;
    private final Paint blit = new Paint();
    private final Rect dst = new Rect();

    // touches, handed from the UI thread to the game thread
    private final float[] evX = new float[64], evY = new float[64];
    private final int[] evType = new int[64];
    private int evCount;
    private boolean backPending, storyDone;

    GameView(MainActivity act) {
        super(act);
        world = new World(act);
        getHolder().addCallback(this);
        setFocusable(true);
        blit.setFilterBitmap(false);
    }

    void resumeLoop() {
        if (running) return;
        running = true;
        thread = new Thread(this, "game");
        thread.start();
    }

    void pauseLoop() {
        running = false;
        if (thread != null) {
            try {
                thread.join(500);
            } catch (InterruptedException ignored) {
            }
            thread = null;
        }
    }

    void backPressed() {
        synchronized (this) {
            backPending = true;
        }
    }

    /** The story over the game was closed; the world hears of it on its own thread. */
    void storyClosed() {
        synchronized (this) {
            storyDone = true;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder h) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int format, int w, int hh) {
        synchronized (this) {
            viewW = w;
            viewH = hh;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        int type = a == MotionEvent.ACTION_DOWN ? World.DOWN : a == MotionEvent.ACTION_MOVE ? World.MOVE
                : a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL ? World.UP : -1;
        if (type < 0) return true;
        synchronized (this) {
            if (evCount < evX.length && viewW > 0) {
                // into game pixels
                int px = World.pixelSize(viewW);
                float ox = (viewW - viewW / px * px) / 2f, oy = (viewH - viewH / px * px) / 2f;
                evX[evCount] = (e.getX() - ox) / px;
                evY[evCount] = (e.getY() - oy) / px;
                evType[evCount] = type;
                evCount++;
            }
        }
        return true;
    }

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - last) / 1e9f);
            last = now;
            if (!ensureBuffer()) {
                sleep(16);
                continue;
            }
            processEvents();
            world.update(dt);
            world.draw(frame);
            present();
            long spent = (System.nanoTime() - now) / 1000000L;
            if (spent < 16) sleep(16 - spent);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    /** The frame: the screen divided by a whole pixel size. */
    private boolean ensureBuffer() {
        int vw, vh;
        synchronized (this) {
            vw = viewW;
            vh = viewH;
        }
        if (vw <= 0 || vh <= 0) return false;
        int px = World.pixelSize(vw);
        int w = Math.max(1, vw / px), h = Math.max(1, vh / px);
        if (frame == null || W != w || H != h) {
            W = w;
            H = h;
            k = px;
            frame = new Px(W, H);
            fb = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            world.resize(W, H);
        }
        return true;
    }

    private void processEvents() {
        boolean back, done;
        synchronized (this) {
            for (int i = 0; i < evCount; i++) world.touch(evType[i], evX[i], evY[i]);
            evCount = 0;
            back = backPending;
            backPending = false;
            done = storyDone;
            storyDone = false;
        }
        if (done) world.storyClosed();
        if (back) world.back();
    }

    /** The small frame, scaled up to the screen with hard edges. */
    private void present() {
        fb.setPixels(frame.a, 0, W, 0, 0, W, H);
        SurfaceHolder h = getHolder();
        Canvas c = h.lockCanvas();
        if (c == null) return;
        try {
            // centred; a few leftover screen pixels at the edges stay black
            int ox = (c.getWidth() - W * k) / 2, oy = (c.getHeight() - H * k) / 2;
            dst.set(ox, oy, ox + W * k, oy + H * k);
            c.drawColor(0xFF000000);
            c.drawBitmap(fb, null, dst, blit);
        } finally {
            h.unlockCanvasAndPost(c);
        }
    }
}
