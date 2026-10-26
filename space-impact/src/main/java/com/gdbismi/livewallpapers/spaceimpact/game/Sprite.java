package com.gdbismi.livewallpapers.spaceimpact.game;

/** A 1-bit pixel-art bitmap, parsed from rows of text where '#' marks a lit pixel. */
final class Sprite {
    final int w;
    final int h;
    private final boolean[] bits;

    Sprite(String... rows) {
        int width = 0;
        for (String row : rows) {
            width = Math.max(width, row.length());
        }
        w = width;
        h = rows.length;
        bits = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            String row = rows[y];
            for (int x = 0; x < row.length(); x++) {
                bits[y * w + x] = row.charAt(x) == '#';
            }
        }
    }

    boolean get(int x, int y) {
        return bits[y * w + x];
    }
}
