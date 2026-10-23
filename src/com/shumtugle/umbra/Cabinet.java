package com.shumtugle.umbra;

import java.util.ArrayList;

/**
 * The cabinet of curiosities: every match won leaves an exhibit of the realm it
 * was won in, under a glass dome on a small plinth, with the score on its plate.
 * A match won while the opponent scored three or fewer puts the exhibit on gold;
 * a match won from five points down ties a red ribbon to it.
 *
 * Kept as one line of text: realm, score and marks for each exhibit, in order.
 */
final class Cabinet {
    static final class Item {
        final String realm;
        final int mine, theirs;
        final boolean gold, comeback;

        Item(String realm, int mine, int theirs, boolean gold, boolean comeback) {
            this.realm = realm;
            this.mine = mine;
            this.theirs = theirs;
            this.gold = gold;
            this.comeback = comeback;
        }
    }

    static final int GOLD_AT_MOST = 3, COMEBACK_FROM = 5;

    final ArrayList<Item> items = new ArrayList<Item>();

    void load(String text) {
        items.clear();
        if (text == null || text.isEmpty()) return;
        for (String e : text.split(";")) {
            String[] p = e.split(",");
            if (p.length < 4) continue;
            try {
                int mine = Integer.parseInt(p[1]), theirs = Integer.parseInt(p[2]);
                items.add(new Item(p[0], mine, theirs, p[3].indexOf('g') >= 0, p[3].indexOf('c') >= 0));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    String save() {
        StringBuilder b = new StringBuilder();
        for (Item it : items) {
            if (b.length() > 0) b.append(';');
            b.append(it.realm).append(',').append(it.mine).append(',').append(it.theirs).append(',')
                    .append(it.gold ? "g" : "").append(it.comeback ? "c" : "").append('.');
        }
        return b.toString();
    }

    Item add(String realm, int mine, int theirs, int worstBehind) {
        Item it = new Item(realm, mine, theirs, theirs <= GOLD_AT_MOST, worstBehind >= COMEBACK_FROM);
        items.add(it);
        return it;
    }

    // ---- the exhibits, drawn by hand

    /** An exhibit of size s (the dome's radius) standing with its plinth's top at (cx, cy). */
    static void draw(Px f, Item it, float cx, float cy, float s, float t) {
        int wood = it.gold ? 0xFFD8A830 : 0xFF4A2E18, woodLit = it.gold ? 0xFFFFE07A : 0xFF8A6040;
        // plinth
        f.ellipseAA(cx, cy + s * 0.18f, s * 0.95f, s * 0.22f, Col.mul(wood, 0.6f), 1f);
        f.ellipseAA(cx, cy + s * 0.08f, s * 0.95f, s * 0.2f, wood, 1f);
        f.ellipseAA(cx - s * 0.2f, cy + s * 0.04f, s * 0.5f, s * 0.08f, woodLit, 0.5f);
        float oy = cy - s * 0.55f;   // the exhibit's centre, inside the dome
        String k = it.realm;
        if (k.equals("hole")) hole(f, cx, oy, s * 0.55f, t);
        else if (k.equals("station")) station(f, cx, oy, s * 0.6f, t);
        else if (k.equals("nucleus")) atom(f, cx, oy, s * 0.6f, t);
        else if (k.equals("lyceum")) amphora(f, cx, oy, s * 0.62f);
        else if (k.equals("river")) pistol(f, cx, oy, s * 0.7f);
        else if (k.equals("chamber")) chamber(f, cx, oy, s * 0.6f);
        else if (k.equals("slit")) slits(f, cx, oy, s * 0.62f);
        else if (k.equals("darkroom")) camera(f, cx, oy, s * 0.66f);
        else f.discAA(cx, oy, s * 0.3f, 0xFFB8C8E0, 1f);
        // the glass dome: a faint body, a bright edge and a glint
        f.ellipseAA(cx, cy - s * 0.5f, s * 0.86f, s * 0.6f, 0xFFDDEEFF, 0.06f);
        f.ringAA(cx, cy - s * 0.5f, s * 0.86f, s * 0.6f, 1f, 0xFFDDEEFF, 0.35f);
        f.lineAA(cx - s * 0.55f, cy - s * 0.75f, cx - s * 0.4f, cy - s * 0.95f, Math.max(1f, s * 0.08f), 0xFFFFFFFF, 0.5f);
        if (it.gold) f.glow(cx, cy - s * 0.5f, s * 1.3f, 0xFFFFD040, 0.18f);
        if (it.comeback) {
            f.lineAA(cx + s * 0.55f, cy + s * 0.05f, cx + s * 0.78f, cy + s * 0.42f, Math.max(1.2f, s * 0.12f), 0xFFC8282A, 1f);
            f.lineAA(cx + s * 0.62f, cy + s * 0.05f, cx + s * 0.5f, cy + s * 0.45f, Math.max(1.2f, s * 0.12f), 0xFFA01A1C, 1f);
        }
    }

    private static void hole(Px f, float cx, float cy, float s, float t) {
        f.glow(cx, cy, s * 1.6f, 0xFFFF7A30, 0.35f);
        f.ringAA(cx, cy, s * 1.15f, s * 0.3f, Math.max(1f, s * 0.18f), 0xFFFFB050, 0.9f);
        f.discAA(cx, cy, s * 0.55f, 0xFF000000, 1f);
        f.ringAA(cx, cy, s * 0.58f, s * 0.58f, 0.8f, 0xFFFFEED2, 0.7f);
        f.lineAA(cx - s * 1.1f, cy + s * 0.05f, cx + s * 1.1f, cy + s * 0.05f, Math.max(0.8f, s * 0.1f), 0xFFFFE0A0, 0.8f);
    }

    private static void station(Px f, float cx, float cy, float s, float t) {
        f.ringAA(cx, cy, s, s * 0.42f, Math.max(1.2f, s * 0.2f), 0xFF8A949E, 1f);
        f.ringAA(cx, cy, s, s * 0.42f, 0.8f, 0xFFD8DEE6, 0.5f);
        for (int i = 0; i < 4; i++) {
            float a = i * 1.5708f + t * 0.4f;
            f.lineAA(cx, cy, cx + (float) Math.cos(a) * s, cy + (float) Math.sin(a) * s * 0.42f, 1f, 0xFF6A747E, 1f);
        }
        f.discAA(cx, cy, s * 0.22f, 0xFFB8C0CA, 1f);
        f.discAA(cx + s, cy, Math.max(0.8f, s * 0.09f), 0xFFFF8A2A, 1f);
        f.discAA(cx - s, cy, Math.max(0.8f, s * 0.09f), 0xFFFF8A2A, 1f);
    }

    private static void atom(Px f, float cx, float cy, float s, float t) {
        for (int o = 0; o < 3; o++) {
            float ang = o * 1.0472f, lx = 0f, ly = 0f;
            for (int i = 0; i <= 28; i++) {
                float th = i / 28f * 6.2832f;
                float u = s * (float) Math.cos(th), v = s * 0.36f * (float) Math.sin(th);
                float x = cx + u * (float) Math.cos(ang) - v * (float) Math.sin(ang);
                float y = cy + u * (float) Math.sin(ang) + v * (float) Math.cos(ang);
                if (i > 0) f.lineAA(lx, ly, x, y, 0.8f, 0xFF9FD8FF, 0.6f);
                lx = x;
                ly = y;
            }
        }
        float r = Math.max(1f, s * 0.16f);
        for (int k = 0; k < 7; k++) {
            float px = cx, py = cy;
            if (k > 0) {
                float a = (k - 1) * 1.0472f + 0.4f;
                px += (float) Math.cos(a) * r * 1.15f;
                py += (float) Math.sin(a) * r * 1.05f;
            }
            f.discAA(px, py, r, k % 2 == 0 ? 0xFFD8503A : 0xFFE8E2D0, 1f);
        }
    }

    private static void amphora(Px f, float cx, float cy, float s) {
        f.ellipseAA(cx, cy + s * 0.15f, s * 0.55f, s * 0.7f, 0xFFD8783A, 1f);
        f.ellipseAA(cx, cy + s * 0.85f, s * 0.2f, s * 0.12f, 0xFFB85A28, 1f);
        f.lineAA(cx, cy - s * 0.4f, cx, cy - s * 0.85f, Math.max(1.2f, s * 0.36f), 0xFFD8783A, 1f);
        f.lineAA(cx - s * 0.22f, cy - s * 0.88f, cx + s * 0.22f, cy - s * 0.88f, Math.max(1f, s * 0.12f), 0xFF1A1210, 1f);
        f.ringAA(cx - s * 0.42f, cy - s * 0.42f, s * 0.18f, s * 0.28f, 1f, 0xFF1A1210, 1f);
        f.ringAA(cx + s * 0.42f, cy - s * 0.42f, s * 0.18f, s * 0.28f, 1f, 0xFF1A1210, 1f);
        // a black band with two figures
        f.ellipseAA(cx, cy + s * 0.1f, s * 0.53f, s * 0.22f, 0xFF1A1210, 1f);
        f.discAA(cx - s * 0.18f, cy + s * 0.04f, Math.max(0.8f, s * 0.07f), 0xFFD8783A, 1f);
        f.discAA(cx + s * 0.18f, cy + s * 0.04f, Math.max(0.8f, s * 0.07f), 0xFFD8783A, 1f);
        f.lineAA(cx - s * 0.3f, cy - s * 0.25f, cx - s * 0.1f, cy + s * 0.5f, 0.8f, 0xFFF0A060, 0.5f);
    }

    private static void pistol(Px f, float cx, float cy, float s) {
        f.ellipseAA(cx, cy + s * 0.35f, s * 0.9f, s * 0.25f, 0xFF8A1A24, 1f);       // the velvet cushion
        f.ellipseAA(cx - s * 0.2f, cy + s * 0.3f, s * 0.5f, s * 0.1f, 0xFFC84050, 0.5f);
        f.lineAA(cx - s * 0.75f, cy - s * 0.05f, cx + s * 0.2f, cy - s * 0.05f, Math.max(1.2f, s * 0.14f), 0xFFC8D0D8, 1f);
        f.lineAA(cx + s * 0.1f, cy - s * 0.02f, cx + s * 0.6f, cy + s * 0.3f, Math.max(1.6f, s * 0.26f), 0xFF5A3418, 1f);
        f.discAA(cx + s * 0.62f, cy + s * 0.32f, Math.max(1f, s * 0.12f), 0xFFD8DEE6, 1f);
        f.lineAA(cx + s * 0.05f, cy + s * 0.04f, cx + s * 0.12f, cy + s * 0.2f, 1f, 0xFFC8D0D8, 1f);
    }

    /** A glass cylinder of vapour, two tracks curling opposite ways inside it. */
    private static void chamber(Px f, float cx, float cy, float s) {
        f.ellipseAA(cx, cy, s * 0.7f, s * 0.8f, 0xFF1A2430, 0.9f);
        for (int side = -1; side <= 1; side += 2) {
            float lx = cx, ly = cy + s * 0.6f;
            for (int i = 1; i <= 24; i++) {
                float a = i / 24f * 7f, r = s * 0.75f * (1f - i / 30f);
                float x = cx + side * (r - r * (float) Math.cos(a)) * 0.7f, y = cy + s * 0.6f - r * (float) Math.sin(a) - i * s * 0.02f;
                f.lineAA(lx, ly, x, y, 0.8f, side < 0 ? 0xFFEFF6FF : 0xFFFFB0C8, 0.9f);
                lx = x;
                ly = y;
            }
        }
        f.ringAA(cx, cy - s * 0.8f, s * 0.7f, s * 0.18f, 1f, 0xFFB8C8D8, 0.8f);
        f.ellipseAA(cx, cy + s * 0.85f, s * 0.75f, s * 0.2f, 0xFF5A626E, 1f);
    }

    /** A dark plate with two bright slits, and the fringes they make on a screen behind. */
    private static void slits(Px f, float cx, float cy, float s) {
        f.ellipseAA(cx, cy - s * 0.1f, s * 0.95f, s * 0.6f, 0xFF14161C, 1f);
        for (int k = -3; k <= 3; k++) {
            float op = 0.9f * (float) Math.exp(-k * k / 6f);
            f.lineAA(cx + k * s * 0.24f, cy - s * 0.55f, cx + k * s * 0.24f, cy + s * 0.3f, Math.max(1f, s * 0.1f), 0xFFFF6A7A, op);
        }
        f.lineAA(cx - s * 0.7f, cy + s * 0.55f, cx + s * 0.7f, cy + s * 0.55f, Math.max(1.6f, s * 0.3f), 0xFF2A2E36, 1f);
        f.lineAA(cx - s * 0.25f, cy + s * 0.45f, cx - s * 0.25f, cy + s * 0.65f, 1f, 0xFFFFE8C8, 1f);
        f.lineAA(cx + s * 0.25f, cy + s * 0.45f, cx + s * 0.25f, cy + s * 0.65f, 1f, 0xFFFFE8C8, 1f);
    }

    /** A plate camera: a wooden box, black bellows in folds, a brass lens. */
    private static void camera(Px f, float cx, float cy, float s) {
        float bw = Math.max(1.6f, s * 0.7f);
        f.lineAA(cx + s * 0.35f, cy - s * 0.4f, cx + s * 0.35f, cy + s * 0.45f, bw, 0xFF8A5A30, 1f);
        f.lineAA(cx + s * 0.35f, cy - s * 0.4f, cx + s * 0.35f, cy - s * 0.3f, bw, 0xFFB07A48, 1f);
        for (int i = 0; i < 5; i++) {
            float x = cx + s * (0.05f - i * 0.12f), hgt = s * (0.36f - i * 0.04f);
            f.lineAA(x, cy - hgt, x, cy + hgt, Math.max(1f, s * 0.13f), i % 2 == 0 ? 0xFF1A1614 : 0xFF2E2824, 1f);
        }
        f.discAA(cx - s * 0.62f, cy, Math.max(1.2f, s * 0.2f), 0xFFC8A040, 1f);
        f.discAA(cx - s * 0.62f, cy, Math.max(0.8f, s * 0.12f), 0xFF101418, 1f);
        f.discAA(cx - s * 0.66f, cy - s * 0.04f, Math.max(0.5f, s * 0.04f), 0xFFE8F0FF, 0.8f);
        f.ellipseAA(cx, cy + s * 0.6f, s * 0.8f, s * 0.08f, 0xFF5A3A20, 1f);
    }
}
