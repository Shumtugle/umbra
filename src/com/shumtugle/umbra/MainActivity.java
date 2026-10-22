package com.shumtugle.umbra;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/** The window: full screen, no bars, screen kept on. Answers the world's requests to the phone. */
public final class MainActivity extends Activity implements World.Host {
    private GameView view;
    private Vibrator vibrator;
    private Sound sound;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        prefs = getSharedPreferences("umbra", MODE_PRIVATE);
        sound = new Sound();
        view = new GameView(this);
        setContentView(view);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideBars();
        sound.start();
        view.resumeLoop();
    }

    @Override
    protected void onPause() {
        view.pauseLoop();
        sound.stop();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) hideBars();
    }

    @Override
    public void onBackPressed() {
        view.backPressed();
    }

    @SuppressWarnings("deprecation")
    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void buzz(int ms) {
        if (vibrator != null && vibrator.hasVibrator())
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    @Override
    public void cue(int id, float vol, float pitch) {
        sound.cue(id, vol, pitch);
    }

    @Override
    public void hum(float level, float near) {
        sound.hum(level, near);
    }

    @Override
    public int pref(String key, int def) {
        return prefs.getInt(key, def);
    }

    @Override
    public void setPref(String key, int value) {
        prefs.edit().putInt(key, value).apply();
    }

    @Override
    public void quit() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        });
    }
}
