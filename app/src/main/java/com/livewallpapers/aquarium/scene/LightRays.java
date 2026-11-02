package com.livewallpapers.aquarium.scene;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

import java.util.Random;

/** Slowly breathing shafts of light coming down from the surface. */
final class LightRays {
    private static final int COUNT = 9;

    private final AquariumScene s;
    private final float[] x = new float[COUNT];
    private final float[] topW = new float[COUNT];
    private final float[] spread = new float[COUNT];
    private final float[] len = new float[COUNT];
    private final float[] speed = new float[COUNT];
    private final float[] phase = new float[COUNT];
    private final float[] strength = new float[COUNT];
    private final Paint[] paints = new Paint[COUNT];
    private final Path path = new Path();
    private final float slant;

    LightRays(AquariumScene scene) {
        s = scene;
        Random r = new Random(s.rnd.nextLong());
        float span = s.width + (s.worldW - s.width) * Background.PARALLAX;
        slant = Mathx.range(r, 0.12f, 0.22f) * (r.nextBoolean() ? 1f : -1f);
        for (int i = 0; i < COUNT; i++) {
            x[i] = span * (i + Mathx.range(r, 0.1f, 0.9f)) / COUNT - slant * s.height * 0.4f;
            topW[i] = Mathx.range(r, 8f, 34f) * s.u;
            spread[i] = Mathx.range(r, 1.6f, 3.2f);
            len[i] = Mathx.range(r, 0.55f, 0.95f) * s.height;
            speed[i] = Mathx.range(r, 0.15f, 0.45f);
            phase[i] = Mathx.range(r, 0f, Mathx.TAU);
            strength[i] = Mathx.range(r, 0.5f, 1f);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new LinearGradient(0, s.surfaceY, 0, s.surfaceY + len[i],
                    new int[] {0x70E8FFF8, 0x30C0F0EE, 0x00A0E0E0}, new float[] {0f, 0.45f, 1f},
                    Shader.TileMode.CLAMP));
            paints[i] = p;
        }
    }

    void draw(Canvas c, float offsetX, float time) {
        float top = s.surfaceY;
        for (int i = 0; i < COUNT; i++) {
            float pulse = 0.5f + 0.5f * Mathx.sin(time * speed[i] + phase[i]);
            pulse = pulse * pulse;
            int a = (int) (255 * strength[i] * (0.15f + 0.85f * pulse));
            if (a < 6) continue;
            float sway = Mathx.sin(time * speed[i] * 0.7f + phase[i] * 2f) * 10f * s.u;
            float x0 = offsetX + x[i] + sway;
            float w0 = topW[i] * (0.8f + 0.4f * pulse);
            float w1 = w0 * spread[i];
            float bx = x0 + slant * len[i];
            path.reset();
            path.moveTo(x0 - w0, top);
            path.lineTo(x0 + w0, top);
            path.lineTo(bx + w1, top + len[i]);
            path.lineTo(bx - w1, top + len[i]);
            path.close();
            paints[i].setAlpha(a);
            c.drawPath(path, paints[i]);
        }
    }
}
