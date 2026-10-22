package com.shumtugle.umbra;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/**
 * The speaker: a thread that feeds the synthesizer's output to the phone at the
 * phone's own sample rate, in small blocks, so a knock is heard with the frame
 * that shows it.
 */
final class Sound implements Runnable {
    private static final int BLOCK = 256;

    private Synth synth;
    private volatile boolean running;
    private Thread thread;

    void cue(int id, float vol, float pitch) {
        Synth s = synth;
        if (s != null) s.cue(id, vol, pitch);
    }

    void hum(int kind, float level, float near) {
        Synth s = synth;
        if (s != null) s.hum(kind, level, near);
    }

    void start() {
        if (running) return;
        running = true;
        thread = new Thread(this, "sound");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    void stop() {
        running = false;
        if (thread != null) {
            try {
                thread.join(800);
            } catch (InterruptedException ignored) {
            }
            thread = null;
        }
    }

    @Override
    public void run() {
        int rate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        if (rate < 8000 || rate > 96000) rate = 48000;
        if (synth == null || synth.rate != rate) synth = new Synth(rate);
        AudioTrack t;
        try {
            int min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            t = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(rate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(min, BLOCK * 2 * 4))
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            t.play();
        } catch (RuntimeException e) {
            running = false;
            return;
        }
        short[] buf = new short[BLOCK];
        try {
            while (running) {
                synth.render(buf, buf.length);
                t.write(buf, 0, buf.length);
            }
        } finally {
            try {
                t.pause();
                t.flush();
            } catch (RuntimeException ignored) {
            }
            t.release();
        }
    }
}
