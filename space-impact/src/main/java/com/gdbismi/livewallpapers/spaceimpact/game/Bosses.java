package com.gdbismi.livewallpapers.spaceimpact.game;

import java.util.Random;

/**
 * Generates a different boss every time: a hull mirrored top to bottom, outlined, with a body
 * pattern, an eye and guns at the nose. Bosses face left, towards the player.
 */
final class Bosses {
    private Bosses() {}

    static final int MIN_W = 16, MAX_W = 22, MIN_H = 11, MAX_H = 17;

    private static final int GUN = 3; // columns reserved at the nose for gun barrels

    /** Two animation frames: the eye glances at the player and the guns recoil. */
    static Sprite[] create(Random rng) {
        int bodyW = MIN_W - GUN + rng.nextInt(MAX_W - MIN_W + 1);
        int w = GUN + bodyW;
        int half = (MIN_H - 1) / 2 + rng.nextInt((MAX_H - MIN_H) / 2 + 1);
        int h = half * 2 + 1;

        boolean[][] hull = new boolean[h][w];
        int shape = rng.nextInt(4);
        float waist = 0.45f + rng.nextFloat() * 0.2f;
        for (int x = 0; x < bodyW; x++) {
            float u = x / (bodyW - 1f); // 0 at the nose, 1 at the tail
            float reach;
            switch (shape) {
                case 0: // wedge
                    reach = 1.5f + u * (half - 1.5f);
                    break;
                case 1: // diamond
                    reach = u < waist ? 1.5f + u / waist * (half - 1.5f)
                            : half - (u - waist) / (1 - waist) * half * 0.45f;
                    break;
                case 2: // oval
                    float c = 2 * u - 1;
                    reach = Math.max(1.5f, half * (float) Math.sqrt(1 - c * c) + 0.5f);
                    break;
                default: // pincers, notched below
                    reach = half * (0.55f + 0.45f * u);
                    break;
            }
            for (int dy = 0; dy <= half && dy <= reach; dy++) {
                setPair(hull, half, dy, GUN + x, true);
            }
        }
        if (shape == 3) {
            int depth = bodyW / 3;
            int gap = Math.max(1, half / 3);
            for (int x = 0; x < depth; x++) {
                for (int dy = 0; dy <= gap; dy++) setPair(hull, half, dy, GUN + x, false);
            }
        }
        if (rng.nextBoolean()) { // tail fins
            for (int x = bodyW - 2; x < bodyW; x++) {
                for (int dy = 0; dy <= half; dy++) setPair(hull, half, dy, GUN + x, true);
            }
        }
        for (int x = 1; x < bodyW - 2; x++) { // nibble the edge for a less regular silhouette
            if (rng.nextInt(4) != 0) continue;
            for (int dy = half; dy >= 2; dy--) {
                if (hull[half - dy][GUN + x]) {
                    setPair(hull, half, dy, GUN + x, false);
                    break;
                }
            }
        }

        boolean[][] base = new boolean[h][w];
        int armor = 1 + rng.nextInt(2); // outline thickness
        int pattern = rng.nextInt(5);
        for (int y = 0; y < h; y++) {
            int dy = Math.abs(y - half);
            for (int x = 0; x < w; x++) {
                if (!hull[y][x]) continue;
                int depth = edgeDistance(hull, x, y);
                if (depth <= armor) {
                    base[y][x] = true;
                    continue;
                }
                switch (pattern) {
                    case 1: base[y][x] = dy % 3 == 0; break; // stripes
                    case 2: base[y][x] = dy % 2 == 0 && (x + dy) % 3 == 0; break; // rivets
                    case 3: base[y][x] = depth == armor + 2; break; // inner ring
                    case 4: base[y][x] = depth >= armor + 2; break; // solid core
                    default: break; // hollow
                }
            }
        }

        // The eye sits where the hull is tall enough for it, somewhere mid-body.
        int eyeX = GUN + bodyW / 2;
        int from = GUN + (int) (bodyW * 0.25f), to = GUN + (int) (bodyW * 0.7f);
        int start = from + rng.nextInt(Math.max(1, to - from));
        for (int i = 0; i < to - from; i++) {
            int x = from + (start - from + i) % (to - from);
            if (x - 3 >= GUN && x + 3 < w && hull[half - 3][x - 3] && hull[half - 3][x + 3]) {
                eyeX = x;
                break;
            }
        }
        for (int y = half - 3; y <= half + 3; y++) { // clear around the eye, keeping the armor
            for (int x = eyeX - 3; x <= eyeX + 3; x++) {
                if (hull[y][x] && edgeDistance(hull, x, y) > armor) base[y][x] = false;
            }
        }
        boolean diamondEye = rng.nextBoolean();
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                boolean ring = diamondEye ? Math.abs(dx) + Math.abs(dy) == 2
                        : Math.max(Math.abs(dx), Math.abs(dy)) == 2;
                if (ring) base[half + dy][eyeX + dx] = true;
            }
        }

        int[] gunRows = rng.nextBoolean() || shape == 3
                ? new int[] {half - 1 - rng.nextInt(Math.max(1, half - 3)), -1}
                : new int[] {half, -1};
        if (gunRows[0] != half) gunRows[1] = 2 * half - gunRows[0]; // mirrored pair

        Sprite[] frames = new Sprite[2];
        for (int f = 0; f < 2; f++) {
            boolean[][] px = new boolean[h][];
            for (int y = 0; y < h; y++) px[y] = base[y].clone();
            px[half][f == 0 ? eyeX : eyeX - 1] = true; // pupil looks ahead on frame 1
            for (int row : gunRows) {
                if (row < 0) continue;
                int lx = GUN;
                while (lx < w && !hull[row][lx]) lx++;
                if (lx == w) continue;
                for (int x = lx - (f == 0 ? 3 : 2); x < lx; x++) px[row][x] = true;
            }
            frames[f] = toSprite(px);
        }
        return frames;
    }

    private static void setPair(boolean[][] grid, int half, int dy, int x, boolean value) {
        grid[half - dy][x] = value;
        grid[half + dy][x] = value;
    }

    /** 1 for a hull pixel touching the outside, 2 one pixel further in, and so on (max 4). */
    private static int edgeDistance(boolean[][] hull, int x, int y) {
        for (int d = 1; d < 4; d++) {
            for (int dy = -d; dy <= d; dy++) {
                for (int dx = -d; dx <= d; dx++) {
                    if (Math.abs(dx) + Math.abs(dy) != d) continue;
                    int nx = x + dx, ny = y + dy;
                    if (ny < 0 || ny >= hull.length || nx < 0 || nx >= hull[0].length
                            || !hull[ny][nx]) {
                        return d;
                    }
                }
            }
        }
        return 4;
    }

    private static Sprite toSprite(boolean[][] px) {
        String[] rows = new String[px.length];
        for (int y = 0; y < px.length; y++) {
            StringBuilder sb = new StringBuilder(px[y].length);
            for (boolean on : px[y]) sb.append(on ? '#' : '.');
            rows[y] = sb.toString();
        }
        return new Sprite(rows);
    }
}
