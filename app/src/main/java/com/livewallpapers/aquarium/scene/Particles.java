package com.livewallpapers.aquarium.scene;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.Random;

/** Tiny suspended particles drifting in the water; they make the volume of water readable. */
final class Particles {
    private static final int COUNT = 90;

    private final AquariumScene s;
    private final float[] x = new float[COUNT], y = new float[COUNT], size = new float[COUNT];
    private final float[] depth = new float[COUNT], phase = new float[COUNT];
    private final int[] alpha = new int[COUNT];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    Particles(AquariumScene scene) {
        s = scene;
        Random r = new Random(scene.rnd.nextLong());
        for (int i = 0; i < COUNT; i++) {
            x[i] = r.nextFloat() * scene.width;
            y[i] = Mathx.lerp(scene.surfaceY, scene.height, r.nextFloat());
            depth[i] = r.nextFloat();
            size[i] = Mathx.lerp(1.4f, 0.5f, depth[i]) * scene.u;
            alpha[i] = (int) Mathx.lerp(120, 40, depth[i]);
            phase[i] = r.nextFloat() * Mathx.TAU;
        }
        paint.setColor(0xFFDDEEE6);
    }

    void update(float dt) {
        float u = s.u;
        for (int i = 0; i < COUNT; i++) {
            phase[i] += dt * 0.5f;
            x[i] += (Mathx.sin(phase[i] + i) * 2.5f + 1.2f) * u * dt;
            y[i] += (Mathx.cos(phase[i] * 0.7f + i) * 2f + 1.0f) * u * dt;
            if (y[i] > s.height) y[i] = s.surfaceY;
            if (y[i] < s.surfaceY) y[i] = s.height;
        }
    }

    /** Particles live in screen space; nearer ones scroll a little more for parallax. */
    void draw(Canvas c, float camX) {
        float w = s.width;
        for (int i = 0; i < COUNT; i++) {
            float factor = Mathx.lerp(1f, 0.6f, depth[i]);
            float sx = x[i] - camX * factor;
            sx = ((sx % w) + w) % w;
            int a = (int) (alpha[i] * (0.6f + 0.4f * Mathx.sin(phase[i] * 3f + i)));
            paint.setAlpha(a);
            c.drawCircle(camX + sx, y[i], size[i], paint);
        }
    }
}
