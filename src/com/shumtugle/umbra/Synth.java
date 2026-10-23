package com.shumtugle.umbra;

/**
 * Every sound in the game, made from numbers; nothing is recorded. Short cues are
 * synthesized once into a bank when the game starts and played back at any pitch;
 * the hum of the realm is made live, so it can follow the ball. A small room of
 * echoes gives the knocks a place to sound in. Plain Java: the phone feeds the
 * output to its speaker, the stand writes it to a file.
 */
final class Synth {
    static final int HUM_DRONE = 0, HUM_MAINS = 1, HUM_STATION = 2, HUM_CICADAS = 3, HUM_BLIZZARD = 4, HUM_CHAMBER = 5, HUM_OPTICS = 6, HUM_DARKROOM = 7;

    final int rate;
    private final float[][] bank = new float[13][];

    private static final int VOICES = 16, QUEUE = 32;
    private final Object lock = new Object();
    private final int[] qId = new int[QUEUE];
    private final float[] qVol = new float[QUEUE], qPitch = new float[QUEUE];
    private int qCount;
    private final int[] vId = new int[VOICES];
    private final float[] vPos = new float[VOICES], vStep = new float[VOICES], vVol = new float[VOICES];

    // the hum: targets set from the game, values eased toward them per sample
    private volatile float humLevelT, humNearT;
    private volatile int humKind;
    private float dripAmp, dripPh, dripF = 1200f;
    private float crackle, crackleLp, phSwell, cicLow, cicBand, phCic, phChorus, wind;
    private float humLevel, humNear, ph1, ph2, ph3, svfLow, svfBand;
    private int seed = 0x2545F491;

    // the room
    private final float[][] comb;
    private final int[] combPos;
    private final float[] combLp;
    private final float[][] pass;
    private final int[] passPos;

    Synth(int rate) {
        this.rate = rate;
        for (int i = 0; i < VOICES; i++) vId[i] = -1;
        float s = rate / 44100f;
        int[] cd = {1557, 1617, 1491, 1422};
        comb = new float[4][];
        for (int i = 0; i < 4; i++) comb[i] = new float[(int) (cd[i] * s)];
        combPos = new int[4];
        combLp = new float[4];
        int[] ad = {556, 225};
        pass = new float[2][];
        for (int i = 0; i < 2; i++) pass[i] = new float[(int) (ad[i] * s)];
        passPos = new int[2];
        build();
    }

    // ---- the bank

    private void build() {
        bank[Game.C_PADDLE] = knock(0.16f, new float[]{480, 1150, 2230, 3600}, new float[]{38, 65, 110, 170},
                new float[]{1f, 0.45f, 0.25f, 0.12f}, 160f, 45f, 0.35f, 900f, 0.5f);
        bank[Game.C_TABLE] = knock(0.15f, new float[]{1020, 2380, 4150}, new float[]{60, 95, 160},
                new float[]{0.8f, 0.4f, 0.18f}, 115f, 22f, 0.45f, 1400f, 0.45f);
        bank[Game.C_NET] = net();
        bank[Game.C_CORD] = cord();
        bank[Game.C_GONE] = gone();
        bank[Game.C_POINT] = bells(new float[]{784, 1175}, 0.09f, 1.0f, 1.4f, 0.8f);
        bank[Game.C_LOSS] = bells(new float[]{587, 440}, 0.12f, 1.0f, 1f, 0.5f);
        bank[Game.C_WON] = bells(new float[]{523, 659, 784, 1047}, 0.11f, 1.7f, 1.4f, 0.8f);
        bank[Game.C_LOST] = bells(new float[]{440, 349, 294, 220}, 0.16f, 1.8f, 1f, 0.45f);
        bank[Game.C_LET] = let();
        bank[Game.C_START] = start();
        bank[Game.C_SHOT] = shot();
        bank[Game.C_TICK] = knock(0.05f, new float[]{1800, 3900}, new float[]{120, 200}, new float[]{0.5f, 0.2f},
                0f, 1f, 0f, 2000f, 0.15f);
    }

    private float noise() {
        seed = seed * 1103515245 + 12345;
        return ((seed >>> 16) & 0x7FFF) / 16384f - 1f;
    }

    private static float sin(double p) {
        return (float) Math.sin(p);
    }

    /** A struck body: a few ringing modes, a low thump under them, and a click of noise. */
    private float[] knock(float len, float[] hz, float[] decay, float[] amp, float low, float lowDecay, float lowAmp,
                          float clickDecay, float clickAmp) {
        int n = (int) (len * rate);
        float[] o = new float[n];
        float prev = 0f;
        for (int i = 0; i < n; i++) {
            float t = i / (float) rate;
            float v = 0f;
            for (int m = 0; m < hz.length; m++) v += sin(6.2831853 * hz[m] * t) * Math.exp(-t * decay[m]) * amp[m];
            if (lowAmp > 0f) v += sin(6.2831853 * low * t) * Math.exp(-t * lowDecay) * lowAmp;
            float nz = noise();
            v += (nz - prev) * 0.5f * (float) Math.exp(-t * clickDecay) * clickAmp;    // a bright click
            prev = nz;
            v *= Math.min(1f, t * rate / 24f);                                        // half a millisecond of attack
            o[i] = v;
        }
        return norm(o, 0.9f);
    }

    /** The ball into the mesh: a soft, muffled thud with a rustle of threads. */
    private float[] net() {
        int n = (int) (0.28f * rate);
        float[] o = new float[n];
        float lp = 0f, lp2 = 0f;
        for (int i = 0; i < n; i++) {
            float t = i / (float) rate;
            lp += (noise() - lp) * 0.09f;
            lp2 += (lp - lp2) * 0.2f;
            float env = (float) (Math.exp(-t * 26f) * (1f - Math.exp(-t * 700f)));
            float rustle = (0.6f + 0.4f * sin(6.2831853 * 57f * t)) * (float) Math.exp(-t * 18f);
            o[i] = lp2 * env * 3f + lp * rustle * 0.9f + sin(6.2831853 * 92f * t) * (float) Math.exp(-t * 20f) * 0.45f;
        }
        return norm(o, 0.85f);
    }

    /** The ball on the top cord: a sharp tick and the cord buzzing for a moment. */
    private float[] cord() {
        int n = (int) (0.34f * rate);
        float[] o = new float[n];
        for (int i = 0; i < n; i++) {
            float t = i / (float) rate;
            float tick = sin(6.2831853 * 2400f * t) * (float) Math.exp(-t * 300f);
            float str = 0f;
            for (int k = 1; k <= 6; k++) str += sin(6.2831853 * 196f * k * t) / k;
            str *= (1f + 0.55f * sin(6.2831853 * 31f * t)) * (float) Math.exp(-t * 13f) * 0.35f;
            o[i] = tick * 0.7f + str + noise() * (float) Math.exp(-t * 600f) * 0.3f;
        }
        return norm(o, 0.85f);
    }

    /** The ball crossing the horizon: a falling sweep that ends in a deep thump. */
    private float[] gone() {
        int n = (int) (1.6f * rate);
        float[] o = new float[n];
        double ph = 0, ph2 = 0;
        float lp = 0f, lp2 = 0f;
        for (int i = 0; i < n; i++) {
            float t = i / (float) rate;
            float hz = 760f * (float) Math.exp(-t * 3.4f) + 34f;
            ph += 6.2831853 * hz / rate;
            ph2 += 6.2831853 * hz * 4.02f / rate;
            float env = (float) ((1f - Math.exp(-t * 60f)) * Math.exp(-t * 3.2f));
            float cut = 0.08f * (float) Math.exp(-t * 3f) + 0.004f;
            lp += (noise() - lp) * cut;
            lp2 += (lp - lp2) * cut;
            float v = (float) Math.sin(ph) * 0.7f + (float) Math.sin(ph2) * 0.12f * (float) Math.exp(-t * 6f) + lp2 * 3f;
            v *= env;
            if (t > 0.62f) {
                float u = t - 0.62f;
                v += sin(6.2831853 * 44f * u) * Math.exp(-u * 5f) * Math.min(1f, u * 200f) * 0.9f;
            }
            o[i] = v;
        }
        return norm(o, 0.95f);
    }

    /** Bell notes one after another: brightness is how much the tone is frequency modulated. */
    private float[] bells(float[] notes, float gap, float len, float ratio, float bright) {
        int n = (int) (len * rate);
        float[] o = new float[n];
        for (int k = 0; k < notes.length; k++) {
            int start = (int) (k * gap * rate);
            boolean last = k == notes.length - 1;
            float decay = last ? 5f : 9f;
            for (int i = start; i < n; i++) {
                float t = (i - start) / (float) rate;
                float idx = bright * 2.2f * (float) Math.exp(-t * 7f);
                float mod = sin(6.2831853 * notes[k] * ratio * t) * idx;
                float v = (float) Math.sin(6.2831853 * notes[k] * t + mod) * (float) Math.exp(-t * decay);
                v += sin(6.2831853 * notes[k] * 2.01f * t) * Math.exp(-t * decay * 2f) * 0.18f;
                o[i] += v * Math.min(1f, t * rate / 40f) * 0.55f;
            }
        }
        return norm(o, 0.7f);
    }

    /** A pistol: a dry crack, a heavy boom under it, and the report rolling away. */
    private float[] shot() {
        int n = (int) (1.4f * rate);
        float[] o = new float[n];
        float lp = 0f;
        for (int i = 0; i < n; i++) {
            float t = i / (float) rate;
            float nz = noise();
            lp += (nz - lp) * (0.5f * (float) Math.exp(-t * 6f) + 0.02f);
            float crack = nz * (float) Math.exp(-t * 90f);
            float boom = sin(6.2831853 * 55f * t) * (float) Math.exp(-t * 7f) + sin(6.2831853 * 170f * t) * (float) Math.exp(-t * 20f) * 0.5f;
            float roll = lp * (float) Math.exp(-t * 2.8f) * 1.4f;
            o[i] = crack * 1.2f + boom * 0.8f + roll;
        }
        return norm(o, 0.95f);
    }

    private float[] let() {
        int n = (int) (0.3f * rate);
        float[] o = new float[n];
        for (int k = 0; k < 2; k++) {
            int s = (int) (k * 0.1f * rate);
            for (int i = s; i < n; i++) {
                float t = (i - s) / (float) rate;
                o[i] += sin(6.2831853 * (1500f - 200f * k) * t) * Math.exp(-t * 45f) * 0.6f;
            }
        }
        return norm(o, 0.6f);
    }

    /** The match begins: air rising into a soft tone. */
    private float[] start() {
        int n = (int) (0.7f * rate);
        float[] o = new float[n];
        float lp = 0f;
        double ph = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) rate;
            float k = t / 0.7f;
            lp += (noise() - lp) * (0.02f + 0.25f * k);
            ph += 6.2831853 * (180f + 520f * k * k) / rate;
            float env = (float) Math.sin(Math.PI * Math.min(1f, k * 1.15f));
            o[i] = (lp * 1.4f + (float) Math.sin(ph) * 0.35f) * env * env;
        }
        return norm(o, 0.6f);
    }

    private static float[] norm(float[] o, float peak) {
        float m = 1e-6f;
        for (float v : o) m = Math.max(m, Math.abs(v));
        float k = peak / m;
        for (int i = 0; i < o.length; i++) o[i] *= k;
        // a short fade at the end, so nothing clicks
        int fade = Math.min(o.length / 4, 480);
        for (int i = 0; i < fade; i++) o[o.length - 1 - i] *= i / (float) fade;
        return o;
    }

    /** Length of a cue in seconds, for the stand. */
    float length(int id) {
        return bank[id].length / (float) rate;
    }

    float[] sample(int id) {
        return bank[id];
    }

    // ---- playing

    /** Asks for a cue; may be called from any thread. */
    void cue(int id, float vol, float pitch) {
        synchronized (lock) {
            if (qCount < QUEUE) {
                qId[qCount] = id;
                qVol[qCount] = vol;
                qPitch[qCount] = pitch;
                qCount++;
            }
        }
    }

    /**
     * Sets the hum: its kind (a deep drone, or the buzz of mains with sparks), its
     * overall level and how near the ball is to the realm's thing, both 0..1.
     */
    void hum(int kind, float level, float near) {
        humKind = kind;
        humLevelT = level;
        humNearT = near;
    }

    /** Fills n samples of 16-bit sound. */
    void render(short[] out, int n) {
        synchronized (lock) {
            for (int q = 0; q < qCount; q++) {
                int slot = 0;
                float oldest = -1f;
                for (int i = 0; i < VOICES; i++) {
                    if (vId[i] < 0) {
                        slot = i;
                        break;
                    }
                    float age = vPos[i] / bank[vId[i]].length;
                    if (age > oldest) {
                        oldest = age;
                        slot = i;
                    }
                }
                vId[slot] = qId[q];
                vPos[slot] = 0f;
                vStep[slot] = qPitch[q];
                vVol[slot] = qVol[q];
            }
            qCount = 0;
        }
        float lt = humLevelT, nt = humNearT;
        boolean mains = humKind == HUM_MAINS, station = humKind == HUM_STATION, cicadas = humKind == HUM_CICADAS,
                blizzard = humKind == HUM_BLIZZARD, chamber = humKind == HUM_CHAMBER, optics = humKind == HUM_OPTICS,
                darkroom = humKind == HUM_DARKROOM;
        float cicF = 2f * (float) Math.sin(Math.PI * 5600.0 / rate);
        float ease = 1f - (float) Math.exp(-1.0 / (0.06 * rate));
        float twoPi = 6.2831853f / rate;
        for (int s = 0; s < n; s++) {
            float dry = 0f;
            for (int i = 0; i < VOICES; i++) {
                int id = vId[i];
                if (id < 0) continue;
                float[] b = bank[id];
                int p = (int) vPos[i];
                if (p + 1 >= b.length) {
                    vId[i] = -1;
                    continue;
                }
                float fr = vPos[i] - p;
                dry += (b[p] + (b[p + 1] - b[p]) * fr) * vVol[i];
                vPos[i] += vStep[i];
            }
            // the hum: a low drone that rises and thickens as the ball comes near, and a thin whine
            humLevel += (lt - humLevel) * ease;
            humNear += (nt - humNear) * ease;
            float f0 = mains || chamber || darkroom ? 25f : station ? 33f : 41f * (1f + 0.4f * humNear);
            ph1 += f0 * twoPi;
            ph2 += f0 * 2.003f * twoPi;
            ph3 += f0 * 1.498f * twoPi;
            if (ph1 > 6.2831853f) ph1 -= 6.2831853f;
            if (ph2 > 6.2831853f) ph2 -= 6.2831853f;
            if (ph3 > 6.2831853f) ph3 -= 6.2831853f;
            float hum;
            if (darkroom) {
                // a darkroom: water running through the wash tray, the safelight's transformer, a drop now and then
                float nz = noise();
                phSwell += 0.09f * twoPi;
                if (phSwell > 6.2831853f) phSwell -= 6.2831853f;
                float fc = (650f + 300f * (float) Math.sin(phSwell)) / rate;
                float fq = 2f * (float) Math.sin(Math.PI * fc);
                svfLow += fq * svfBand;
                float high = nz - svfLow - 0.25f * svfBand;
                svfBand += fq * high;
                float gurgle = 0.7f + 0.3f * (float) Math.sin(phSwell * 3.1f);
                if (dripAmp < 0.002f && noise() > 1f - 0.000012f) {
                    dripAmp = 0.22f;
                    dripF = 1100f + 500f * Math.abs(noise());
                }
                dripPh += dripF * twoPi;
                if (dripPh > 6.2831853f) dripPh -= 6.2831853f;
                dripF *= 1.00004f;
                float drip = dripAmp * (float) Math.sin(dripPh);
                dripAmp *= 0.9993f;
                float coil = (float) (Math.sin(ph1 * 2.0) * 0.5 + Math.sin(ph1 * 4.0) * 0.2);
                hum = humLevel * (svfBand * 0.03f * gurgle + coil * 0.012f + drip);
            } else if (optics) {
                // a dark optics room: still air, and the thin high singing of a light source
                float nz = noise();
                wind += (nz - wind) * 0.02f;
                float sing = (float) (Math.sin(ph1 * 34.0) + 0.6 * Math.sin(ph1 * 34.17 + 1.0)) * (0.6f + 0.4f * (float) Math.sin(ph3 * 0.01f));
                hum = humLevel * (wind * 0.35f + sing * 0.006f);
            } else if (chamber) {
                // the chamber: the hiss of vapour, the coils' low hum, and a counter clicking at stray particles
                float nz = noise();
                float hiss = nz - wind;
                wind = nz;
                float coil = (float) (Math.sin(ph1 * 4.0) * 0.5 + Math.sin(ph1 * 8.0) * 0.2);
                if (noise() > 1f - 0.00012f - 0.0006f * humNear) crackle = 0.9f;
                crackle *= 0.985f;
                hum = humLevel * (hiss * 0.012f + coil * (0.02f + 0.05f * humNear) + crackle * (noise() > 0f ? 0.5f : -0.5f));
            } else if (blizzard) {
                // the wind: noise through a resonance that rises and howls with the gusts
                float nz = noise();
                float fc = (220f + 700f * humNear + 80f * (float) Math.sin(ph3 * 0.5f)) / rate;
                float fq = 2f * (float) Math.sin(Math.PI * fc);
                svfLow += fq * svfBand;
                float high = nz - svfLow - 0.12f * svfBand;
                svfBand += fq * high;
                wind += (nz - wind) * 0.01f;
                hum = humLevel * (svfBand * (0.05f + 0.12f * humNear) + wind * 0.4f);
            } else if (cicadas) {
                // a summer courtyard: a soft wind, and cicadas singing in long phrases
                float nz = noise();
                wind += (nz - wind) * 0.004f;
                phChorus += 0.17f * twoPi;
                if (phChorus > 6.2831853f) phChorus -= 6.2831853f;
                phCic += 31f * twoPi;
                if (phCic > 6.2831853f) phCic -= 6.2831853f;
                cicLow += cicF * cicBand;
                float high = nz - cicLow - 0.06f * cicBand;
                cicBand += cicF * high;
                float phrase = Col.clamp01((float) Math.sin(phChorus) * 1.6f);
                float pulse = 0.5f + 0.5f * (float) Math.sin(phCic);
                hum = humLevel * (wind * 0.5f + cicBand * 0.035f * phrase * pulse * pulse);
            } else if (station) {
                // the station: a low machine rumble, air moving in ducts, and a slow swell as it turns
                float rumble = (float) (Math.sin(ph1) * 0.6 + Math.sin(ph2) * 0.3);
                float nz = noise();
                svfLow += 0.02f * svfBand;
                float high = nz - svfLow - 0.5f * svfBand;
                svfBand += 0.02f * high;
                phSwell += 0.21f * twoPi;
                if (phSwell > 6.2831853f) phSwell -= 6.2831853f;
                float swell = 0.6f + 0.4f * (float) Math.sin(phSwell);
                hum = humLevel * (rumble * 0.06f + svfBand * 0.05f * swell);
            } else if (mains) {
                // the buzz of a laboratory: a transformer's hum, and sparks as the ball comes near
                float buzz = (float) (Math.sin(ph1 * 2.0) * 0.35 + Math.sin(ph1 * 4.0) * 0.5 + Math.sin(ph1 * 6.0) * 0.2 + Math.sin(ph1 * 8.0) * 0.12);
                float nz = noise();
                if (nz > 1f - 0.006f * humNear * humNear) crackle = 0.4f + 0.6f * Math.abs(noise());
                crackle *= 0.9985f - 0.002f;
                crackleLp += (noise() * crackle - crackleLp) * 0.6f;
                hum = humLevel * (buzz * (0.035f + 0.08f * humNear) + crackleLp * 0.9f);
            } else {
                float drone = (float) (Math.sin(ph1) + 0.45 * Math.sin(ph2) + 0.25 * humNear * Math.sin(ph3));
                float fc = (500f + 1500f * humNear) / rate;
                float fq = 2f * (float) Math.sin(Math.PI * fc);
                float nz = noise();
                svfLow += fq * svfBand;
                float high = nz - svfLow - 0.08f * svfBand;
                svfBand += fq * high;
                hum = humLevel * (drone * (0.05f + 0.30f * humNear) + svfBand * 0.12f * humNear * humNear);
            }
            float wet = room(dry);
            float mix = dry * 1.35f + wet * 0.2f + hum;
            mix = mix * (27f + mix * mix) / (27f + 9f * mix * mix);     // soft limit
            if (mix > 1f) mix = 1f;
            if (mix < -1f) mix = -1f;
            out[s] = (short) (mix * 30000f);
        }
    }

    /** Four combs into two all-passes: a small, dark room. */
    private float room(float in) {
        float sum = 0f;
        for (int i = 0; i < 4; i++) {
            float[] buf = comb[i];
            int p = combPos[i];
            float y = buf[p];
            combLp[i] += (y - combLp[i]) * 0.45f;
            buf[p] = in + combLp[i] * 0.78f;
            combPos[i] = (p + 1) % buf.length;
            sum += y;
        }
        float x = sum * 0.25f;
        for (int i = 0; i < 2; i++) {
            float[] buf = pass[i];
            int p = passPos[i];
            float y = buf[p];
            buf[p] = x + y * 0.5f;
            passPos[i] = (p + 1) % buf.length;
            x = y - x * 0.5f;
        }
        return x;
    }
}
