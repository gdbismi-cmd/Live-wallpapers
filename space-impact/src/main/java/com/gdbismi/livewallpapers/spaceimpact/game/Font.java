package com.gdbismi.livewallpapers.spaceimpact.game;

/** A tiny 5-pixel-tall bitmap font covering A-Z, 0-9 and space. */
final class Font {
    private Font() {}

    static final int HEIGHT = 5;

    private static final Sprite SPACE = new Sprite("..", "..", "..", "..", "..");
    private static final Sprite[] LETTERS = {
            new Sprite(".#.", "#.#", "###", "#.#", "#.#"), // A
            new Sprite("##.", "#.#", "##.", "#.#", "##."), // B
            new Sprite(".##", "#..", "#..", "#..", ".##"), // C
            new Sprite("##.", "#.#", "#.#", "#.#", "##."), // D
            new Sprite("###", "#..", "##.", "#..", "###"), // E
            new Sprite("###", "#..", "##.", "#..", "#.."), // F
            new Sprite(".##", "#..", "#.#", "#.#", ".##"), // G
            new Sprite("#.#", "#.#", "###", "#.#", "#.#"), // H
            new Sprite("###", ".#.", ".#.", ".#.", "###"), // I
            new Sprite("..#", "..#", "..#", "#.#", ".#."), // J
            new Sprite("#.#", "#.#", "##.", "#.#", "#.#"), // K
            new Sprite("#..", "#..", "#..", "#..", "###"), // L
            new Sprite("#...#", "##.##", "#.#.#", "#...#", "#...#"), // M
            new Sprite("#..#", "##.#", "#.##", "#..#", "#..#"), // N
            new Sprite(".#.", "#.#", "#.#", "#.#", ".#."), // O
            new Sprite("##.", "#.#", "##.", "#..", "#.."), // P
            new Sprite(".#.", "#.#", "#.#", "##.", ".##"), // Q
            new Sprite("##.", "#.#", "##.", "#.#", "#.#"), // R
            new Sprite(".##", "#..", ".#.", "..#", "##."), // S
            new Sprite("###", ".#.", ".#.", ".#.", ".#."), // T
            new Sprite("#.#", "#.#", "#.#", "#.#", "###"), // U
            new Sprite("#.#", "#.#", "#.#", "#.#", ".#."), // V
            new Sprite("#...#", "#...#", "#.#.#", "##.##", "#...#"), // W
            new Sprite("#.#", "#.#", ".#.", "#.#", "#.#"), // X
            new Sprite("#.#", "#.#", ".#.", ".#.", ".#."), // Y
            new Sprite("###", "..#", ".#.", "#..", "###"), // Z
    };
    private static final Sprite[] DIGITS = {
            new Sprite("###", "#.#", "#.#", "#.#", "###"),
            new Sprite(".#.", "##.", ".#.", ".#.", "###"),
            new Sprite("##.", "..#", ".#.", "#..", "###"),
            new Sprite("##.", "..#", ".#.", "..#", "##."),
            new Sprite("#.#", "#.#", "###", "..#", "..#"),
            new Sprite("###", "#..", "##.", "..#", "##."),
            new Sprite(".##", "#..", "###", "#.#", "###"),
            new Sprite("###", "..#", ".#.", ".#.", ".#."),
            new Sprite("###", "#.#", "###", "#.#", "###"),
            new Sprite("###", "#.#", "###", "..#", "##."),
    };

    static Sprite glyph(char c) {
        if (c >= 'A' && c <= 'Z') return LETTERS[c - 'A'];
        if (c >= 'a' && c <= 'z') return LETTERS[c - 'a'];
        if (c >= '0' && c <= '9') return DIGITS[c - '0'];
        return SPACE;
    }

    /** Width in unscaled pixels, including the 1px gap between glyphs. */
    static int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            if (i > 0) w++;
            w += glyph(text.charAt(i)).w;
        }
        return w;
    }
}
