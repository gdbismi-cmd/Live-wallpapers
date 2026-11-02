package com.livewallpapers.aquarium.scene;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * The far end of the tank: water colour gradient, hazy distant planting and substrate. Rendered once
 * at a quarter of the screen resolution, blurred and then scaled up, which gives a natural
 * out-of-focus look for free.
 */
final class Background {
    static final float PARALLAX = 0.5f;
    private static final int DOWNSCALE = 4;

    private final AquariumScene s;
    private Bitmap bitmap;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();
    private final float fullWidth;

    Background(AquariumScene scene) {
        s = scene;
        fullWidth = s.width + (s.worldW - s.width) * PARALLAX;
        render();
    }

    private void render() {
        int bw = (int) Math.ceil(fullWidth / DOWNSCALE) + 2;
        int bh = (int) Math.ceil(s.height / (float) DOWNSCALE) + 2;
        bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        c.scale(1f / DOWNSCALE, 1f / DOWNSCALE);

        float W = fullWidth + DOWNSCALE * 2;
        float H = s.height + DOWNSCALE * 2;
        float u = s.u;
        Random r = new Random(s.rnd.nextLong());
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // Water column.
        p.setShader(new LinearGradient(0, 0, 0, H,
                new int[] {0xFF4FB4C8, 0xFF2A8BA6, 0xFF186A85, 0xFF0F4D63, 0xFF0A3747, 0xFF082C39},
                new float[] {0f, 0.07f, 0.3f, 0.55f, 0.78f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);

        // Glow from the light above, centred and slightly off to one side.
        p.setShader(new RadialGradient(W * 0.42f, -H * 0.05f, H * 0.75f,
                new int[] {0x5AB8F0F0, 0x2280D0D8, 0x00000000}, new float[] {0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);

        // Distant substrate slope.
        float horizon = s.sandBaseY - 40f * u;
        Path path = new Path();
        path.moveTo(0, H);
        for (float x = 0; x <= W + 20f * u; x += 20f * u) {
            path.lineTo(x, horizon + (Mathx.fbm(x / (90f * u), 3.3f, 77, 3) - 0.5f) * 40f * u);
        }
        path.lineTo(W + 20f * u, H);
        path.close();
        p.setShader(new LinearGradient(0, horizon - 20f * u, 0, H,
                new int[] {0xFF3F6F6E, 0xFF5E7F72}, null, Shader.TileMode.CLAMP));
        c.drawPath(path, p);
        p.setShader(null);

        // Three layers of hazy plants, the furthest closest to the water colour.
        int[] layerColors = {0xFF266C70, 0xFF215F55, 0xFF1C5246};
        for (int layer = 0; layer < 3; layer++) {
            int col = layerColors[layer];
            float baseY = horizon + (10f + layer * 14f) * u;
            float maxH = s.height * (0.45f - layer * 0.08f);
            // Bushy clumps.
            for (float x = r.nextFloat() * 60f * u; x < W; x += Mathx.range(r, 40f, 110f) * u) {
                float bushH = Mathx.range(r, 0.25f, 1f) * maxH * 0.5f;
                p.setColor(Mathx.shade(col, Mathx.range(r, 0.85f, 1.1f)));
                int blobs = 14 + r.nextInt(12);
                for (int i = 0; i < blobs; i++) {
                    float t = r.nextFloat();
                    float bx = x + Mathx.range(r, -1f, 1f) * (1f - t * 0.6f) * 26f * u;
                    float by = baseY - t * bushH;
                    c.drawCircle(bx, by, Mathx.range(r, 6f, 14f) * u * (1f - t * 0.5f), p);
                }
            }
            // Grass blades.
            for (float x = 0; x < W; x += Mathx.range(r, 3f, 9f) * u) {
                float bladeH = Mathx.range(r, 0.2f, 1f) * maxH;
                float lean = Mathx.range(r, -0.25f, 0.25f) * bladeH;
                float bladeW = Mathx.range(r, 1.5f, 3.5f) * u;
                p.setColor(Mathx.shade(col, Mathx.range(r, 0.8f, 1.15f)));
                path.reset();
                path.moveTo(x - bladeW, baseY);
                path.quadTo(x - bladeW * 0.5f + lean * 0.2f, baseY - bladeH * 0.6f, x + lean, baseY - bladeH);
                path.quadTo(x + bladeW * 0.5f + lean * 0.2f, baseY - bladeH * 0.6f, x + bladeW, baseY);
                path.close();
                c.drawPath(path, p);
            }
            // Haze between layers.
            p.setShader(new LinearGradient(0, s.surfaceY, 0, H,
                    new int[] {0x00000000, Mathx.withAlpha(AquariumScene.FOG, 0.28f), 0x22082C39},
                    new float[] {0f, 0.75f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, p);
            p.setShader(null);
        }

        // Fade the bottom of the distant planting into darkness behind the main substrate.
        p.setShader(new LinearGradient(0, horizon, 0, H,
                new int[] {0x00000000, 0x66061E28}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, horizon, W, H, p);

        blur(bitmap, 2);
    }

    /** Two-pass box blur on the bitmap pixels. */
    private static void blur(Bitmap bmp, int radius) {
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        int[] src = new int[w * h];
        int[] tmp = new int[w * h];
        bmp.getPixels(src, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 2; pass++) {
            boxPass(src, tmp, w, h, radius, true);
            boxPass(tmp, src, w, h, radius, false);
        }
        bmp.setPixels(src, 0, w, 0, 0, w, h);
    }

    private static void boxPass(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w;
        int len = horizontal ? w : h;
        int div = r * 2 + 1;
        for (int l = 0; l < lines; l++) {
            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int c = in[idx(l, Math.max(0, Math.min(len - 1, i)), w, horizontal)];
                sa += c >>> 24;
                sr += (c >> 16) & 0xFF;
                sg += (c >> 8) & 0xFF;
                sb += c & 0xFF;
            }
            for (int i = 0; i < len; i++) {
                out[idx(l, i, w, horizontal)] = ((sa / div) << 24) | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int cOut = in[idx(l, Math.max(0, i - r), w, horizontal)];
                int cIn = in[idx(l, Math.min(len - 1, i + r + 1), w, horizontal)];
                sa += (cIn >>> 24) - (cOut >>> 24);
                sr += ((cIn >> 16) & 0xFF) - ((cOut >> 16) & 0xFF);
                sg += ((cIn >> 8) & 0xFF) - ((cOut >> 8) & 0xFF);
                sb += (cIn & 0xFF) - (cOut & 0xFF);
            }
        }
    }

    private static int idx(int line, int i, int w, boolean horizontal) {
        return horizontal ? line * w + i : i * w + line;
    }

    void draw(Canvas c, float x) {
        dst.set(x, 0, x + bitmap.getWidth() * DOWNSCALE, bitmap.getHeight() * DOWNSCALE);
        c.drawBitmap(bitmap, null, dst, paint);
    }

    void release() {
        if (bitmap != null) bitmap.recycle();
        bitmap = null;
    }
}
