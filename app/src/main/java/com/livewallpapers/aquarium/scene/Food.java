package com.livewallpapers.aquarium.scene;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.Random;

/** Fish food flakes dropped by tapping the screen. They float, then slowly sink. */
final class Food {
    private static final int MAX = 48;

    private final AquariumScene s;
    private final Random r = new Random();
    final float[] x = new float[MAX], y = new float[MAX];
    private final float[] vy = new float[MAX], rot = new float[MAX], spin = new float[MAX];
    private final float[] floatTime = new float[MAX], restTime = new float[MAX], size = new float[MAX];
    private final int[] color = new int[MAX];
    final boolean[] alive = new boolean[MAX];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int count;
    private static final int[] COLORS = {0xFFB8432E, 0xFFD9A33A, 0xFF7A8E3A, 0xFF8C5A34, 0xFFC86E3E};

    Food(AquariumScene scene) {
        s = scene;
    }

    boolean any() {
        return count > 0;
    }

    void drop(float wx, float sy) {
        int n = 6 + r.nextInt(5);
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < MAX; i++) {
                if (alive[i]) continue;
                alive[i] = true;
                count++;
                x[i] = wx + Mathx.range(r, -18f, 18f) * s.u;
                y[i] = s.surfaceY + Mathx.range(r, 1f, 4f) * s.u;
                vy[i] = 0f;
                rot[i] = r.nextFloat() * 360f;
                spin[i] = Mathx.range(r, -90f, 90f);
                floatTime[i] = Mathx.range(r, 0.5f, 2.5f);
                restTime[i] = 0f;
                size[i] = Mathx.range(r, 1.6f, 2.8f) * s.u;
                color[i] = COLORS[r.nextInt(COLORS.length)];
                break;
            }
        }
    }

    void eat(int i) {
        if (alive[i]) {
            alive[i] = false;
            count--;
        }
    }

    /** Index of the closest flake within {@code radius} of (px, py), or -1. */
    int nearest(float px, float py, float radius) {
        int best = -1;
        float bestD = radius * radius;
        for (int i = 0; i < MAX; i++) {
            if (!alive[i]) continue;
            float dx = x[i] - px, dy = y[i] - py;
            float d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    void update(float dt) {
        float u = s.u;
        for (int i = 0; i < MAX; i++) {
            if (!alive[i]) continue;
            float floor = s.sandTop(x[i]) + 8f * u;
            if (floatTime[i] > 0f) {
                floatTime[i] -= dt;
                x[i] += Mathx.sin(s.time * 0.7f + i) * 3f * u * dt;
                y[i] = s.surfaceY + 2f * u + Mathx.sin(s.time * 2f + i) * u;
            } else if (y[i] < floor) {
                vy[i] = Mathx.approach(vy[i], 13f * u, 20f * u * dt);
                y[i] += vy[i] * dt;
                x[i] += Mathx.sin(s.time * 1.6f + i * 2.1f) * 9f * u * dt;
                rot[i] += spin[i] * dt;
            } else {
                restTime[i] += dt;
                if (restTime[i] > 12f) eat(i);
            }
        }
    }

    void clear() {
        for (int i = 0; i < MAX; i++) alive[i] = false;
        count = 0;
    }

    void draw(Canvas c) {
        if (count == 0) return;
        for (int i = 0; i < MAX; i++) {
            if (!alive[i]) continue;
            float a = restTime[i] > 9f ? 1f - (restTime[i] - 9f) / 3f : 1f;
            paint.setColor(Mathx.withAlpha(color[i], a * 0.95f));
            float sz = size[i];
            c.save();
            c.translate(x[i], y[i]);
            c.rotate(rot[i]);
            c.drawOval(-sz, -sz * 0.6f, sz, sz * 0.6f, paint);
            c.restore();
        }
    }
}
