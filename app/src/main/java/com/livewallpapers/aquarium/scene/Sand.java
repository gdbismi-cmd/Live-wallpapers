package com.livewallpapers.aquarium.scene;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.util.Arrays;
import java.util.Random;

/** The gravel/sand substrate, with animated caustics projected onto it. */
final class Sand {
    private static final int TILE = 128;
    private static final int FRAMES = 24;
    private static final float CAUSTIC_PERIOD = 9f;

    private final AquariumScene s;
    private Bitmap bitmap;
    private float top;
    private final Path clip = new Path();
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    private Bitmap[] frames;
    private BitmapShader[] shaders;
    private final Paint causticPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();

    Sand(AquariumScene scene) {
        s = scene;
        renderSubstrate();
        if (s.config.caustics) buildCaustics();
    }

    // --------------------------------------------------------------------------------------

    private void renderSubstrate() {
        int W = (int) Math.ceil(s.worldW);
        float[] edge = new float[W];
        float minTop = Float.MAX_VALUE;
        for (int x = 0; x < W; x++) {
            edge[x] = s.sandTop(x);
            minTop = Math.min(minTop, edge[x]);
        }
        top = (float) Math.floor(minTop - 2);
        int H = (int) Math.ceil(s.height - top);
        bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);

        Random r = new Random(s.rnd.nextLong());
        float u = s.u;
        int[] px = new int[W * H];
        int back = 0xFF6B6650;
        int front = 0xFFD6C49B;
        int seed = r.nextInt(1000);
        float depthSpan = s.height - s.sandBaseY;
        for (int y = 0; y < H; y++) {
            float wy = top + y;
            for (int x = 0; x < W; x++) {
                float dy = wy - edge[x];
                if (dy < 0f) {
                    px[y * W + x] = 0;
                    continue;
                }
                float t = Mathx.clamp((wy - s.sandBaseY + 20f * u) / depthSpan, 0f, 1f);
                int col = Mathx.mix(back, front, Mathx.pow(t, 0.8f));
                // Large soft undulations, stretched horizontally for perspective.
                float n = Mathx.fbm(x / (40f * u), wy / (9f * u + 14f * u * t), seed, 4);
                // Fine grain.
                float g = (r.nextFloat() - 0.5f) * 0.22f;
                float f = 0.78f + n * 0.4f + g;
                col = Mathx.shade(col, f);
                // Fog towards the back of the tank and darkness right at the edge.
                col = Mathx.mix(col, AquariumScene.FOG, (1f - t) * 0.5f);
                float edgeShade = Mathx.smoothstep(0f, 10f * u, dy);
                col = Mathx.shade(col, 0.7f + 0.3f * edgeShade);
                int a = (int) (255 * Mathx.clamp(dy / 1.5f, 0f, 1f));
                px[y * W + x] = Mathx.withAlpha(col, a);
            }
        }
        bitmap.setPixels(px, 0, W, 0, 0, W, H);

        // Scatter pebbles, sorted back to front.
        Canvas c = new Canvas(bitmap);
        c.translate(0, -top);
        int count = (int) (W * (s.height - s.sandBaseY) / (u * u * 70f));
        float[][] pebbles = new float[count][];
        for (int i = 0; i < count; i++) {
            float x = r.nextFloat() * W;
            float t = Mathx.pow(r.nextFloat(), 0.7f);
            float y = Mathx.lerp(s.sandTop(x) + 3f * u, s.height + 6f * u, t);
            pebbles[i] = new float[] {x, y, t};
        }
        Arrays.sort(pebbles, (a, b) -> Float.compare(a[1], b[1]));
        int[] palette = {0xFF8C8577, 0xFFB2A58C, 0xFF6E6559, 0xFFC9BFAE, 0xFF9A7A5C, 0xFF545049, 0xFFE0D8C8};
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (float[] pb : pebbles) {
            float x = pb[0], y = pb[1], t = pb[2];
            float persp = Mathx.lerp(0.35f, 1.25f, t);
            float rx = Mathx.range(r, 1.2f, 4.2f) * u * persp;
            if (r.nextInt(14) == 0) rx *= 2.2f;
            float ry = rx * Mathx.range(r, 0.45f, 0.7f);
            int col = palette[r.nextInt(palette.length)];
            col = Mathx.shade(col, Mathx.range(r, 0.85f, 1.1f));
            col = Mathx.mix(col, AquariumScene.FOG, (1f - t) * 0.5f);
            shadow.setColor(0x40000000);
            c.drawOval(x - rx * 1.05f, y - ry * 0.4f, x + rx * 1.15f, y + ry * 1.2f, shadow);
            p.setShader(new RadialGradient(x - rx * 0.35f, y - ry * 0.45f, rx * 1.3f,
                    new int[] {Mathx.mix(col, 0xFFFFFFFF, 0.35f), col, Mathx.shade(col, 0.55f)},
                    new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            c.drawOval(x - rx, y - ry, x + rx, y + ry, p);
        }
        p.setShader(null);

        // Gentle darkening at the very front where the glass frame shadows the substrate.
        clip.reset();
        clip.moveTo(0, s.height + 1);
        for (int x = 0; x <= W; x += 4) clip.lineTo(x, edge[Math.min(x, W - 1)]);
        clip.lineTo(W, s.height + 1);
        clip.close();
    }

    // --------------------------------------------------------------------------------------

    /**
     * Pre-computes a looping, tileable caustics animation using the classic iterated-trig
     * "water caustic" function with integer frequencies so it loops seamlessly.
     */
    private void buildCaustics() {
        frames = new Bitmap[FRAMES];
        shaders = new BitmapShader[FRAMES];
        int[] px = new int[TILE * TILE];
        float[] k = {-2f, -1f, 1f, -1f, 1f};
        float inten = 0.005f;
        for (int f = 0; f < FRAMES; f++) {
            float tau = Mathx.TAU * f / FRAMES;
            for (int y = 0; y < TILE; y++) {
                for (int x = 0; x < TILE; x++) {
                    float px0 = Mathx.TAU * x / TILE - 250f;
                    float py0 = Mathx.TAU * y / TILE - 250f;
                    float ix = px0, iy = py0;
                    float c = 1f;
                    for (int n = 0; n < 5; n++) {
                        float t = tau * k[n] + n * 0.7f;
                        float nx = px0 + Mathx.cos(t - ix) + Mathx.sin(t + iy);
                        float ny = py0 + Mathx.sin(t - iy) + Mathx.cos(t + ix);
                        ix = nx;
                        iy = ny;
                        float ax = px0 / (Mathx.sin(ix + t) / inten);
                        float ay = py0 / (Mathx.cos(iy + t) / inten);
                        c += 1f / (float) Math.sqrt(ax * ax + ay * ay);
                    }
                    c /= 5f;
                    c = 1.17f - Mathx.pow(c, 1.4f);
                    float v = Mathx.pow(Math.abs(c), 8f);
                    int a = (int) Mathx.clamp(v * 255f * 1.15f, 0f, 255f);
                    px[y * TILE + x] = Mathx.argb(a, 210, 255, 240);
                }
            }
            Bitmap b = Bitmap.createBitmap(TILE, TILE, Bitmap.Config.ARGB_8888);
            b.setPixels(px, 0, TILE, 0, 0, TILE, TILE);
            frames[f] = b;
            shaders[f] = new BitmapShader(b, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        }
        causticPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
    }

    void draw(Canvas c, float time, float x0, float x1) {
        c.drawBitmap(bitmap, 0, top, bmpPaint);
        if (frames == null) return;

        float pos = (time / CAUSTIC_PERIOD) % 1f * FRAMES;
        int fa = (int) pos;
        int fb = (fa + 1) % FRAMES;
        float blend = pos - fa;

        c.save();
        c.clipPath(clip);
        float scaleX = 120f * s.u / TILE;
        float scaleY = scaleX * 0.42f;
        matrix.setScale(scaleX, scaleY);
        matrix.postTranslate(time * 3f * s.u, top);
        drawFrame(c, fa, (1f - blend), x0, x1);
        drawFrame(c, fb, blend, x0, x1);
        c.restore();
    }

    private void drawFrame(Canvas c, int f, float weight, float x0, float x1) {
        int a = (int) (95 * weight);
        if (a <= 1) return;
        shaders[f].setLocalMatrix(matrix);
        causticPaint.setShader(shaders[f]);
        causticPaint.setAlpha(a);
        c.drawRect(x0, top, x1, s.height, causticPaint);
    }

    void release() {
        if (bitmap != null) bitmap.recycle();
        bitmap = null;
        if (frames != null) for (Bitmap b : frames) b.recycle();
        frames = null;
        shaders = null;
    }
}
