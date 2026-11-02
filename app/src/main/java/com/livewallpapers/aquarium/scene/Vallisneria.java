package com.livewallpapers.aquarium.scene;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

import java.util.Random;

/** A clump of long, twisting ribbon leaves (eelgrass) that sway in the current. */
final class Vallisneria extends Decor {
    private static final int SEG = 18;

    private final AquariumScene s;
    private final float x;
    private final int leaves;
    private final float[] len, width, lean, curve, phase, twist, amp, dx;
    private final Paint[] paints;
    private final Path path = new Path();
    private final float[] lx = new float[SEG + 1];
    private final float[] ly = new float[SEG + 1];
    private final float[] rx = new float[SEG + 1];
    private final float[] ry = new float[SEG + 1];

    /**
     * @param fog     0..1 how much the clump fades into the water colour
     * @param variant 0 = green, 1 = red-tinted
     */
    Vallisneria(AquariumScene scene, float x, float baseY, float height, int leaves, float fog, int variant) {
        s = scene;
        this.x = x;
        this.baseY = baseY;
        this.leaves = leaves;
        Random r = new Random(scene.rnd.nextLong());
        len = new float[leaves];
        width = new float[leaves];
        lean = new float[leaves];
        curve = new float[leaves];
        phase = new float[leaves];
        twist = new float[leaves];
        amp = new float[leaves];
        dx = new float[leaves];
        paints = new Paint[leaves];

        float maxLen = baseY - scene.surfaceY - 30f * scene.u;
        float u = scene.u;
        boolean big = baseY > scene.height;
        left = x - height * 0.6f;
        right = x + height * 0.6f;
        for (int i = 0; i < leaves; i++) {
            len[i] = Math.min(maxLen, height * Mathx.range(r, 0.55f, 1f));
            width[i] = Mathx.range(r, 2.6f, 4.2f) * u * (big ? 2.3f : 1f);
            lean[i] = Mathx.range(r, -0.28f, 0.28f) * (big ? 0.45f : 1f);
            curve[i] = Mathx.range(r, -0.5f, 0.5f) * (big ? 0.35f : 1f);
            phase[i] = Mathx.range(r, 0f, Mathx.TAU);
            twist[i] = Mathx.range(r, 0f, Mathx.TAU);
            amp[i] = Mathx.range(r, 0.05f, 0.12f);
            dx[i] = Mathx.range(r, -6f, 6f) * u * (big ? 2.5f : 1f);

            int baseCol = variant == 1 ? 0xFF3B4A22 : 0xFF1F4A1C;
            int midCol = variant == 1 ? 0xFF6E6A2C : 0xFF3A7A2A;
            int tipCol = variant == 1 ? 0xFFA0603A : 0xFF7DAE4B;
            float shadeF = Mathx.range(r, 0.78f, 1.08f) * (big ? 0.62f : 1f);
            int[] cols = {baseCol, midCol, tipCol};
            for (int k = 0; k < 3; k++) {
                cols[k] = Mathx.mix(Mathx.shade(cols[k], shadeF), AquariumScene.FOG, fog);
            }
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new LinearGradient(0, baseY, 0, baseY - len[i], cols,
                    new float[] {0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
            paints[i] = p;
        }
    }

    @Override
    void draw(Canvas c, float time) {
        float current = 0.08f * Mathx.sin(time * 0.31f + x * 0.0021f) + 0.04f * Mathx.sin(time * 0.17f);
        for (int i = 0; i < leaves; i++) {
            float seg = len[i] / SEG;
            float px = x + dx[i];
            float py = baseY;
            float a = -Mathx.PI / 2f + lean[i];
            for (int k = 0; k <= SEG; k++) {
                float t = k / (float) SEG;
                float w = width[i] * (k >= SEG ? 0f : (t > 0.82f ? Mathx.sqrt((1f - t) / 0.18f) : 1f));
                w *= 0.35f + 0.65f * Math.abs(Mathx.cos(twist[i] + t * 5f + time * 0.2f));
                float nx = -Mathx.sin(a), ny = Mathx.cos(a);
                lx[k] = px + nx * w;
                ly[k] = py + ny * w;
                rx[k] = px - nx * w;
                ry[k] = py - ny * w;
                float sway = amp[i] * Mathx.sin(time * 0.9f + phase[i] - t * 3.2f);
                a += (curve[i] / SEG) * 1.2f + (sway + current) * t * 0.55f;
                px += Mathx.cos(a) * seg;
                py += Mathx.sin(a) * seg;
            }
            path.reset();
            path.moveTo(lx[0], ly[0]);
            for (int k = 1; k <= SEG; k++) path.lineTo(lx[k], ly[k]);
            for (int k = SEG; k >= 0; k--) path.lineTo(rx[k], ry[k]);
            path.close();
            c.drawPath(path, paints[i]);
        }
    }
}
