package com.shumtugle.umbra;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Writes the game's sounds to WAV files through the very synthesizer the phone
 * uses: every cue on its own, then a short scene where the ball passes the hole,
 * strikes the cord and is swallowed. Prints the loudness of each, so silence and
 * clipping show up without ears.
 *
 *   java ... Listen out/sound
 */
public final class Listen {
    private static final String[] NAMES = {"paddle", "table", "net", "cord", "gone", "point", "loss", "won", "lost", "let", "start", "tick"};

    public static void main(String[] args) throws IOException {
        File dir = new File(args.length > 0 ? args[0] : "out/sound");
        dir.mkdirs();
        int rate = 48000;
        for (int id = 0; id < NAMES.length; id++) {
            Synth s = new Synth(rate);
            int n = (int) ((s.length(id) + 0.3f) * rate);
            short[] out = new short[n];
            s.hum(Synth.HUM_DRONE, 0f, 0f);
            s.cue(id, 1f, 1f);
            s.render(out, n);
            write(new File(dir, (id < 10 ? "0" : "") + id + "_" + NAMES[id] + ".wav"), out, rate);
            report(NAMES[id], out);
        }
        // a scene in each realm: the hum, a rally, the ball closing in, then gone or thrown back
        for (int scene = 0; scene < 2; scene++) {
            boolean mains = scene == 1;
            Synth s = new Synth(rate);
            int n = rate * 6;
            short[] out = new short[n];
            int block = 256;
            float[] at = {0.3f, 0.9f, 1.6f, 2.2f, 3.0f, 3.5f, 4.2f};
            int[] what = {Game.C_PADDLE, Game.C_TABLE, Game.C_PADDLE, Game.C_TABLE, Game.C_CORD, Game.C_PADDLE, mains ? Game.C_TABLE : Game.C_GONE};
            short[] tmp = new short[block];
            int next = 0;
            for (int i = 0; i < n; i += block) {
                float t = i / (float) rate;
                while (next < at.length && at[next] <= t) s.cue(what[next++], 1f, 1f);
                float near = t < 3.5f ? 0.1f : Math.min(1f, (t - 3.5f) / 0.7f);
                if (t > 4.2f) near = 0f;
                s.hum(mains ? Synth.HUM_MAINS : Synth.HUM_DRONE, 1f, near * near);
                s.render(tmp, block);
                System.arraycopy(tmp, 0, out, i, Math.min(block, n - i));
            }
            write(new File(dir, mains ? "scene_lab.wav" : "scene_space.wav"), out, rate);
            report(mains ? "lab" : "space", out);
        }
    }

    private static void report(String name, short[] o) {
        double sum = 0;
        int peak = 0, clipped = 0;
        for (short v : o) {
            sum += v * (double) v;
            peak = Math.max(peak, Math.abs(v));
            if (Math.abs(v) >= 29900) clipped++;
        }
        double rms = Math.sqrt(sum / o.length);
        System.out.println(String.format("%-7s %.2fs  peak %5.1f%%  rms %5.1f%%  at the limit %d",
                name, o.length / 48000f, peak / 327.67, rms / 327.67, clipped));
    }

    private static void write(File f, short[] pcm, int rate) throws IOException {
        DataOutputStream d = new DataOutputStream(new FileOutputStream(f));
        int bytes = pcm.length * 2;
        d.writeBytes("RIFF");
        d.writeInt(Integer.reverseBytes(36 + bytes));
        d.writeBytes("WAVEfmt ");
        d.writeInt(Integer.reverseBytes(16));
        d.writeShort(Short.reverseBytes((short) 1));
        d.writeShort(Short.reverseBytes((short) 1));
        d.writeInt(Integer.reverseBytes(rate));
        d.writeInt(Integer.reverseBytes(rate * 2));
        d.writeShort(Short.reverseBytes((short) 2));
        d.writeShort(Short.reverseBytes((short) 16));
        d.writeBytes("data");
        d.writeInt(Integer.reverseBytes(bytes));
        for (short v : pcm) d.writeShort(Short.reverseBytes(v));
        d.close();
    }
}
