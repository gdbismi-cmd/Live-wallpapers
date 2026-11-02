package com.livewallpapers.aquarium.scene;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

import java.util.Arrays;
import java.util.Random;

/** A rosette of broad, arching lance-shaped leaves (Amazon sword style). */
final class SwordPlant extends Decor {
    private static final int SEG = 14;

    private final AquariumScene s;
    private final float x;
    private final int count;
    private final float[] angle, len, width, bend, phase;
    private final Paint[] paints;
    private final Paint rib = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stalk = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path blade = new Path();
    private final Path line = new Path();
    private final float[] cx = new float[SEG + 1];
    private final float[] cy = new float[SEG + 1];

    SwordPlant(AquariumScene scene, float x, float baseY, float height, float fog) {
        s = scene;
        this.x = x;
        this.baseY = baseY;
        Random r = new Random(scene.rnd.nextLong());
        float u = scene.u;
        count = 8 + r.nextInt(5);
        angle = new float[count];
        len = new float[count];
        width = new float[count];
        bend = new float[count];
        phase = new float[count];
        paints = new Paint[count];
        boolean fg = baseY > scene.height;
        left = x - height * 1.3f;
        right = x + height * 1.3f;

        // Sort leaves so the ones leaning far out (seen more side-on, further back) are drawn first.
        float[] raw = new float[count];
        for (int i = 0; i < count; i++) raw[i] = Mathx.range(r, -1.15f, 1.15f);
        Arrays.sort(raw);
        for (int i = 0; i < count; i++) {
            int idx = (i % 2 == 0) ? i / 2 : count - 1 - i / 2;
            angle[i] = raw[idx];
        }
        for (int i = 0; i < count; i++) {
            float spread = Math.abs(angle[i]);
            len[i] = height * Mathx.range(r, 0.7f, 1f) * (1.1f - spread * 0.35f);
            width[i] = len[i] * Mathx.range(r, 0.1f, 0.14f);
            bend[i] = Math.signum(angle[i]) * Mathx.range(r, 0.4f, 0.9f);
            phase[i] = Mathx.range(r, 0f, Mathx.TAU);
            float depthShade = i < count / 2 ? 0.75f : 1f;
            float sh = Mathx.range(r, 0.85f, 1.1f) * depthShade * (fg ? 0.55f : 1f);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            float tipX = x + Mathx.sin(angle[i]) * len[i];
            float tipY = baseY - Mathx.cos(angle[i]) * len[i];
            p.setShader(new LinearGradient(x, baseY, tipX, tipY,
                    new int[] {
                            Mathx.mix(Mathx.shade(0xFF2A5A22, sh), AquariumScene.FOG, fog),
                            Mathx.mix(Mathx.shade(0xFF3F8A30, sh), AquariumScene.FOG, fog),
                            Mathx.mix(Mathx.shade(0xFF6AAE45, sh), AquariumScene.FOG, fog)},
                    new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            paints[i] = p;
        }
        rib.setStyle(Paint.Style.STROKE);
        rib.setStrokeWidth(0.9f * u * (fg ? 2f : 1f));
        rib.setColor(Mathx.withAlpha(Mathx.mix(0xFFB8E08A, AquariumScene.FOG, fog), 0.55f));
        stalk.setStyle(Paint.Style.STROKE);
        stalk.setStrokeWidth(1.8f * u * (fg ? 2f : 1f));
        stalk.setColor(Mathx.mix(fg ? 0xFF1E3A16 : 0xFF2E5A22, AquariumScene.FOG, fog));
    }

    @Override
    void draw(Canvas c, float time) {
        for (int i = 0; i < count; i++) {
            float sway = 0.04f * Mathx.sin(time * 0.7f + phase[i]) + 0.03f * Mathx.sin(time * 0.29f + x * 0.002f);
            float seg = len[i] / SEG;
            float a = -Mathx.PI / 2f + angle[i] * 0.55f;
            float px = x, py = baseY;
            for (int k = 0; k <= SEG; k++) {
                cx[k] = px;
                cy[k] = py;
                float t = k / (float) SEG;
                a += (bend[i] * 0.9f / SEG) * (0.4f + t) + sway * t * 0.4f;
                px += Mathx.cos(a) * seg;
                py += Mathx.sin(a) * seg;
            }
            // Stalk: the first 30% of the leaf.
            int bladeStart = (int) (SEG * 0.3f);
            line.reset();
            line.moveTo(cx[0], cy[0]);
            for (int k = 1; k <= bladeStart; k++) line.lineTo(cx[k], cy[k]);
            c.drawPath(line, stalk);

            blade.reset();
            int n = SEG - bladeStart;
            for (int k = bladeStart; k <= SEG; k++) {
                float t = (k - bladeStart) / (float) n;
                float w = width[i] * Mathx.pow(Mathx.sin(Mathx.PI * Mathx.pow(t, 0.75f)), 0.8f);
                float nx = normalX(k), ny = normalY(k);
                if (k == bladeStart) blade.moveTo(cx[k] + nx * w, cy[k] + ny * w);
                else blade.lineTo(cx[k] + nx * w, cy[k] + ny * w);
            }
            for (int k = SEG; k >= bladeStart; k--) {
                float t = (k - bladeStart) / (float) n;
                float w = width[i] * Mathx.pow(Mathx.sin(Mathx.PI * Mathx.pow(t, 0.75f)), 0.8f) * 0.85f;
                blade.lineTo(cx[k] - normalX(k) * w, cy[k] - normalY(k) * w);
            }
            blade.close();
            c.drawPath(blade, paints[i]);

            line.reset();
            line.moveTo(cx[bladeStart], cy[bladeStart]);
            for (int k = bladeStart + 1; k < SEG; k++) line.lineTo(cx[k], cy[k]);
            c.drawPath(line, rib);
        }
    }

    private float normalX(int k) {
        int a = Math.max(0, k - 1), b = Math.min(SEG, k + 1);
        float dx = cx[b] - cx[a], dy = cy[b] - cy[a];
        float l = (float) Math.hypot(dx, dy) + 1e-4f;
        return -dy / l;
    }

    private float normalY(int k) {
        int a = Math.max(0, k - 1), b = Math.min(SEG, k + 1);
        float dx = cx[b] - cx[a], dy = cy[b] - cy[a];
        float l = (float) Math.hypot(dx, dy) + 1e-4f;
        return dx / l;
    }
}
