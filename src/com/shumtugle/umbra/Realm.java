package com.shumtugle.umbra;

/**
 * A world the table stands in: one thing floating over the net, the law by which
 * it moves the ball, and the look that shows that law before the ball obeys it.
 * A law the player cannot see is a trap, so every realm paints its own sign: the
 * grid on the table bends with the force, and the ball's tail draws its path.
 *
 * The thing's place is a function of time alone, and act() is the only way it
 * touches the ball, so the opponent's prediction and the game run the same law.
 * Its power grows with the level: the deeper into a realm, the stronger it is,
 * and the more the grid bends.
 */
abstract class Realm {
    float x, z = Game.NZ;
    float power = 1f;

    /** A short stable name for saved settings. */
    abstract String key();

    abstract String name();

    /** One line of what it does, for the title screen. */
    abstract String note();

    /** Height of the thing above the table top. */
    abstract float height();

    abstract float posX(float t);

    abstract float posZ(float t);

    void at(float t) {
        x = posX(t);
        z = posZ(t);
    }

    void setLevel(int level) {
        power = Math.min(1.8f, 1f + 0.08f * (level - 1));
    }

    /** Moves the ball for h seconds at time t. True when the ball is taken out of play. */
    abstract boolean act(Ball b, float t, float h);

    /** A first-game hint that names the law. */
    abstract String hint();

    /** Shown when the ball is taken. */
    String takenReason() {
        return "SWALLOWED";
    }

    /** 0 far away .. 1 at the centre: how close the ball is, for the sound and the glow. */
    float closeness(Ball b) {
        float dx = x - b.x, dy = height() - b.y, dz = z - b.z;
        float r = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        return Col.clamp01(1f - (r - 0.05f) / 0.9f);
    }

    // ---- the look

    /** Paints what lies around the table, once per screen size. */
    abstract void backdrop(Scene s, int[] out);

    /** Lets parts of the backdrop move; only pixels the table does not cover. */
    void keepVisible(int[] still) {}

    void twinkle(int[] frame, float t) {}

    /** The bare table top at a point. */
    abstract int surface(float x, float z);

    /** Colour of the painted lines. */
    abstract int lines();

    /** Colour added for the grid on the table, and how strongly. */
    abstract int grid();

    float gridGain() {
        return 1f;
    }

    /**
     * The grid's bend: a point at squared distance r2 from the thing is drawn
     * k = bendA / (r2 + bendC) of the way toward it (away when negative). With
     * |bendA| < bendC the bent grid never folds over itself.
     */
    abstract float bendA();

    abstract float bendC();

    /** A turn of the grid about the centre, in radians at the centre; 0 for none. */
    float swirl() {
        return 0f;
    }

    /** Light the thing throws on the table: colour, strength, and its glossy reflection. */
    abstract int light();

    abstract float lightK();

    abstract float gloss();

    /** The table's near face: top, bottom, and the lit bevel between them. */
    abstract int faceTop();

    abstract int faceBottom();

    abstract int bevel();

    abstract int tail();

    float tailWidth() {
        return 1.3f;
    }

    float tailAlpha() {
        return 0.42f;
    }

    abstract int netThread();

    abstract int netTape();

    abstract int ripple();

    /** The warm side of the ball faces the thing in this colour. */
    abstract int rimLight();

    /** Marks laid on the table after it is drawn, under everything else. */
    void drawOnTable(Scene s, Px f, Game g, float t) {}

    /** The thing itself, drawn at its depth among the paddles, the ball and the net. */
    abstract void drawCenter(Scene s, Px f, Game g, float t);

    /** Which hum the synthesizer plays for this realm. */
    abstract int hum();
}
