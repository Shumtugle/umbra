package com.shumtugle.umbra;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The window: full screen, no bars, screen kept on. Answers the world's requests
 * to the phone, and shows a realm's story over the game as a page of text.
 */
public final class MainActivity extends Activity implements World.Host {
    private GameView view;
    private Vibrator vibrator;
    private Sound sound;
    private SharedPreferences prefs;
    private FrameLayout root;
    private View story;

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
        root = new FrameLayout(this);
        root.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
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
        if (story != null) closeStory();
        else view.backPressed();
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
    public void hum(int kind, float level, float near) {
        sound.hum(kind, level, near);
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
    public String prefText(String key) {
        return prefs.getString(key, "");
    }

    @Override
    public void setPrefText(String key, String value) {
        prefs.edit().putString(key, value).apply();
    }

    @Override
    public void story(final String key, final String title, final String note) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showStory(key, title, note);
            }
        });
    }

    /** A dark page over the game: the realm's name, its line, and the story, to scroll and read. */
    private void showStory(String key, String title, String note) {
        if (story != null) return;
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (26 * dp);
        col.setPadding(pad, (int) (72 * dp), pad, (int) (56 * dp));
        TextView head = new TextView(this);
        head.setText(capitalize(title));
        head.setTextColor(0xFFFFC857);
        head.setTextSize(28);
        head.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        col.addView(head);
        TextView line = new TextView(this);
        line.setText(sentence(note));
        line.setTextColor(0xFFA8BCE0);
        line.setTextSize(16);
        line.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
        line.setPadding(0, (int) (6 * dp), 0, (int) (22 * dp));
        col.addView(line);
        TextView body = new TextView(this);
        body.setText(readStory(key));
        body.setTextColor(0xFFE6E2D6);
        body.setTextSize(18);
        body.setTypeface(Typeface.SERIF);
        body.setLineSpacing(0f, 1.35f);
        col.addView(body);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xF20B0D14);
        scroll.addView(col);
        TextView close = new TextView(this);
        close.setText("\u00D7");
        close.setTextColor(0xFFFFC857);
        close.setTextSize(34);
        close.setPadding((int) (20 * dp), (int) (18 * dp), (int) (22 * dp), (int) (10 * dp));
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeStory();
            }
        });
        FrameLayout page = new FrameLayout(this);
        page.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page.addView(close, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END));
        story = page;
        root.addView(page, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void closeStory() {
        if (story == null) return;
        root.removeView(story);
        story = null;
        hideBars();
        view.storyClosed();
    }

    /** The story's text, from the package; a line of its own if the story is not written yet. */
    private String readStory(String key) {
        try {
            InputStream in = getAssets().open("stories/" + key + ".txt");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            return out.toString("UTF-8").trim();
        } catch (IOException e) {
            return "This story is still being written.";
        }
    }

    /** A line in capitals made a sentence: only its first letter stays large. */
    private static String sentence(String s) {
        String low = s.toLowerCase().replace(" bc ", " BC ");
        return low.isEmpty() ? low : Character.toUpperCase(low.charAt(0)) + low.substring(1);
    }

    /** THE BLACK RIVER becomes The Black River. */
    private static String capitalize(String s) {
        StringBuilder b = new StringBuilder(s.length());
        boolean start = true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(start ? c : Character.toLowerCase(c));
            start = !Character.isLetter(c);
        }
        return b.toString();
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
