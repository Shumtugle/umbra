package com.shumtugle.umbra;

/**
 * How a paddle looks in a realm: the face, the edge round it, the handle, and an
 * optional glow at the rim. Each realm dresses both paddles in its own time and
 * material; the player's face stays warm and the opponent's cool, so they are
 * never confused.
 */
final class Racket {
    final int face, faceLight, faceDark;
    final int edgeDark, edgeLight;
    final int handle, handleLight, handleEnd;
    final int glow;            // 0 for none
    final float grain;         // how rough the face looks, 0 smooth .. 1 coarse

    Racket(int face, int faceLight, int faceDark, int edgeDark, int edgeLight, int handle, int handleLight, int handleEnd,
           int glow, float grain) {
        this.face = face;
        this.faceLight = faceLight;
        this.faceDark = faceDark;
        this.edgeDark = edgeDark;
        this.edgeLight = edgeLight;
        this.handle = handle;
        this.handleLight = handleLight;
        this.handleEnd = handleEnd;
        this.glow = glow;
        this.grain = grain;
    }

    /** Red rubber and violet rubber on plain wood: the table tennis of today. */
    static final Racket PLAYER = new Racket(0xFFD8352B, 0xFFFF8E7A, 0xFF7A120E, 0xFF8A5A30, 0xFFE2B47C,
            0xFFA8733E, 0xFFE0B07A, 0xFF7A4E28, 0, 0.08f);
    static final Racket OPPONENT = new Racket(0xFF3C2E7A, 0xFF8C7CE0, 0xFF140E30, 0xFF8A5A30, 0xFFE2B47C,
            0xFFA8733E, 0xFFE0B07A, 0xFF7A4E28, 0xFF7FE8FF, 0.08f);
}
