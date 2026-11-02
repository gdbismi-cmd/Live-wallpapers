package com.livewallpapers.aquarium.scene;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

import java.util.Random;

/** A bunch of upright stems with opposite pairs of small leaves (rotala / ludwigia style). */
final class StemPlant extends Decor {
    private final AquariumScene s;
    private final int stems;
    private final float[] sx, sLen, sLean, sPhase;
    private final int nodes;
    private final Paint[] leafPaints;
    private final Paint stemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path leafPath = new Path();
    private final Path stemPath = new Path();
    private final float leafLen, leafW, nodeGap;

    StemPlant(AquariumScene scene, float x, float baseY, float height, int stems, float fog, boolean red) {
        s = scene;
        this.baseY = baseY;
        this.stems = stems;
        Random r = new Random(scene.rnd.nextLong());
        float u = scene.u;
        sx = new float[stems];
        sLen = new float[stems];
        sLean = new float[stems];
        sPhase = new float[stems];
        leafPaints = new Paint[stems];
        float maxLen = baseY - scene.surfaceY - 40f * u;
        left = x - height * 0.4f - 30f * u;
        right = x + height * 0.4f + 30f * u;
        leafLen = Mathx.range(r, 12f, 17f) * u;
        leafW = leafLen * Mathx.range(r, 0.22f, 0.3f);
        nodeGap = leafLen * 0.5f;
        nodes = (int) (Math.min(maxLen, height) / nodeGap);

        int bottom = red ? 0xFF35562A : 0xFF2C5A22;
        int middle = red ? 0xFF6E6A36 : 0xFF467E2C;
        int topCol = red ? 0xFFC0604E : 0xFF84B550;
        for (int i = 0; i < stems; i++) {
            sx[i] = x + Mathx.range(r, -12f, 12f) * u;
            sLen[i] = Math.min(maxLen, height * Mathx.range(r, 0.65f, 1f));
            sLean[i] = Mathx.range(r, -0.18f, 0.18f) + (sx[i] - x) / (60f * u);
            sPhase[i] = Mathx.range(r, 0f, Mathx.TAU);
            float shadeF = Mathx.range(r, 0.85f, 1.1f);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new LinearGradient(0, baseY, 0, baseY - sLen[i],
                    new int[] {
                            Mathx.mix(Mathx.shade(bottom, shadeF), AquariumScene.FOG, fog),
                            Mathx.mix(Mathx.shade(middle, shadeF), AquariumScene.FOG, fog),
                            Mathx.mix(Mathx.shade(topCol, shadeF), AquariumScene.FOG, fog * 0.8f)},
                    new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            leafPaints[i] = p;
        }
        stemPaint.setStyle(Paint.Style.STROKE);
        stemPaint.setStrokeWidth(1.3f * u);
        stemPaint.setColor(Mathx.mix(red ? 0xFF6A4A30 : 0xFF3D6A2A, AquariumScene.FOG, fog));
    }

    @Override
    void draw(Canvas c, float time) {
        for (int i = 0; i < stems; i++) {
            int n = Math.max(3, (int) (sLen[i] / nodeGap));
            float px = sx[i], py = baseY;
            float a = -Mathx.PI / 2f + sLean[i] * 0.4f;
            stemPath.reset();
            leafPath.reset();
            stemPath.moveTo(px, py);
            for (int k = 0; k < n; k++) {
                float t = k / (float) n;
                float sway = 0.035f * Mathx.sin(time * 0.8f + sPhase[i] - t * 2.5f)
                        + 0.03f * Mathx.sin(time * 0.31f + sx[i] * 0.003f);
                a += (sLean[i] * 0.6f / n) + sway * t * 0.6f;
                px += Mathx.cos(a) * nodeGap;
                py += Mathx.sin(a) * nodeGap;
                stemPath.lineTo(px, py);
                if (k < 1) continue;
                // Leaves shrink a little towards the growing tip.
                float size = t > 0.85f ? Mathx.lerp(1f, 0.55f, (t - 0.85f) / 0.15f) : 1f;
                float ll = leafLen * size;
                float lw = leafW * size;
                float spreadA = Mathx.lerp(1.15f, 0.5f, t);
                // Leaf pairs rotate around the stem (decussate), so their apparent length varies.
                float rot = k * 1.9f + sPhase[i];
                float fl = 0.45f + 0.55f * Math.abs(Mathx.cos(rot));
                float fr = 0.45f + 0.55f * Math.abs(Mathx.sin(rot));
                float jitter = 0.15f * Mathx.sin(k * 3.7f + i);
                addLeaf(px, py, a - spreadA * (0.7f + 0.3f * fl) + jitter, ll * fl, lw);
                addLeaf(px, py, a + spreadA * (0.7f + 0.3f * fr) + jitter, ll * fr, lw);
            }
            c.drawPath(stemPath, stemPaint);
            c.drawPath(leafPath, leafPaints[i]);
        }
    }

    private void addLeaf(float x, float y, float angle, float len, float w) {
        float dx = Mathx.cos(angle), dy = Mathx.sin(angle);
        float nx = -dy, ny = dx;
        float tx = x + dx * len, ty = y + dy * len;
        float mx = x + dx * len * 0.45f, my = y + dy * len * 0.45f;
        leafPath.moveTo(x, y);
        leafPath.quadTo(mx + nx * w * 1.6f, my + ny * w * 1.6f, tx, ty);
        leafPath.quadTo(mx - nx * w * 1.6f, my - ny * w * 1.6f, x, y);
        leafPath.close();
    }
}
