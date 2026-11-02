package com.livewallpapers.aquarium.scene;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/** A gnarled piece of driftwood, pre-rendered into a bitmap. */
final class Driftwood extends Decor {
    private static final int SAMPLES = 40;

    private Bitmap bitmap;
    private final float top;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final float u;
    private final Random r;

    Driftwood(AquariumScene scene, float cx, float baseY, int seed) {
        this.baseY = baseY;
        u = scene.u;
        r = new Random(seed);
        float dir = r.nextBoolean() ? 1f : -1f;
        int bw = (int) (300 * u);
        int bh = (int) (230 * u);
        left = cx - bw / 2f;
        right = left + bw;
        top = baseY - bh + 20 * u;
        bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        float ox = bw / 2f, oy = baseY - top;

        // Shadow on the sand.
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(ox, oy, 120 * u, new int[] {0x60000000, 0x00000000}, null, Shader.TileMode.CLAMP));
        c.save();
        c.scale(1f, 0.15f, ox, oy);
        c.drawCircle(ox, oy, 120 * u, p);
        c.restore();

        // Main trunk lying diagonally, with two branches reaching up.
        branch(c, ox - dir * 120 * u, oy - 4 * u, ox - dir * 40 * u, oy - 10 * u,
                ox + dir * 10 * u, oy - 60 * u, ox + dir * 60 * u, oy - 150 * u, 19 * u, 5 * u);
        branch(c, ox - dir * 30 * u, oy - 16 * u, ox + dir * 40 * u, oy - 20 * u,
                ox + dir * 90 * u, oy - 40 * u, ox + dir * 130 * u, oy - 95 * u, 13 * u, 3 * u);
        branch(c, ox + dir * 5 * u, oy - 62 * u, ox - dir * 10 * u, oy - 100 * u,
                ox - dir * 25 * u, oy - 140 * u, ox - dir * 15 * u, oy - 190 * u, 9f * u, 2.5f * u);
        branch(c, ox + dir * 45 * u, oy - 120 * u, ox + dir * 70 * u, oy - 140 * u,
                ox + dir * 80 * u, oy - 150 * u, ox + dir * 105 * u, oy - 165 * u, 6f * u, 1.5f * u);

        // Fog it slightly so it sits in the water.
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int[] px = new int[w * h];
        bitmap.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            int a = px[i] >>> 24;
            if (a == 0) continue;
            px[i] = Mathx.withAlpha(Mathx.mix(px[i] | 0xFF000000, AquariumScene.FOG, 0.12f), a);
        }
        bitmap.setPixels(px, 0, w, 0, 0, w, h);
    }

    private void branch(Canvas c, float x0, float y0, float x1, float y1, float x2, float y2,
                        float x3, float y3, float w0, float w1) {
        float[] bx = new float[SAMPLES + 1];
        float[] by = new float[SAMPLES + 1];
        float[] bw = new float[SAMPLES + 1];
        int seed = r.nextInt(1000);
        for (int i = 0; i <= SAMPLES; i++) {
            float t = i / (float) SAMPLES;
            float it = 1f - t;
            bx[i] = it * it * it * x0 + 3 * it * it * t * x1 + 3 * it * t * t * x2 + t * t * t * x3;
            by[i] = it * it * it * y0 + 3 * it * it * t * y1 + 3 * it * t * t * y2 + t * t * t * y3;
            bw[i] = Mathx.lerp(w0, w1, Mathx.pow(t, 0.8f)) * (0.85f + 0.3f * Mathx.noise(t * 8f, 0.3f, seed));
        }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // Layers: dark body, lit side, highlight. Light from the upper left.
        int[] cols = {0xFF2E2117, 0xFF5A4230, 0xFF8A6E52, 0xFFB09272};
        float[] widths = {1f, 0.78f, 0.45f, 0.18f};
        float[] shifts = {0f, -0.22f, -0.42f, -0.6f};
        Path path = new Path();
        for (int layer = 0; layer < cols.length; layer++) {
            path.reset();
            buildOutline(path, bx, by, bw, widths[layer], shifts[layer]);
            p.setColor(cols[layer]);
            if (layer == 3) p.setAlpha(110);
            c.drawPath(path, p);
        }
        // Grain lines.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(0.7f * u);
        for (int g = 0; g < 7; g++) {
            float off = Mathx.range(r, -0.85f, 0.85f);
            p.setColor(g % 2 == 0 ? 0x701A120C : 0x40C8A888);
            path.reset();
            int start = r.nextInt(SAMPLES / 3);
            int end = Math.min(SAMPLES, start + SAMPLES / 2 + r.nextInt(SAMPLES / 2));
            for (int i = start; i <= end; i++) {
                float nx = normal(bx, by, i, true), ny = normal(bx, by, i, false);
                float o = (off + 0.15f * Mathx.sin(i * 0.9f + g)) * bw[i];
                if (i == start) path.moveTo(bx[i] + nx * o, by[i] + ny * o);
                else path.lineTo(bx[i] + nx * o, by[i] + ny * o);
            }
            c.drawPath(path, p);
        }
        // A couple of knots.
        p.setStyle(Paint.Style.FILL);
        for (int k = 0; k < 2; k++) {
            int i = 5 + r.nextInt(SAMPLES - 10);
            float rr = bw[i] * 0.45f;
            p.setColor(0xFF22180F);
            c.drawOval(bx[i] - rr * 1.3f, by[i] - rr * 0.8f, bx[i] + rr * 1.3f, by[i] + rr * 0.8f, p);
            p.setColor(0x806E5640);
            c.drawOval(bx[i] - rr * 0.8f, by[i] - rr * 0.6f, bx[i] + rr * 0.6f, by[i] + rr * 0.2f, p);
        }
    }

    private static void buildOutline(Path path, float[] bx, float[] by, float[] bw, float scale, float shift) {
        int n = bx.length;
        for (int i = 0; i < n; i++) {
            float nx = normal(bx, by, i, true), ny = normal(bx, by, i, false);
            // Normal pointing "up" is the lit side.
            if (ny > 0) {
                nx = -nx;
                ny = -ny;
            }
            float cxp = bx[i] - nx * shift * bw[i];
            float cyp = by[i] - ny * shift * bw[i];
            float x = cxp + nx * bw[i] * scale, y = cyp + ny * bw[i] * scale;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        for (int i = n - 1; i >= 0; i--) {
            float nx = normal(bx, by, i, true), ny = normal(bx, by, i, false);
            if (ny > 0) {
                nx = -nx;
                ny = -ny;
            }
            float cxp = bx[i] - nx * shift * bw[i];
            float cyp = by[i] - ny * shift * bw[i];
            path.lineTo(cxp - nx * bw[i] * scale, cyp - ny * bw[i] * scale);
        }
        path.close();
    }

    private static float normal(float[] bx, float[] by, int i, boolean xComp) {
        int a = Math.max(0, i - 1), b = Math.min(bx.length - 1, i + 1);
        float dx = bx[b] - bx[a], dy = by[b] - by[a];
        float l = (float) Math.hypot(dx, dy) + 1e-4f;
        return xComp ? -dy / l : dx / l;
    }

    @Override
    void draw(Canvas canvas, float time) {
        canvas.drawBitmap(bitmap, left, top, paint);
    }

    @Override
    void release() {
        if (bitmap != null) bitmap.recycle();
        bitmap = null;
    }
}
