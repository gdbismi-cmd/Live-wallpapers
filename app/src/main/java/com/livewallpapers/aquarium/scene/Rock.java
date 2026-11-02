package com.livewallpapers.aquarium.scene;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Random;

/**
 * A craggy stone half buried in the sand. The stone is rendered once, per pixel, from a noise
 * height field lit with simple Lambert shading, which looks far more natural than vector shading.
 */
final class Rock extends Decor {
    private final AquariumScene s;
    private Bitmap bitmap;
    private final float top;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

    Rock(AquariumScene scene, float cx, float baseY, float w, float h, int seed) {
        s = scene;
        this.baseY = baseY;
        Random r = new Random(seed);
        float u = scene.u;
        float margin = 0.25f * w;
        int bw = (int) Math.ceil(w + margin * 2);
        int bh = (int) Math.ceil(h * 1.3f + margin);
        left = cx - bw / 2f;
        right = left + bw;
        top = baseY - h * 1.3f - margin * 0.5f;
        float ox = bw / 2f; // centre x in bitmap space
        float oy = baseY - top; // base line in bitmap space

        // ---- silhouette ------------------------------------------------------------------
        Path path = new Path();
        int pts = 26;
        float tilt = Mathx.range(r, -0.25f, 0.25f);
        float peak = Mathx.range(r, 0.35f, 0.65f);
        for (int i = 0; i <= pts; i++) {
            float t = i / (float) pts; // 0 = left foot, 1 = right foot
            float ang = Mathx.PI * (1f - t);
            float rad = 0.82f + 0.3f * Mathx.noise(t * 5f, 1.7f, seed) + 0.08f * Mathx.noise(t * 17f, 3.1f, seed);
            // Make the top lumpy and slightly asymmetric.
            float hx = Mathx.cos(ang) * w * 0.5f * rad;
            float hy = Mathx.sin(ang) * h * rad * (0.8f + 0.25f * Mathx.smoothstep(0f, 1f, 1f - Math.abs(t - peak) * 2f));
            float x = ox + hx + tilt * hy;
            float y = oy - hy;
            if (i == 0) path.moveTo(x, oy + 4f * u);
            path.lineTo(x, y);
        }
        path.lineTo(ox + w * 0.5f, oy + 4f * u);
        path.quadTo(ox, oy + 9f * u, ox - w * 0.5f, oy + 4f * u);
        path.close();

        Bitmap mask = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFFFFFFFF);
        new Canvas(mask).drawPath(path, p);
        int[] m = new int[bw * bh];
        mask.getPixels(m, 0, bw, 0, 0, bw, bh);
        mask.recycle();

        // ---- contact shadow on the sand ---------------------------------------------------
        bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        p.setShader(new RadialGradient(ox + w * 0.08f, oy, w * 0.62f,
                new int[] {0x70000000, 0x30000000, 0x00000000}, new float[] {0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        c.save();
        c.scale(1f, 0.22f, ox, oy);
        c.drawCircle(ox + w * 0.08f, oy, w * 0.62f, p);
        c.restore();
        int[] out = new int[bw * bh];
        bitmap.getPixels(out, 0, bw, 0, 0, bw, bh);

        // ---- per-pixel stone -------------------------------------------------------------
        int[] palettes = {0xFF9A9C94, 0xFF8E887C, 0xFFA59E90, 0xFF828A8C};
        int albedo0 = palettes[r.nextInt(palettes.length)];
        int albedo1 = Mathx.shade(albedo0, 0.72f);
        int veinCol = 0xFFD9D6CC;
        float scale = 1f / (14f * u);
        float[] hgt = new float[bw * bh];
        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                int i = y * bw + x;
                if ((m[i] >>> 24) == 0) continue;
                float nx = (x - ox) / (w * 0.5f);
                float ny = (oy - y) / h;
                float dome = Mathx.sqrt(Math.max(0f, 1f - nx * nx * 0.8f - (1f - ny) * (1f - ny) * 0.15f));
                // Ridged noise produces sharp crags.
                float rn = 1f - Math.abs(Mathx.fbm(x * scale, y * scale, seed, 4) * 2f - 1f);
                hgt[i] = dome * 1.4f + rn * 0.55f + Mathx.fbm(x * scale * 3f, y * scale * 3f, seed + 9, 2) * 0.12f;
            }
        }
        // Light comes from above, a little from the left and towards the viewer.
        float lx = -0.35f, ly = -0.8f, lz = 0.5f;
        float ll = Mathx.sqrt(lx * lx + ly * ly + lz * lz);
        lx /= ll;
        ly /= ll;
        lz /= ll;
        float bump = 5f * u;
        for (int y = 1; y < bh - 1; y++) {
            for (int x = 1; x < bw - 1; x++) {
                int i = y * bw + x;
                int ma = m[i] >>> 24;
                if (ma == 0) continue;
                float hx = (hgt[i + 1] - hgt[i - 1]) * bump;
                float hy = (hgt[i + bw] - hgt[i - bw]) * bump;
                float nl = Mathx.sqrt(hx * hx + hy * hy + 1f);
                float diff = Math.max(0f, (-hx * lx - hy * ly + lz) / nl);
                float ao = Mathx.smoothstep(0f, h * 0.35f, oy - y);
                float spot = Mathx.fbm(x * scale * 0.6f, y * scale * 0.6f, seed + 3, 3);
                int col = Mathx.mix(albedo1, albedo0, spot);
                float vein = Math.abs(Mathx.sin((x * 0.7f + y) * scale * 2.2f + Mathx.fbm(x * scale, y * scale, seed + 5, 3) * 9f));
                if (vein < 0.06f) col = Mathx.mix(col, veinCol, (0.06f - vein) / 0.06f * 0.6f);
                // Faint algae on the upper surfaces.
                float moss = Mathx.smoothstep(0.55f, 0.75f, Mathx.fbm(x * scale * 1.5f, y * scale * 1.5f, seed + 11, 3)) * Math.max(0f, -ly * 0.6f);
                col = Mathx.mix(col, 0xFF4E6B2E, moss * 0.6f * Mathx.smoothstep(0.4f, 0.9f, diff));
                float light = 0.42f + 0.9f * diff * (0.55f + 0.45f * ao);
                col = Mathx.shade(col, light);
                col = Mathx.mix(col, AquariumScene.FOG, 0.12f);
                // Composite over the shadow.
                int dst = out[i];
                float a = ma / 255f;
                int blended = (dst >>> 24) == 0 ? col : Mathx.mix(dst, col, a);
                out[i] = Mathx.withAlpha(blended, Math.max(dst >>> 24, ma));
            }
        }
        bitmap.setPixels(out, 0, bw, 0, 0, bw, bh);
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
